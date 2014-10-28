/**
 * 
 */
package org.ats.jmeter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.ats.common.StringUtil;
import org.ats.common.ssh.SSHClient;
import org.ats.gitlab.GitlabAPI;
import org.ats.jmeter.models.JMeterArgument;
import org.ats.jmeter.models.JMeterSampler;
import org.ats.jmeter.models.JMeterSampler.Method;
import org.ats.jmeter.models.JMeterScript;
import org.gitlab.api.models.GitlabProject;
import org.rythmengine.Rythm;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Oct 8, 2014
 */
public class JMeterFactory {

  public static enum Template {
    ARGUMENT, ARGUMENTS, JMETER, POM, SAMPLE_GET, SAMPLE_POST, ASSERTION_TEXT, CONTANT_TIME;
  }
  
  private Map<String, String> templates = new HashMap<String, String>();
  
  public JMeterFactory() throws IOException {
    this(null);
  }
  
  public GitlabProject createProject(GitlabAPI api, String companyName, String projectName) throws IOException, JSchException {
    GitlabProject project = api.getAPI().createProject(projectName);
    
    String url = project.getSshUrl().replace("git.sme.org", api.getHost());
    
    StringBuilder sb = new StringBuilder("ssh-keyscan -H ").append(api.getHost()).append(" >> ~/.ssh/known_hosts").append(" && ");
    sb.append("git config --global user.name 'Administrator'").append(" && ");
    sb.append("git config --global user.email 'admin@local.host'").append(" && ");
    sb.append("rm -rf /tmp/").append(projectName).append(" && ");
    sb.append("mkdir /tmp/").append(projectName).append(" && ");
    sb.append("cd /tmp/").append(projectName).append(" && ");
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

    JMeterFactory factory = new JMeterFactory();
    String pom = factory.createPom(companyName, projectName);
    api.createFile(project, "pom.xml", "master", pom, "init pom");
    
    channel.disconnect();
    session.disconnect();
    return project;
  }
  
  public JMeterParser createJMeterParser(String source) throws Exception {
    return new JMeterParser(source, this.templates);
  }
  
  public JMeterFactory(String templateSource) throws IOException {
    this.templates.put(Template.ARGUMENT.toString(), templateSource != null ?
        StringUtil.readStream(new FileInputStream(templateSource + "/argument.xml")) :  
          StringUtil.readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("argument.xml")));
        
    this.templates.put(Template.ARGUMENTS.toString(), templateSource != null ?
        StringUtil.readStream(new FileInputStream(templateSource + "/arguments.xml")) : 
          StringUtil.readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("arguments.xml")));
        
    this.templates.put(Template.JMETER.toString(), templateSource != null ?
        StringUtil.readStream(new FileInputStream(templateSource + "/jmeter.xml")) :
          StringUtil.readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("jmeter.xml")));
        
    this.templates.put(Template.POM.toString(), templateSource != null ?
        StringUtil.readStream(new FileInputStream(templateSource + "/pom.xml")) :
          StringUtil.readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("pom.xml")));
        
    this.templates.put(Template.SAMPLE_GET.toString(), templateSource != null ?
        StringUtil.readStream(new FileInputStream(templateSource + "/sample-get.xml")) :
          StringUtil.readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("sample-get.xml")));
        
    this.templates.put(Template.SAMPLE_POST.toString(), templateSource != null ?
        StringUtil.readStream(new FileInputStream(templateSource + "/sample-post.xml")) :
          StringUtil.readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("sample-post.xml")));
    
    this.templates.put(Template.ASSERTION_TEXT.toString(), templateSource != null ?
        StringUtil.readStream(new FileInputStream(templateSource + "/assertion-text.xml")) :
          StringUtil.readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("assertion-text.xml")));
    
    this.templates.put(Template.CONTANT_TIME.toString(), templateSource != null ?
        StringUtil.readStream(new FileInputStream(templateSource + "/contant-time.xml")) :
          StringUtil.readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("contant-time.xml")));
  }
  
  public Map<String, String> getTemplates() {
    return Collections.unmodifiableMap(this.templates);
  }
  
  public String createPom(String groupId, String artifactId) {
    if (groupId.indexOf(' ') != -1) groupId = groupId.replaceAll(" ", "-");
    if (artifactId.indexOf(' ') != -1) artifactId = artifactId.replaceAll(" ", "-");
    
    Map<String, Object> params = ParamBuilder.start().put("groupId", groupId).put("artifactId", artifactId).build();
    return Rythm.render(this.templates.get(Template.POM.toString()), params);
  }
  
  public JMeterScript createJmeterScript(String testName, int loops, int numberThreads, int ramUp, boolean scheduler, int duration, JMeterSampler... samplers) {
    return new JMeterScript(templates, testName, loops, numberThreads, ramUp, scheduler, duration, samplers);
  }
  
  public String createArguments(JMeterArgument... arguments) {
    StringBuilder sb = new StringBuilder();
    for (JMeterArgument argument : arguments) {
      sb.append(argument.toString()).append('\n');
    }
    return Rythm.render(this.templates.get(Template.ARGUMENTS.toString()), sb.toString());
  }
  
  public JMeterArgument createArgument(String paramName, String paramValue) {
    return new JMeterArgument(this.templates, paramName, paramValue);
  }
  
  public JMeterSampler createHttpGet(String name, String url, String assertionText, long contantTime, JMeterArgument... arguments) throws UnsupportedEncodingException {
    return createHttpRequest(Method.GET, name, url, assertionText, contantTime, arguments);
  }
  
  public JMeterSampler createHttpPost(String name, String url, String assertionText, long contantTime, JMeterArgument... arguments) throws UnsupportedEncodingException {
    return createHttpRequest(Method.POST, name, url, assertionText, contantTime, arguments);
  }
  
  public JMeterSampler createHttpRequest(Method method, String name, String url, String assertionText, long contantTime, JMeterArgument... arguments) throws UnsupportedEncodingException {
    return new JMeterSampler(templates, method, name, url, assertionText, contantTime, arguments);
  }
}
