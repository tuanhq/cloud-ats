/**
 * 
 */
package helpertest;

import helpervm.VMHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import models.test.JenkinsJobModel;
import models.test.JenkinsJobModel.JenkinsBuildResult;
import models.test.JenkinsJobStatus;
import models.test.TestProjectModel;
import models.vm.VMModel;
import models.vm.VMModel.VMStatus;
//import azure.AzureClient;
import openstack.OpenstackCredential;
import openstack.nova.JCloudsNova;
import openstack.nova.ServerAction;

import org.ats.jenkins.JenkinsMaster;
import org.ats.jenkins.JenkinsMavenJob;
import org.ats.jenkins.JenkinsSlave;
import org.ats.jmeter.models.JMeterScript;
import org.jclouds.openstack.nova.v2_0.domain.Server;

import play.Logger;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Oct 26, 2014
 */
public class JenkinsJobExecutor {

  /** .*/
  private ScheduledExecutorService service = null;
  
  /** .*/
  private ExecutorService executor = Executors.newCachedThreadPool();
  private JenkinsJobExecutor() {}
  
  private static JenkinsJobExecutor instance = null;
  
  public final static JenkinsJobExecutor getInstance() { 
    return instance == null ? (instance = new JenkinsJobExecutor()) : instance;
  }
  
  public static boolean checkJenkinsMasterReady(JenkinsMaster master, long start, long timeout) {
    boolean ready = false;
    try {
      ready = master.isReady();
    } catch (Exception e) {
      Logger.debug("Jenkins Master " + master.getMasterHost() + " is not ready", e);
      if ((start - System.currentTimeMillis()) > timeout) return false;
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e1) {
        Logger.debug("Interrupted thread sleep", e);
      }
      return checkJenkinsMasterReady(master, start, timeout);
    }
    return ready;
  }
  
  public static Map<String, ConcurrentLinkedQueue<String>> QueueHolder = new HashMap<String, ConcurrentLinkedQueue<String>>();

  public void start() {
    System.out.println("Start Jenkins Job Executor");

    this.service = Executors.newSingleThreadScheduledExecutor();

    this.service.scheduleAtFixedRate(new Runnable() {

      public void run() {
        final DBObject source = JenkinsJobHelper.getCollection().findOne(new BasicDBObject("status", JenkinsJobStatus.Initializing.toString()));
        
        if (source == null) return;
        
        final JenkinsJobModel jobModel = new JenkinsJobModel().from(source);
        
        ConcurrentLinkedQueue<String> queue = QueueHolder.get(jobModel.getId());
        
        if (queue == null) QueueHolder.put(jobModel.getId(), queue = new ConcurrentLinkedQueue<String>());
        
        final VMModel vm = VMHelper.getVMByID(jobModel.getString("vm_id"));
        
        if (vm == null) return;
        
        jobModel.put("status", JenkinsJobStatus.Running.toString());
        JenkinsJobHelper.updateJenkinsJob(jobModel);
        
        
        //Run a test

        executor.execute(new Runnable() {
          
          @Override
          public void run() {
            
            VMModel jenkins = VMHelper.getVMByID(jobModel.getString("jenkins_id"));

            TestProjectModel project = TestProjectHelper.getProjectById(jobModel.getString("project_id"));
            
            String subfix = jenkins.getName() + "/jenkins";
            JenkinsMaster jenkinsMaster = new JenkinsMaster(jenkins.getPublicIP(), "http", subfix, 8080);

            runTest(project, jenkinsMaster, jobModel, vm);
          }
        });
        
      }
    }, 0, 1000, TimeUnit.MILLISECONDS);
  }
  
  private boolean checkVMReady(VMModel vm, ConcurrentLinkedQueue<String> queue) {
    
    while(true) {
      vm = VMHelper.getVMByID(vm.getId());
      switch (vm.getStatus()) {
      case Initializing:
        queue.add("vm-log-" + vm.getId());
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e1) {
          Logger.debug("Interrupted sleeping thread", e1);
        }
        continue;
      case Error:
        queue.add("VM " + vm.getPublicIP() + " has occurred some errors in initialization phase");

        VMHelper.removeVM(vm);
        VMModel jenkins = VMHelper.getVMsByGroupID(vm.getGroup().getId(), new BasicDBObject("jenkins", true)).get(0);
        try {
          VMHelper.getKnife(jenkins).deleteNode(vm.getName());
        } catch (Exception e) {
          e.printStackTrace();
          Logger.debug("Cloud not release chef node", e);
        }
        
        try {
          String subfix = jenkins.getName() + "/jenkins";
          new JenkinsSlave(new JenkinsMaster(jenkins.getPublicIP(), "http", subfix, 8080), vm.getPublicIP()).release();
        } catch (IOException e) {
          e.printStackTrace();
          Logger.debug("Could not release jenkins node ", e);
        }

        try {
          //AzureClient client = VMHelper.getAzureClient();
          //client.deleteVirtualMachineByName(vm.getName());
          OpenstackCredential openstackCredential = new OpenstackCredential(jenkins.getTenant(), jenkins.getTenantUser(), jenkins.getTenantPassword());
          JCloudsNova jcloudNova = VMHelper.getJCloudsNovaInstance(openstackCredential);
          Server server  = jcloudNova.getServerByName(vm.getName());
          jcloudNova.serverAction(server.getId(), ServerAction.DELETE);
         
        } catch (Exception e) {
          e.printStackTrace();
          Logger.debug("Cloud not destroy vm", e);
        }
        
        return false;
      case Ready:
        vm.setStatus(VMStatus.Running);
        VMHelper.updateVM(vm);
        return true;
      default:
        return false;
      }
    }
  }
  
  private void runTest(TestProjectModel project, JenkinsMaster jenkinsMaster, JenkinsJobModel jobModel, VMModel vm) {
    
    ConcurrentLinkedQueue<String> queue = QueueHolder.get(jobModel.getId());
    
   //Checking VM
    if (! checkVMReady(vm, queue)) {
      long time = System.currentTimeMillis();
      
      jobModel.put("status", JenkinsJobStatus.Errors.toString());
      jobModel.addBuildResult(new JenkinsBuildResult(new Random().nextInt(65536) - 65537, JenkinsJobStatus.Errors, time));
      JenkinsJobHelper.updateJenkinsJob(jobModel);
      
      project.put("status", JenkinsJobStatus.Errors.toString());
      project.put("last_build", time);
      TestProjectHelper.updateProject(project);
      
      if (TestProjectModel.PERFORMANCE.equals(project.getType())) {
        JMeterScript snapshot = JMeterScriptHelper.getJMeterScriptById(jobModel.getId());
        snapshot.put("status", JenkinsJobStatus.Completed.toString());
        snapshot.put("last_build", time);
        JMeterScriptHelper.updateJMeterScript(snapshot);
      }
      return;
    }
    
    queue.add("Checking Jenkins Master status...");
    while(!checkJenkinsMasterReady(jenkinsMaster, System.currentTimeMillis(), 10 * 60 * 1000)) {
      queue.add("Waiting for Jenkins Master to be ready...");
    }
    
    JMeterScript snapshot = JMeterScriptHelper.getJMeterScriptById(jobModel.getId());
    
    try {

      String command = null;
      if (TestProjectModel.FUNCTIONAL.equals(project.getType())) {
        command = "clean install";
      } else if (TestProjectModel.PERFORMANCE.equals(project.getType())) {
        command = "clean verify -Pperformance -Dtest.server=" + vm.getPublicIP();
      }
      
      String assigned = null;
      if (TestProjectModel.FUNCTIONAL.equals(project.getType())) {
        assigned = vm.getPublicIP();
      } else if (TestProjectModel.PERFORMANCE.equals(project.getType())) {
        assigned = vm.getPublicIP();
      }
      
      JenkinsMavenJob job = new JenkinsMavenJob(jenkinsMaster, jobModel.getString("_id") , assigned, 
          project.geGitHttpUrl(), snapshot != null ? snapshot.getString("_id") : "master", command, null);

      int buildNumber = job.update() ? job.build() : job.submit();
      
      Logger.debug("The current build number of " + job.getName() + " is: " + buildNumber);
      
      jobModel.addBuildResult(new JenkinsBuildResult(buildNumber, JenkinsJobStatus.Running, System.currentTimeMillis()));
      JenkinsJobHelper.updateJenkinsJob(jobModel);
      
      if (TestProjectModel.PERFORMANCE.equals(project.getType())) {
        snapshot.put("status", JenkinsJobStatus.Running.toString());
        JMeterScriptHelper.updateJMeterScript(snapshot);
      }
      
      project.put("status", JenkinsJobStatus.Running.toString());
      TestProjectHelper.updateProject(project);
      
      int start = 0;
      int last = 0;
      byte[] bytes = null;
      
      try {
        while (job.isBuilding(buildNumber, System.currentTimeMillis(), 5 * 60 * 1000)) {
          bytes = job.getConsoleOutput(buildNumber, start);
          last = bytes.length;
          byte[] next = new byte[last - start];

          System.arraycopy(bytes, start, next, 0, next.length);

          start += (last - start);

          if (next.length > 0) { 
            String output = new String(next);
            queue.add(output.trim());
          }

          Thread.sleep(1000);
        }
      } catch (Exception e) {
        
        long time = System.currentTimeMillis();
        
        jobModel.put("status", JenkinsJobStatus.Errors.toString());
        jobModel.addBuildResult(new JenkinsBuildResult(buildNumber, JenkinsJobStatus.Errors, time));
        JenkinsJobHelper.updateJenkinsJob(jobModel);
        
        project.put("status", JenkinsJobStatus.Errors.toString());
        project.put("last_build", time);
        TestProjectHelper.updateProject(project);
        
        if (TestProjectModel.PERFORMANCE.equals(project.getType())) {
          snapshot.put("status", JenkinsJobStatus.Errors.toString());
          snapshot.put("last_build", time);
          JMeterScriptHelper.updateJMeterScript(snapshot);
        }
        
        Logger.debug("Can not build a job", e);
        return;
      }
      
      //fetch the last log
      bytes = job.getConsoleOutput(buildNumber, start);
      last = bytes.length;
      byte[] next = new byte[last - start];

      System.arraycopy(bytes, start, next, 0, next.length);

      start += (last - start);

      if (next.length > 0) { 
        String output = new String(next);
        queue.add(output.trim());
      }
      //
      
      long time = System.currentTimeMillis();
      
      String buildStatus = job.getStatus(buildNumber);
      Logger.debug("The build status: " + buildStatus);
      
      jobModel = JenkinsJobHelper.getJobById(jobModel.getId());
      
      if ("SUCCESS".equals(buildStatus)) {
        jobModel.put("status", JenkinsJobStatus.Completed.toString());
        jobModel.addBuildResult(new JenkinsBuildResult(buildNumber, JenkinsJobStatus.Completed, time));
        JenkinsJobHelper.updateJenkinsJob(jobModel);
        
        project.put("status", JenkinsJobStatus.Completed.toString());
        project.put("last_build", time);
        TestProjectHelper.updateProject(project);

        if (TestProjectModel.PERFORMANCE.equals(project.getType())) {
          snapshot.put("status", JenkinsJobStatus.Completed.toString());
          snapshot.put("last_build", time);
          JMeterScriptHelper.updateJMeterScript(snapshot);
        }
        
      } else if ("FAILURE".equals(buildStatus) || "UNSTABLE".equals(buildStatus)) {
        
        jobModel.put("status", JenkinsJobStatus.Errors.toString());
        jobModel.addBuildResult(new JenkinsBuildResult(buildNumber, JenkinsJobStatus.Errors, time));
        JenkinsJobHelper.updateJenkinsJob(jobModel);
        
        project.put("status", JenkinsJobStatus.Errors.toString());
        project.put("last_build", time);
        TestProjectHelper.updateProject(project);
        
        if (TestProjectModel.PERFORMANCE.equals(project.getType())) {
          snapshot.put("status", JenkinsJobStatus.Errors.toString());
          snapshot.put("last_build", time);
          JMeterScriptHelper.updateJMeterScript(snapshot);
        }
        //
      } else if ("ABORTED".equals(buildStatus)) {
        jobModel.put("status", JenkinsJobStatus.Aborted.toString());
        jobModel.addBuildResult(new JenkinsBuildResult(buildNumber, JenkinsJobStatus.Aborted, time));
        JenkinsJobHelper.updateJenkinsJob(jobModel);
        
        project.put("status", JenkinsJobStatus.Aborted.toString());
        project.put("last_build", time);
        TestProjectHelper.updateProject(project);
        
        if (TestProjectModel.PERFORMANCE.equals(project.getType())) {
          snapshot.put("status", JenkinsJobStatus.Aborted.toString());
          snapshot.put("last_build", time);
          JMeterScriptHelper.updateJMeterScript(snapshot);
        } 
      }
      
    } catch (Exception e) {
      jobModel.put("status", JenkinsJobStatus.Errors.toString());
      JenkinsJobHelper.updateJenkinsJob(jobModel);
      
      project.put("status", JenkinsJobStatus.Errors.toString());
      TestProjectHelper.updateProject(project);

      if (TestProjectModel.PERFORMANCE.equals(project.getType())) {
        snapshot.put("status", JenkinsJobStatus.Errors.toString());
        JMeterScriptHelper.updateJMeterScript(snapshot);
      }
      
      Logger.debug("An error occurs when dequeue a jenkins maven job", e);
    } finally {
      vm = VMHelper.getVMByID(vm.getId());
      vm.setStatus(VMStatus.Ready);
      VMHelper.updateVM(vm);
      
      //
      queue.add("project.log.exit");
    }
  }
  
  public void stop() {
    this.service.shutdown();
  }
}
