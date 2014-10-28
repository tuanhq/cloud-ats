/**
 * 
 */
package controllers.vm;

import helpervm.OfferingHelper;
import helpervm.VMHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import models.vm.OfferingModel;
import models.vm.VMModel;
import models.vm.VMModel.VMStatus;

import org.ats.cloudstack.AsyncJobAPI;
import org.ats.cloudstack.CloudStackClient;
import org.ats.cloudstack.VirtualMachineAPI;
import org.ats.cloudstack.model.Job;
import org.ats.cloudstack.model.VirtualMachine;
import org.ats.common.ssh.SSHClient;
import org.ats.component.usersmgt.group.Group;
import org.ats.jenkins.JenkinsMaster;
import org.ats.jenkins.JenkinsSlave;
import org.ats.knife.Knife;

import play.Logger;
import utils.LogBuilder;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.mongodb.BasicDBObject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Sep 19, 2014
 */
public class VMCreator {
  
  public static void startJenkins(VMModel jenkins) throws Exception {
    
    StringBuilder sb = jenkins.getString("log") == null ? new StringBuilder() : new StringBuilder(jenkins.getString("log"));
    
    if (SSHClient.checkEstablished(jenkins.getPublicIP(), 22, 120)) {
      Session session = SSHClient.getSession(jenkins.getPublicIP(), 22, jenkins.getUsername(), jenkins.getPassword());
      ChannelExec channel = (ChannelExec) session.openChannel("exec");
         
      LinkedList<String> queue = new LinkedList<String>();
      
      String command = "/home/ubuntu/java/jdk1.7.0_51/bin/java -jar jenkins.war > log.txt &";
      channel.setCommand(command);
      channel.connect();
      LogBuilder.log(sb, "Execute command: " + command);
      
      int exitCode = SSHClient.printOut(queue, channel);
      
      for (String s : queue) {
        LogBuilder.log(sb, s);
      }
      LogBuilder.log(sb, "exit code: " + exitCode);
      channel.disconnect();
      session.disconnect();
    } else {
      LogBuilder.log(sb, "Cloud not establish connection in 120s");
    }
    
    jenkins.put("log", sb.toString());
    VMHelper.updateVM(jenkins);
  }
  
  public static VMModel createCompanySystemVM(Group company) throws Exception {
    CloudStackClient client = VMHelper.getCloudStackClient();
    String[] response = VirtualMachineAPI.quickDeployVirtualMachine(client, company.getString("name") + "-jenkins", "gitlab-jenkins", "Large Instance", null);
    String vmId = response[0];
    String jobId = response[1];
    Job job = AsyncJobAPI.queryAsyncJobResult(client, jobId);
    while (!job.getStatus().done()) {
      job = AsyncJobAPI.queryAsyncJobResult(client, jobId);
    }
    
    if (job.getStatus() == org.apache.cloudstack.jobs.JobInfo.Status.SUCCEEDED) {
      
      VirtualMachine vm = VirtualMachineAPI.findVMById(client, vmId, null);
      VMModel vmModel = new VMModel(vm.id, vm.name, company.getId(), vm.templateName, vm.templateId, vm.nic[0].ipAddress, "ubuntu", "ubuntu");
      vmModel.put("system", true);
      vmModel.put("jenkins", true);
      vmModel.put("offering_id", vm.serviceOfferingId);
      VMHelper.createVM(vmModel);
      
      //edit `/etc/hosts` file
      StringBuilder sb = new StringBuilder();
      LogBuilder.log(sb, "Checking SSHD on " + vmModel.getPublicIP());
      if (SSHClient.checkEstablished(vmModel.getPublicIP(), 22, 120)) {
        LogBuilder.log(sb, "Connection is established");
        
        Session session = SSHClient.getSession(vmModel.getPublicIP(), 22, vmModel.getUsername(), vmModel.getPassword());
        
        //sudo
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        //TODO: workaround for unclean template
        String command = "sed 's/172.27.4.81/" + vmModel.getPublicIP() + "/' /etc/hosts > /tmp/hosts";
        channel.setCommand(command);
        channel.connect();
        LogBuilder.log(sb, "Execute command: " + command);
        channel.disconnect();
        
        //replace hosts
        channel = (ChannelExec) session.openChannel("exec");
        command = "sudo -S -p '' cp /tmp/hosts /etc/hosts";
        channel.setCommand(command);
        OutputStream out = channel.getOutputStream();
        channel.connect();
        
        out.write((vmModel.getPassword() + "\n").getBytes());
        out.flush();
        
        LinkedList<String> queue = new LinkedList<String>();
        int exitCode = SSHClient.printOut(queue, channel);
        LogBuilder.log(sb, "Execute command: " + command);
        
        for (String s : queue) {
          LogBuilder.log(sb, s);
        }
        LogBuilder.log(sb, "exit code: " + exitCode);
        channel.disconnect();
        
        //start jenkins
        VMCreator.startJenkins(vmModel);
        //disconnect session
        session.disconnect();
      } else {
        LogBuilder.log(sb, "Cloud not establish connection in 120s");
      }
      
      vmModel.put("log", sb.toString());
      VMHelper.updateVM(vmModel);
      
      List<OfferingModel> list = OfferingHelper.getEnableOfferings();
      Collections.sort(list, new Comparator<OfferingModel>() {
        @Override
        public int compare(OfferingModel o1, OfferingModel o2) {
          return o2.getMemory() - o1.getMemory();
        }
      });
      
      OfferingModel defaultOffering = list.get(0);
      OfferingHelper.addDefaultOfferingForGroup(company.getId(), defaultOffering.getId());
      return vmModel;
    } else {
      Logger.error("Could not create system vm for company " + company.getString("name"));
      return createCompanySystemVM(company);
    }
  }
  
  public static VMModel createNormalGuiVM(Group company) throws Exception {
    return createNormalVM(company, "Gui", "jenkins-slave", "jenkins-slave-gui");
  }
  
  public static VMModel createNormalNonGuiVM(Group company) throws Exception {
    return createNormalVM(company, "Non-Gui", "jenkins-slave-non-ui", "jenkins-slave", "jmeter");
  }
  
  public static Map<String, ConcurrentLinkedQueue<String>> QueueHolder = new HashMap<String, ConcurrentLinkedQueue<String>>();

  public static VMModel createNormalVM(Group company, String subfix, String template, final String ...recipes) throws IOException, JSchException {
    CloudStackClient client = VMHelper.getCloudStackClient();
    OfferingModel offering = OfferingHelper.getDefaultOfferingOfGroup(company.getId()).getOffering();
    final VMModel jenkins = VMHelper.getVMsByGroupID(company.getId(), new BasicDBObject("system", true).append("jenkins", true)).get(0);
    
    //create instance
    final String name = getAvailableName(company, subfix, 0);
    QueueHolder.put(name, new ConcurrentLinkedQueue<String>());
    
    String[] response = VirtualMachineAPI.quickDeployVirtualMachine(client, name, template, offering.getName(), null);
    String vmId = response[0];
    String jobId = response[1];
    Job job = AsyncJobAPI.queryAsyncJobResult(client, jobId);
    while (!job.getStatus().done()) {
      job = AsyncJobAPI.queryAsyncJobResult(client, jobId);
    }
    
    if (job.getStatus() == org.apache.cloudstack.jobs.JobInfo.Status.SUCCEEDED) {
      VirtualMachine guiVM = VirtualMachineAPI.findVMById(client, vmId, null);
      final VMModel vmModel = new VMModel(guiVM.id, guiVM.name, company.getId(), guiVM.templateName, guiVM.templateId, guiVM.nic[0].ipAddress, "ubuntu", "ubuntu");
      vmModel.put("gui", "Non-Gui".equals(subfix) ? false : true);
      vmModel.put("system", false);
      vmModel.put("offering_id", guiVM.serviceOfferingId);
      vmModel.setStatus(VMStatus.Initializing);
      VMHelper.createVM(vmModel);

      //Run recipes
      Thread thread = new Thread() {
        @Override
        public void run() {
          ConcurrentLinkedQueue<String> queue = QueueHolder.get(name);
          try {
            queue.add("Checking SSHD on " + vmModel.getPublicIP());
            if (SSHClient.checkEstablished(vmModel.getPublicIP(), 22, 120)) {
              queue.add("Connection is established");
              
              Session session = SSHClient.getSession(vmModel.getPublicIP(), 22, vmModel.getUsername(), vmModel.getPassword());
              
              //sudo
              ChannelExec channel = (ChannelExec) session.openChannel("exec");
              String command = "echo '" + jenkins.getPublicIP() + "' > ~/jenkins.master";
              channel.setCommand(command);
              channel.connect();
              queue.add("Execute command: " + command);
              channel.disconnect();
              
              //TODO:Workaround for unclean vm template
              channel = (ChannelExec) session.openChannel("exec");
              command = "sudo -S -p '' rm /etc/chef/client.rb";
              channel.setCommand(command);
              OutputStream out = channel.getOutputStream();
              channel.connect();
              
              out.write((vmModel.getPassword() + "\n").getBytes());
              out.flush();
              channel.disconnect();
              
              channel = (ChannelExec) session.openChannel("exec");
              command = "sudo -S -p '' rm /etc/chef/client.pem";
              channel.setCommand(command);
              out = channel.getOutputStream();
              channel.connect();
              
              out.write((vmModel.getPassword() + "\n").getBytes());
              out.flush();
              channel.disconnect();
              //End
              
              Knife knife = VMHelper.getKnife();
              knife.bootstrap(vmModel.getPublicIP(), vmModel.getName(), queue, recipes);
              
            } else {
              queue.add("Cloud not establish connection in 120s");
            }
          } catch (Exception e) {
            e.printStackTrace();
            Logger.error(null, e);
            queue.add("setup.vm.error");
          } finally {
            queue.add("log.exit");
          }
        }
      };
      thread.start();
      
      return vmModel;
    }
    
    return null;
  }
  
  public static String getAvailableName(Group company, String subfix, int indent) throws IOException, JSchException {
    CloudStackClient client = VMHelper.getCloudStackClient();
    String name = company.getString("name") + "-" + subfix + "-" + indent;
    
    //remove legacy vm
    List<VMModel> vms = VMHelper.getVMs(new BasicDBObject("group_id", company.getId()).append("system", false));
    VMModel jenkins = VMHelper.getVMsByGroupID(company.getId(), new BasicDBObject("jenkins", true)).get(0);
    
    for (VMModel vm : vms) {
      if (VirtualMachineAPI.findVMById(client, vm.getId(), null) == null) {
        VMHelper.removeVM(vm);
        VMHelper.getKnife().deleteNode(vm.getName());
        new JenkinsSlave(new JenkinsMaster(jenkins.getPublicIP(), "http", 8080), vm.getPublicIP()).release();
      }
    }
    
    List<VirtualMachine> list = VirtualMachineAPI.listVirtualMachines(client, null, name, null, null, null);
    if (list.size() == 0) return name;
    return getAvailableName(company, subfix, indent + 1);
  }
}
