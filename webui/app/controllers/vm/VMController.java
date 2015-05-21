/**
 * 
 */
package controllers.vm;

import helpervm.OfferingHelper;
import helpervm.VMHelper;
import interceptor.AuthenticationInterceptor;
import interceptor.Authorization;
import interceptor.VMWizardIterceptor;
import interceptor.WithSystem;
import interceptor.WizardInterceptor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import models.vm.DefaultOfferingModel;
import models.vm.OfferingModel;
import models.vm.VMModel;
import models.vm.VMModel.VMStatus;
import openstack.OpenstackCredential;
import openstack.OpenstackFactory;
import openstack.exception.RegionException;
import openstack.nova.JCloudsNova;
import openstack.nova.ServerAction;

import org.apache.cloudstack.api.ApiConstants.VMDetails;
import org.ats.cloudstack.CloudStackClient;
import org.ats.cloudstack.ServiceOfferingAPI;
import org.ats.cloudstack.VirtualMachineAPI;
import org.ats.cloudstack.model.ServiceOffering;
import org.ats.cloudstack.model.VirtualMachine;
import org.ats.common.ssh.SSHClient;
import org.ats.component.usersmgt.UserManagementException;
import org.ats.component.usersmgt.feature.Feature;
import org.ats.component.usersmgt.feature.Operation;
import org.ats.component.usersmgt.group.Group;
import org.ats.component.usersmgt.group.GroupDAO;
import org.ats.component.usersmgt.role.Permission;
import org.ats.component.usersmgt.role.Role;
import org.ats.component.usersmgt.role.RoleDAO;
import org.ats.component.usersmgt.user.User;
import org.ats.component.usersmgt.user.UserDAO;
import org.ats.jenkins.JenkinsMaster;
import org.jclouds.openstack.nova.v2_0.domain.Flavor;
import org.jclouds.openstack.nova.v2_0.domain.Server;

import play.Logger;
import play.api.templates.Html;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.F.Callback0;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import play.mvc.With;
import utils.Util;
import views.html.vm.alert;
import views.html.vm.amazontemplate;
import views.html.vm.azuretemplate;
import views.html.vm.cloudstacktemplate;
import views.html.vm.index;
import views.html.vm.offering;
import views.html.vm.offeringbody;
import views.html.vm.propertiesbody;
import views.html.vm.terminal;
import views.html.vm.vmbody;
import views.html.vm.vmproperties;
import views.html.vm.vmstatus;
//import azure.AzureClient;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineRoleSize;
import com.mongodb.BasicDBObject;

import controllers.Application;
import controllers.organization.Organization;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Sep 4, 2014
 */
@With({WizardInterceptor.class, AuthenticationInterceptor.class})
@Authorization(feature = "Virtual Machine", operation = "")
public class VMController extends Controller {

  @With(VMWizardIterceptor.class)
  public static Result index() throws Exception {
    User currentUser = UserDAO.getInstance(Application.dbName).findOne(session("user_id"));

    boolean system = checkCurrentSystem();
    Group group = null;
    if (system) {
      group = GroupDAO.getInstance(Application.dbName).find(new BasicDBObject("system", true)).iterator().next();
    } else {
      BasicDBObject query = new BasicDBObject("level", 1);
      query.append("user_ids", Pattern.compile(currentUser.getId()));
      group = GroupDAO.getInstance(Application.dbName).find(query).iterator().next();
    }
    
    if (hasPermission(group, "Manage System VM")) {
      return redirect(routes.VMController.systemVmView(group.getId()));
    } else {
      return redirect(routes.VMController.normalVMView(group.getId()));
    }
  }
  
  @With(VMWizardIterceptor.class)
  @Authorization(feature = "Virtual Machine", operation = "Manage System VM")
  public static Result systemVmView(String groupId) throws Exception {
    Group group = GroupDAO.getInstance(Application.dbName).findOne(groupId);
    Html html = vmbody.render(checkCurrentSystem(), group, VMHelper.getVMsByGroupID(group.getId(), new BasicDBObject("system", true)), false);
    return ok(index.render(html));
  }
  
  @With(VMWizardIterceptor.class)
  @WithSystem
  public static Result changeGroup(String groupId) throws Exception {
    Group group = GroupDAO.getInstance(Application.dbName).findOne(groupId);
    Html html = vmbody.render(checkCurrentSystem(), group, VMHelper.getVMsByGroupID(group.getId(), new BasicDBObject("system", true)), false);
    return ok(index.render(html));
  }

  @With(VMWizardIterceptor.class)
  public static WebSocket<JsonNode> vmStatus(final String groupId, final String sessionId) {
    return new WebSocket<JsonNode>() {
      @Override
      public void onReady(WebSocket.In<JsonNode> in, WebSocket.Out<JsonNode> out) {
        try {
          VMStatusActor.addChannel(new VMChannel(sessionId, groupId, out));
          in.onClose(new Callback0() {
            @Override
            public void invoke() throws Throwable {
              VMStatusActor.removeChannel(sessionId);
            }
          });
        } catch (Exception e) {
          Logger.debug("Can not create akka for vm status actor", e);
        }
      }
      
    };
  }
  
  @With(VMWizardIterceptor.class)
  public static WebSocket<JsonNode> vmLog(final String groupId, final String sessionId) {
    return new WebSocket<JsonNode>() {
      @Override
      public void onReady(WebSocket.In<JsonNode> in, WebSocket.Out<JsonNode> out) {
        try {
          VMLogActor.addChannel(new VMChannel(sessionId, groupId, out));
          in.onClose(new Callback0() {
            @Override
            public void invoke() throws Throwable {
              VMLogActor.removeChannel(sessionId);
            }
          });
        } catch (Exception e) {
          Logger.debug("Can not create akka for vm log actor", e);
        }
      }
    };
  }

  @With(VMWizardIterceptor.class)
  @Authorization(feature = "Virtual Machine", operation = "Manage System VM")
  public static Result offeringView(String groupId) throws Exception {
    Group group = GroupDAO.getInstance(Application.dbName).findOne(groupId);
    boolean system = checkCurrentSystem();
    List<OfferingModel> list = group.getBoolean("system") ? OfferingHelper.getOfferings() : OfferingHelper.getEnableOfferings();
    Html html = offeringbody.render(system, group, list);
    return ok(index.render(html));
  }

  @WithSystem
  public static Result getOfferings() throws Exception {
    String cloudstackApiUrl = request().getQueryString("cloudstack-api-url");
    String cloudstackApiKey = request().getQueryString("cloudstack-api-key");
    String cloudstackApiSecret = request().getQueryString("cloudstack-api-secret");
    CloudStackClient client = new CloudStackClient(cloudstackApiUrl, cloudstackApiKey, cloudstackApiSecret);

    List<ServiceOffering> list = ServiceOfferingAPI.listServiceOfferings(client, null, null);
    return ok(offering.render(list));
  }

  @WithSystem
  @With(VMWizardIterceptor.class)
  @Authorization(feature = "Virtual Machine", operation = "Manage System VM")
  public static Result propertiesView() throws Exception {
    Group group = GroupDAO.getInstance(Application.dbName).find(new BasicDBObject("system", true)).iterator().next();
    return ok(index.render(propertiesbody.render(checkCurrentSystem(), group, VMHelper.getSystemProperties())));
  }
  
  @WithSystem
  @With(VMWizardIterceptor.class)
  @Authorization(feature = "Virtual Machine", operation = "Manage System VM")
  public static Result saveProperties() throws Exception {
    DynamicForm form = Form.form().bindFromRequest();
    String cloudstackApiUrl = form.get("cloudstack-api-url");
    String cloudstackApiKey = form.get("cloudstack-api-key");
    String cloudstackApiSecret = form.get("cloudstack-api-secret");
    String cloudstackUsername = form.get("cloudstack-username");
    String cloudstackPassword = form.get("cloudstack-password");
    
    Map<String, String> properties = new HashMap<String, String>();
    properties.put("cloudstack-api-url", cloudstackApiUrl);
    properties.put("cloudstack-api-key", cloudstackApiKey);
    properties.put("cloudstack-api-secret", cloudstackApiSecret);
    properties.put("cloudstack-username", cloudstackUsername);
    properties.put("cloudstack-password", cloudstackPassword);

    VMHelper.updateSystemProperties(properties);
    return redirect(routes.VMController.propertiesView());
  }

  @WithSystem
  public static Result doWizard() throws UserManagementException, IOException {
    DynamicForm form = Form.form().bindFromRequest();
    //get queryString for azure api
    
    String subcriptionIdAzure = form.get("subscription-id");
    String keystoreType = form.get("keystore-type");
    String keystorePwd = form.get("keystore-password");    
    String defaultUser = form.get("default-user");
    String defaultPassword = form.get("default-password");
    
    String keystorePath = "";
    File fileinput = null;
    play.mvc.Http.MultipartFormData _body = request().body().asMultipartFormData();        
    play.mvc.Http.MultipartFormData.FilePart keystorefile = _body.getFile("keystore-file");

    if (keystorefile != null) {
      String fileName = keystorefile.getFilename();
      fileinput = keystorefile.getFile();
      String rootPath = play.Play.application().path().getAbsolutePath();  
      String sepPath = System.getProperty("file.separator");
      keystorePath = sepPath + "conf" + sepPath;
      
      //uload
      Util.uploadFile(fileinput, fileName, rootPath + keystorePath);
      keystorePath = keystorePath + fileName;
    }
    
    String gitlabToken = form.get("gitlab-token");
    Map<String, String> properties = new HashMap<String, String>();
    properties.put("subscription-id", subcriptionIdAzure);
    properties.put("keystore-type", keystoreType);
    properties.put("keystore-password", keystorePwd);
    properties.put("keystore-path", keystorePath);    
    properties.put("default-user", defaultUser);
    properties.put("default-password", defaultPassword);
    properties.put("gitlab-api-token", gitlabToken);
    
    //insert properties into database
    VMHelper.setSystemProperties(properties);
    
    //insert offer
    createOffering(form);
    return redirect(controllers.vm.routes.VMController.index());
  }

  private static void createOffering(DynamicForm form) throws IOException {
    JCloudsNova jCloudsNova = OpenstackFactory.getJcloudNovaAdmin();
    List<Flavor> listFlavor = null;
    try {
      listFlavor = jCloudsNova.listFlavor();
    } catch (RegionException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    for(Flavor flavor : listFlavor){
      OfferingModel model = new OfferingModel(flavor.getName(), flavor.getName(), flavor.getVcpus(), -1, flavor.getRam());
      OfferingHelper.createOffering(model);
    }
    
//    for (String offering : list) {
//      OfferingModel model = null;
//      
//      switch (offering) {
//      case VirtualMachineRoleSize.EXTRASMALL:
//        model = new OfferingModel(offering, offering, 1, -1, 768);
//        model.put("disabled", form.get(offering) != null ? false : true);
//        break;
//      case VirtualMachineRoleSize.SMALL:
//        model = new OfferingModel(offering, offering, 1, -1, 1792);
//        model.put("disabled", form.get(offering) != null ? false : true);
//        break;
//      case VirtualMachineRoleSize.MEDIUM:
//        model = new OfferingModel(offering, offering, 2, -1, 3584);
//        model.put("disabled", form.get(offering) != null ? false : true);
//        break;
//      default:
//        break;
//      }
//      
//      OfferingHelper.createOffering(model);
//    }
  }
  
  @With(VMWizardIterceptor.class)
  public static Result viewConsoleURL(String vmId) {
    
    VMModel vm =VMHelper.getVMByID(vmId);
    VMModel jenkins = VMHelper.getVMsByGroupID(session("group_id"), new BasicDBObject("system", true).append("jenkins", true)).get(0);
    
    try {
      response().setContentType("html");
      String protocal = vm.getBoolean("gui") ? "vnc" : "ssh";
      String src = "http://cloud-ats.cloudapp.net/" + jenkins.getName() + "/guacamole/#/client/c/" + protocal + "_node_" + vm.getPublicIP();
      return ok(terminal.render(src));
    } catch (Exception e) {
      Logger.debug("Has error when view vm remote console", e);
      return ok();
    }
  }

  /**
   * Start, Stop or Restore VM
   * @param action
   * @param vmId
   * @return
   * @throws Exception
   */
  @With(VMWizardIterceptor.class)
  public static Result vmAction(String action, final String vmId) throws Exception {

    if (! hasRightPermission(vmId)) return forbidden(views.html.forbidden.render());
    
   // final AzureClient azureClient = VMHelper.getAzureClient();
    
   
    
    final VMModel vm = VMHelper.getVMByID(vmId);
    
    if ("start".equals(action)) {
      Logger.info("Starting vm " + vmId);
      
      Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
          try {
           // Future<OperationStatusResponse> response = azureClient.startVirtualMachineByName(vmId);
           // OperationStatusResponse status = response.get();
            
            VMModel jenkins = VMHelper.getVMsByGroupID(vm.getGroup().getId(), new BasicDBObject("jenkins", true)).get(0);
            OpenstackCredential openstackCredential = new OpenstackCredential(jenkins.getTenant(), jenkins.getTenantUser(), jenkins.getTenantPassword());
            JCloudsNova jcloudNova = VMHelper.getJCloudsNovaInstance(openstackCredential);
            Server server = jcloudNova.getServerByName(vmId);
            
            jcloudNova.serverAction(server.getId(),ServerAction.START);
            
            String ip = vm.getPublicIP();
            if (!vm.getBoolean("gui")) {
              
              Logger.debug("Checking SSHD on " + ip);
              if (SSHClient.checkEstablished(ip, 22, 300)) {
                
                Logger.debug("Connection is established");
                Session session = SSHClient.getSession(ip, 22, VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"));
                ChannelExec channel = (ChannelExec) session.openChannel("exec");
                String command = "nohup jmeter-start > jmeter-server.log 2>&1 &";
                channel.setCommand(command);
                channel.connect();            
                Logger.debug("Execute command: " + command);
                SSHClient.printOut(System.out, channel);
                channel.disconnect();
              }
            }
            
            Logger.info("Started vm" + vmId + " " + server.getStatus());
            
            vm.setStatus(VMStatus.Ready);
            VMHelper.updateVM(vm);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }); thread.start();
      
      Thread.sleep(3000);
      vm.setStatus(VMStatus.Starting);
      VMHelper.updateVM(vm);
    } else if ("stop".equals(action)) {
      Logger.info("Stoping vm " + vmId);
      
      Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
          try {
//            Future<OperationStatusResponse> response = azureClient.stopVirtualMachineByName(vmId);
//            OperationStatusResponse status =  response.get();
            
            VMModel jenkins = VMHelper.getVMsByGroupID(vm.getGroup().getId(), new BasicDBObject("jenkins", true)).get(0);
            OpenstackCredential openstackCredential = new OpenstackCredential(jenkins.getTenant(), jenkins.getTenantUser(), jenkins.getTenantPassword());
            JCloudsNova jcloudNova = VMHelper.getJCloudsNovaInstance(openstackCredential);
            Server server = jcloudNova.getServerByName(vmId);
            jcloudNova.serverAction(server.getId(), ServerAction.STOP);
            vm.setStatus(VMStatus.Stopped);
            VMHelper.updateVM(vm);
            Logger.info("Stopped vm" + vmId + " " + server.getStatus());
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }); thread.start();
      
      Thread.sleep(3000);
      vm.setStatus(VMStatus.Stopping);
      VMHelper.updateVM(vm);
    } else if ("restore".equals(action)) {
      //Unsupported for now
    } else if ("destroy".equals(action)) {
      if (!hasPermission(vm.getGroup(), "Manage Test VM")) return forbidden(views.html.forbidden.render());
      VMHelper.removeVM(vm);
      VMCreator.destroyVM(vm);
    }

    return status(200);
  }

  @WithSystem
  @With(VMWizardIterceptor.class)
  public static Result updateVMProperties() throws Exception {
    DynamicForm form = Form.form().bindFromRequest();

    CloudStackClient client = VMHelper.getCloudStackClient();
    VMModel vmModel = VMHelper.getVMByID(form.get("vmId"));

    String ip = form.get("ip");
    String username = form.get("username");
    String password = form.get("password");

    ObjectNode json = Json.newObject();

    if (vmModel.getPublicIP().equals(ip)) {
      vmModel.put("username", username);
      vmModel.put("password", password);
      VMHelper.updateVM(vmModel);

      json.put("vmStatus", vmstatus.render(vmModel, true).toString());
      json.put("vmProperties", vmproperties.render(vmModel, true, null, true).toString());
      return ok(json);
    }

    List<VirtualMachine> vms = VirtualMachineAPI.listVirtualMachines(client, null, null, null, vmModel.getTemplateId(), VMDetails.nics);
    for (VirtualMachine vm : vms) {
      if (ip.equals(vm.nic[0].ipAddress)) {
        VMModel existedVMModel = VMHelper.getVMByID(vm.id);
        if (existedVMModel != null) {
          json.put("vmStatus", vmstatus.render(vmModel, true).toString());
          json.put("vmProperties", vmproperties.render(vmModel, true, alert.render("The ip " + ip + " is in use."), true).toString());
          return ok(json);
        } else {
          VMHelper.removeVM(vmModel);
          vmModel.put("_id", vm.id);
          vmModel.put("public_ip", ip);
          vmModel.put("username", username);
          vmModel.put("password", password);
          VMHelper.createVM(vmModel);

          json.put("vmStatus", vmstatus.render(vmModel, true).toString());
          json.put("vmProperties", vmproperties.render(vmModel, true, null, true).toString());
          return ok(json);
        }
      }
    }

    json.put("vmStatus", vmstatus.render(vmModel, true).toString());
    json.put("vmProperties", vmproperties.render(vmModel, true, alert.render("The ip " + ip + " is not available."), true).toString());
    return ok(json);
  }

  private static boolean hasRightPermission(String vmId) throws Exception {

    User currentUser = UserDAO.getInstance(Application.dbName).findOne(session("user_id"));
    VMModel vmModel = VMHelper.getVMByID(vmId);
    Group group = vmModel.getGroup();

    if (!checkCurrentSystem()) {
      if (!group.getUsers().contains(currentUser)) return false;

      if (vmModel.getBoolean("system")) {
        return hasPermission(group, "Manage System VM");
      } else {
        return hasPermission(group, "Manage Test VM");
      }
    }

    return true;
  }
  
  

  public static boolean hasPermission(Group group, String operation) throws Exception {
    if (!checkCurrentSystem()) {

      User currentUser = UserDAO.getInstance(Application.dbName).findOne(session("user_id"));

      //check user ACL
      if (group.getInt("level") != 1 || !group.getUsers().contains(currentUser)) {
        return false;
      }

      Collection<Role> roles = RoleDAO.getInstance(Application.dbName).find(new BasicDBObject("group_id", group.getId()).append("user_ids", Pattern.compile(currentUser.getId())));

      for (Role role : roles) {
        for (Permission per : role.getPermissions()) {
          Feature f = per.getFeature();
          if (f.getName().equals("Virtual Machine")) {
            Operation o = per.getOpertion();
            if (o.getName().equals(operation)) {
              return true;
            }
          }
        }
      }
      return false;
    }

    return true;
  }

  @With(VMWizardIterceptor.class)
  @Authorization(feature = "Virtual Machine", operation = "Manage Test VM")
  public static Result normalVMView(String groupId) throws Exception {
    Group group = GroupDAO.getInstance(Application.dbName).findOne(groupId);

    if (! hasPermission(group, "Manage Test VM")) return forbidden(views.html.forbidden.render());

    List<VMModel> list = VMHelper.getVMsByGroupID(groupId, new BasicDBObject("system", false));
    Html html = vmbody.render(checkCurrentSystem(), group, list, true);
    return ok(index.render(html));
  }

  @With(VMWizardIterceptor.class)
  @Authorization(feature = "Virtual Machine", operation = "Manage Test VM")
  public static Result createrNormalVM(String groupId, final boolean gui) throws Exception {
    
    final Group company = GroupDAO.getInstance(Application.dbName).findOne(groupId);
    
    OfferingModel offering = OfferingHelper.getDefaultOfferingOfGroup(company.getId()).getOffering();
    
    String subfix = gui ? "Gui" : "Non-Gui";
    String template = gui ? "cats-ui-image" : "cats-non-ui-image";
    String normalName = VMCreator.getAvailableName(company, subfix, 0);
    String name = VMCreator.normalizeVMName(new StringBuilder(normalName).append("-").append(company.getId()).toString());
    
    final VMModel vmModel = new VMModel(name, name, company.getId(), template, template, 
        null, VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"));
    vmModel.put("gui", "Non-Gui".equals(subfix) ? false : true);
    vmModel.put("system", false);
    vmModel.put("offering_id", offering.getId());
    vmModel.setStatus(VMStatus.Initializing);
    vmModel.put("normal_name", normalName);
    VMHelper.createVM(vmModel);
    
    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          VMModel jenkins = VMHelper.getVMsByGroupID(company.getId(), new BasicDBObject("jenkins", true)).get(0);
          String subfix = jenkins.getName() + "/jenkins";
          JenkinsMaster jenkinsMaster = new JenkinsMaster(jenkins.getPublicIP(), "http", subfix, 8080);
          if (jenkinsMaster.isReady()) {
            if(gui) VMCreator.createNormalGuiVM(company); else VMCreator.createNormalNonGuiVM(company);
          }
        } catch (Exception e) {
          VMHelper.createVM(vmModel);
          throw new RuntimeException(e);
        }
      }
    });
    thread.start();
    
    scala.collection.mutable.StringBuilder sb = new scala.collection.mutable.StringBuilder();
    sb.append(vmstatus.render(vmModel, false));
    sb.append(vmproperties.render(vmModel, checkCurrentSystem(), null, false));
    return ok(sb.toString());
  }
  
  @With(VMWizardIterceptor.class)
  @Authorization(feature = "Virtual Machine", operation = "Manage System VM")
  public static Result saveOffering(String groupId) throws Exception {
    DynamicForm form = Form.form().bindFromRequest();
    if (checkCurrentSystem()) {
      List<OfferingModel> offerings = OfferingHelper.getOfferings();
      List<OfferingModel> disabledOfferings = new ArrayList<OfferingModel>();
      boolean blank = true;
      for (OfferingModel offering : offerings) {
        if (form.get("offering-" + offering.getId()) != null) {
          blank = false;
          offering.put("disabled", false);
          //OfferingHelper.updateOffering(offering);
        } else {
          offering.put("disabled",true);
          disabledOfferings.add(offering);
        }
      }
      if (!blank) {
        for (OfferingModel offering : offerings) {
          OfferingHelper.updateOffering(offering);
        }
        //check if has only one offering
        List<OfferingModel> enabledOfferings = OfferingHelper.getEnableOfferings();
        if (enabledOfferings.size() == 1) {
          List<DefaultOfferingModel> groups = OfferingHelper.getInvalidDefaultOffering();
          for (DefaultOfferingModel groupOffering : groups) {
            groupOffering.put("offering_id", enabledOfferings.get(0).getId());
            OfferingHelper.updateDefaultOfferingOfGroup(groupOffering);
          }
        }
      }
    } else {
      String offeringId = form.get("offering");
      OfferingHelper.removeDefaultOfferingOfGroup(groupId);
      OfferingHelper.addDefaultOfferingForGroup(groupId, offeringId);
    }
    return ok();
  }

  public static boolean checkCurrentSystem() throws UserManagementException {
    return Organization.isSystem(UserDAO.getInstance(Application.dbName).findOne(session("user_id")));
  }
  
  //get template render form input apikey
  
  @WithSystem
  public static Result apivmView() throws Exception {
    String service = request().getQueryString("service");  
    Html html =null;
    if ("apiCloudStack".equalsIgnoreCase(service)) {
      html=cloudstacktemplate.render();
    } else if ("apiAzure".equalsIgnoreCase(service)) {
      html=azuretemplate.render();
    } else if ("apiAmazon".equalsIgnoreCase(service)) {
      html=amazontemplate.render();
    } else {
      html=Html.apply( "<h1>Default template</h1>");
    } 
    return ok(html);
  }
}
