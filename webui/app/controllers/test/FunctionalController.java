/**
 * 
 */
package controllers.test;

import helpertest.JenkinsJobHelper;
import helpertest.TestProjectHelper;
import helpervm.VMHelper;
import interceptor.AuthenticationInterceptor;
import interceptor.Authorization;
import interceptor.WizardInterceptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import models.test.JenkinsJobModel;
import models.test.JenkinsJobStatus;
import models.test.TestProjectModel;
import models.vm.VMModel;

import org.ats.common.StringUtil;
import org.ats.common.ssh.SSHClient;
import org.ats.component.usersmgt.group.Group;
import org.ats.component.usersmgt.group.GroupDAO;
import org.ats.component.usersmgt.user.User;
import org.ats.component.usersmgt.user.UserDAO;
import org.ats.gitlab.GitlabAPI;
import org.gitlab.api.models.GitlabProject;

import play.data.DynamicForm;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import play.mvc.With;
import views.html.test.body;
import views.html.test.index;
import views.html.test.report_func;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import com.mongodb.BasicDBObject;

import controllers.Application;
import controllers.vm.VMCreator;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Oct 27, 2014
 */
@With({WizardInterceptor.class, AuthenticationInterceptor.class})
@Authorization(feature = "Functional", operation = "")
public class FunctionalController extends TestController {

  public static Result index() {
    return ok(index.render(TestProjectModel.FUNCTIONAL, body.render(TestProjectModel.FUNCTIONAL)));
  }
  
  public static Result report(String projectId) {
    JenkinsJobModel job = JenkinsJobHelper.getJobs(new BasicDBObject("project_id", projectId)).iterator().next();
    TestProjectModel project = TestProjectHelper.getProjectById(projectId);
    VMModel jenkins = VMHelper.getVMByID(job.getString("jenkins_id"));
    return ok(report_func.render(project.getName(), "http://" + jenkins.getPublicIP() + ":8080/job/" + job.getId() + "/ws/target/surefire-reports/html/index.html"));
  }
  
  public static Result createProjectByUpload(boolean run) {
    try {
      MultipartFormData body = request().body().asMultipartFormData();
      DynamicForm form = DynamicForm.form().bindFromRequest();

      String testName = form.get("name");

      FilePart uploaded = body.getFile("uploaded");
      if (uploaded != null) {
        
        File file = uploaded.getFile();

        Group company = TestController.getCompany();
        User currentUser = UserDAO.getInstance(Application.dbName).findOne(session("user_id"));
        Group currentGroup = GroupDAO.getInstance(Application.dbName).findOne(session("group_id"));
        
        VMModel jenkins = VMHelper.getVMs(new BasicDBObject("group_id", company.getId()).append("jenkins", true)).iterator().next();

        String gitlabToken = VMHelper.getSystemProperty("gitlab-api-token");

        GitlabAPI gitlabAPI = new GitlabAPI("http://" + jenkins.getPublicIP(), gitlabToken);
        
        String gitName = testName + "-" + UUID.randomUUID();

        GitlabProject gitProject = createGitProject(gitlabAPI, gitName);

        String gitSshUrl = gitProject.getSshUrl().replace("git.sme.org", jenkins.getPublicIP());

        String gitHttpUrl = gitProject.getHttpUrl().replace("git.sme.org", jenkins.getPublicIP());
        
        TestProjectModel project = new TestProjectModel(
            gitProject.getId(),
            testName, 
            currentGroup.getId(), 
            currentUser.getId(), 
            TestProjectModel.FUNCTIONAL, 
            gitSshUrl,
            gitHttpUrl,
            uploaded.getFilename(), StringUtil.readStream(new FileInputStream(file)).getBytes());

        project.put("status", run ? JenkinsJobStatus.Initializing.toString() : JenkinsJobStatus.Ready.toString());
        project.put("jenkins_id", jenkins.getId());
        
        
        TestProjectHelper.createProject(project);
        
        Session session = SSHClient.getSession(jenkins.getPublicIP(), 22, "ubuntu", "ubuntu");
        ChannelExec channel = (ChannelExec) session.openChannel("exec");

        //clone project
        StringBuilder sb = new StringBuilder("cd /tmp && ").append("git clone ").append(gitSshUrl).append(" ").append(project.getId());
        channel.setCommand(sb.toString());
        channel.connect();
        int exitCode = SSHClient.printOut(System.out, channel);
        if (exitCode != 0) throw new RuntimeException("Can not execute command: `" + sb.toString());
        channel.disconnect();
        
        SSHClient.sendFile(jenkins.getPublicIP(), 22, jenkins.getUsername(), jenkins.getPassword(), 
            "/tmp/" + project.getId(), uploaded.getFilename(), file);
        
//      make commit
        sb = new StringBuilder("cd /tmp/").append(project.getId()).append(" && ");
        sb.append("tar xvf ").append(uploaded.getFilename()).append(" && ");
        sb.append("rm ").append(uploaded.getFilename()).append(" && ");
        sb.append("git add -A && git commit -m 'Snapshot 1' && git push origin master");
        
        channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(sb.toString());
        channel.connect();

        exitCode = SSHClient.printOut(System.out, channel);
        if (exitCode != 0) throw new RuntimeException("Can not execute command: `" + sb.toString());
        channel.disconnect();
        
        //disconnect session
        session.disconnect();
        
        if (run) return runProject(project.getId());
      }
      return redirect(routes.FunctionalController.index());
    } catch (Exception e) {
      e.printStackTrace();
     throw new RuntimeException(e);
    }
  }
  
  public static Result runProject(String projectId) throws Exception {
    final TestProjectModel project = TestProjectHelper.getProjectById( projectId);
    final VMModel jenkins = VMHelper.getVMByID(project.getString("jenkins_id"));
    
    final Group company = getCompany();
    
    List<VMModel> list = VMHelper.getReadyVMs(company.getId(), new BasicDBObject("gui", true));
    
    //remove last build
    JenkinsJobHelper.deleteBuildOfSnapshot(project.getId());
    
    if (list.isEmpty()) {
      Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            JenkinsJobModel job = new JenkinsJobModel(
                JenkinsJobHelper.getCurrentBuildIndex(project.getId()) + 1,
                project.getId(), 
                project.getId(), 
                null, 
                jenkins.getId(), 
                TestProjectModel.FUNCTIONAL);
            JenkinsJobHelper.createJenkinsJob(job);
            
            project.put("status", job.getStatus().toString());
            TestProjectHelper.updateProject(project);

            VMModel vm = VMCreator.createNormalGuiVM(company);
            job.put("vm_id", vm.getId());
            JenkinsJobHelper.updateJenkinsJob(job);
            
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      });
      thread.start();
    } else {
      VMModel vm = list.get(0);
      JenkinsJobModel job = new JenkinsJobModel(JenkinsJobHelper.getCurrentBuildIndex(project.getId()) + 1, project.getId(), project.getId(), vm.getId(), jenkins.getId(), TestProjectModel.FUNCTIONAL);
      JenkinsJobHelper.createJenkinsJob(job);

      project.put("status", job.getStatus().toString());
      TestProjectHelper.updateProject(project);
    }
    return redirect(routes.FunctionalController.index());
  }
  
  public static Result deleteProject(String projectId) throws IOException {
    TestProjectModel project = TestProjectHelper.getProjectById(projectId);
    
    TestProjectHelper.removeProject(project);
    JenkinsJobHelper.deleteBuildOfProject(project.getId());
    
    VMModel jenkins = VMHelper.getVMByID(project.getString("jenkins_id"));
    String gitlabToken = VMHelper.getSystemProperty("gitlab-api-token");
    GitlabAPI gitlabAPI = new GitlabAPI("http://" + jenkins.getPublicIP(), gitlabToken);
    gitlabAPI.deleteProject(project.getGitlabProjectId());
    
    return redirect(routes.FunctionalController.index());
  }
  
  private static GitlabProject createGitProject(GitlabAPI api, String projectName) throws Exception {
    GitlabProject project = api.getAPI().createProject(projectName);
    
    String url = project.getSshUrl().replace("git.sme.org", api.getHost());
    
    String hash = UUID.randomUUID().toString();
    
    StringBuilder sb = new StringBuilder("ssh-keyscan -H ").append(api.getHost()).append(" >> ~/.ssh/known_hosts").append(" && ");
    sb.append("git config --global user.name 'Administrator'").append(" && ");
    sb.append("git config --global user.email 'admin@local.host'").append(" && ");
    sb.append("rm -rf /tmp/").append(hash).append(" && ");
    sb.append("mkdir /tmp/").append(hash).append(" && ");
    sb.append("cd /tmp/").append(hash).append(" && ");
    sb.append("git init").append(" && ");
    sb.append("touch README").append(" && ");
    sb.append("git add README").append(" && ");
    sb.append("git commit -m 'first commit'").append(" && ");
    sb.append("git remote add origin ").append(url).append(" && ");
    sb.append("git push -u origin master");
  
    Session session = SSHClient.getSession(api.getHost(), 22, "ubuntu", "ubuntu");
    ChannelExec channel = (ChannelExec) session.openChannel("exec");
       
    channel.setCommand(sb.toString());
    channel.connect();
    
    SSHClient.printOut(System.out, channel);
    channel.disconnect();
    session.disconnect();
    return project;
  }
}
