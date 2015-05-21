/**
 * 
 */
package controllers.vm;

import helpervm.OfferingHelper;
import helpervm.VMHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

import models.vm.OfferingModel;
import models.vm.VMModel;
import models.vm.VMModel.VMStatus;
import openstack.OpenstackConfiguration;
import openstack.OpenstackCredential;
import openstack.exception.RegionException;
import openstack.keystone.JCloudsKeyStone;
import openstack.neutron.JCloudsNeutron;
import openstack.neutron.NeutronException;
import openstack.nova.JCloudsNova;
import openstack.nova.ServerAction;

import org.ats.common.ssh.SSHClient;
import org.ats.component.usersmgt.group.Group;
import org.ats.jenkins.JenkinsMaster;
import org.ats.jenkins.JenkinsSlave;
import org.ats.knife.Knife;
import org.jclouds.openstack.keystone.v2_0.domain.Tenant;
import org.jclouds.openstack.keystone.v2_0.domain.User;
import org.jclouds.openstack.neutron.v2.domain.Network;
import org.jclouds.openstack.neutron.v2.domain.Router;
import org.jclouds.openstack.neutron.v2.domain.Subnet;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;

import play.Logger;
//import azure.AzureClient;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import com.mongodb.BasicDBObject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Sep 19, 2014
 */
public class VMCreator {
  /**
   * 
   * @param tenantName : name of tenant (project ) on the system 
   * @param userName : username login  openstack system 
   * @param password : password login openstack system
   * @param adminEmail : email register in openstack system 
   * @param cidr : Cird of first subnet register in openstack system
   * @throws NeutronException 
   * @throws RegionException 
   */
  public static void createInitialCompanySystem(String tenantName, String userName, String password, String adminEmail, String cidr) throws RegionException, NeutronException{
    
    //Create tenant and user
    JCloudsKeyStone jCloudsKeyStone = VMHelper.getJCloudsKeyStoneInstance();
    Tenant testTenant = jCloudsKeyStone.createTenant(tenantName, tenantName, true);
    User user = jCloudsKeyStone.createUser(testTenant.getId(), userName, password, adminEmail, true);
    String roleDefaultId = jCloudsKeyStone.getRoleIdByName(OpenstackConfiguration.DEFAULT_ROLE);
    jCloudsKeyStone.addRoleOnTenant(testTenant.getId(), user.getId(), roleDefaultId);
    
    //creat network 
    JCloudsNeutron jCloudsNeutron = VMHelper.getJCloudsNeutronInstance(tenantName, userName, password);
    Network network = jCloudsNeutron.createNetwork(tenantName + "_net");
    
    Subnet subnet = jCloudsNeutron.createSubnet(tenantName + "_subnet", network.getId(), cidr == null ? "192.168.1.0/24": cidr );
    //create router 
    String externalNetworkID = jCloudsNeutron.getNetworkIdByName(OpenstackConfiguration.EXTERNAL_NETWORK);
    Router router = jCloudsNeutron.createRouter(tenantName + "_router", externalNetworkID); 
    jCloudsNeutron.addRouterToInterface(router.getId(), subnet.getId());
    
  }
  
  
  public static void createCompanySystemVM(Group company, String tenantName, String userName, String password ) throws Exception {
    
    JCloudsNova jCloudNova = VMHelper.getJCloudsNovaInstance(tenantName, userName, password);
    JCloudsNeutron jCloudNeutron = VMHelper.getJCloudsNeutronInstance(tenantName, userName, password);
    
    String normalName = new StringBuilder().append(company.getString("name")).append("-system").toString();
    String name = normalizeVMName(new StringBuilder(normalName).append("-").append(company.getId()).toString());
    
    String networkId = jCloudNeutron.getNetworkIdByName(tenantName + "_net");
    
    Future<ServerCreated> serverCreate = jCloudNova.createSystemVM(name, networkId);
    
    //Future<OperationStatusResponse> response = azureClient.createSystemVM(name);
    Logger.info("Submited request to create system vm");
    while (!serverCreate.isDone()) {
      System.out.print('.');
      Thread.sleep(3000);
    }
    //OperationStatusResponse status = response.get();
    
    ServerCreated serverCreated = serverCreate.get();
    
    
    Server server =  jCloudNova.getServerByName(name);
    ArrayList<FloatingIP> listFloatingIpAvailable = jCloudNova.listFloatingIpAvailable(); 
    String floatingIpAddr = null;
    if( listFloatingIpAvailable == null){
      floatingIpAddr = jCloudNeutron.createFloatingIP().getFloatingIpAddress();      
    }else{
      floatingIpAddr = listFloatingIpAvailable.get(0).getIp();
    }
    jCloudNova.addFloatingIpToServer(floatingIpAddr, serverCreated.getId());
    
    String privateIp = jCloudNova.getFirstIpOfServer(server);
    
    VMModel vmModel = VMHelper.getVMByName(name);
    vmModel.put("public_ip", floatingIpAddr);
    vmModel.put("private_ip", privateIp);
    VMHelper.updateVM(vmModel);
    
//    Logger.info("Create system vm " + name + " has been " + status.getStatus());
    Logger.info("Create system vm " + name + " has been " + server.getStatus());
    
    List<OfferingModel> list = OfferingHelper.getEnableOfferings();
    Collections.sort(list, new Comparator<OfferingModel>() {
      @Override
      public int compare(OfferingModel o1, OfferingModel o2) {
        return o2.getMemory() - o1.getMemory();
      }
    });

    OfferingModel defaultOffering = list.get(0);
    OfferingHelper.addDefaultOfferingForGroup(company.getId(), defaultOffering.getId());
    
    //add to reverse proxy 
    final String vmSystemName = name;
//    final String vmSystemIp = vm.getIPAddress().getHostAddress();
    final String vmSystemIp = floatingIpAddr;
    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {       
        
        try {
          
        //add guacamole to reverse proxy   
          
          Logger.debug("VMName:" + vmSystemName + " ip:" + vmSystemIp );
          Session session = SSHClient.getSession("127.0.0.1", 22, VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"));
          ChannelExec channel = (ChannelExec) session.openChannel("exec");
          
          String command = "sudo -S -p '' /etc/nginx/sites-available/manage_location.sh "
              + vmSystemIp + " " + vmSystemName + " 0";
          Logger.info("Command add to reverse proxy:" + command);    
          channel.setCommand(command);
          OutputStream out = channel.getOutputStream();
          channel.connect();

          out.write((VMHelper.getSystemProperty("default-password") + "\n").getBytes());
          out.flush();
          channel.disconnect();
          
          
          //restart service nginx
          channel = (ChannelExec) session.openChannel("exec");
          command = "sudo -S -p '' service nginx restart";              
          Logger.info("Command restart service nginx:" + command);    
          channel.setCommand(command);
          out = channel.getOutputStream();
          channel.connect();

          out.write((VMHelper.getSystemProperty("default-password") + "\n").getBytes());
          out.flush();
          SSHClient.printOut(System.out, channel);
          channel.disconnect();
          
          
         //add jenkin to reverse proxy
          
          if (SSHClient.checkEstablished(vmSystemIp, 22, 300)) {
            session = SSHClient.getSession(vmSystemIp, 22, VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"));
            channel = (ChannelExec) session.openChannel("exec");
            
            channel = (ChannelExec) session.openChannel("exec");
            command = "sudo -S -p '' /etc/guacamole/change_prefix_jenkin.sh " + vmSystemName;
            Logger.info("Command add to reverse proxy:" + command);    
            channel.setCommand(command);
            out = channel.getOutputStream();
            channel.connect();

            out.write((VMHelper.getSystemProperty("default-password") + "\n").getBytes());
            out.flush();
            SSHClient.printOut(System.out, channel);
            channel.disconnect();
            
            //restart jenkins service
            channel = (ChannelExec) session.openChannel("exec");
            command = "sudo -S -p '' service jenkins restart";                
            Logger.info("Command  restart service jenkins:" + command);    
            channel.setCommand(command);
            out = channel.getOutputStream();
            channel.connect();

            out.write((VMHelper.getSystemProperty("default-password") + "\n").getBytes());
            out.flush();
            SSHClient.printOut(System.out, channel);
            channel.disconnect();
 
          }          
          session.disconnect();         
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
    thread.start();
    
  }
  
  public static void destroyVM(VMModel vm) throws Exception {

    String vmId = vm.getId();
    
    //remote node of chef server
    VMModel jenkins = VMHelper.getVMsByGroupID(vm.getGroup().getId(), new BasicDBObject("jenkins", true)).get(0);
    VMHelper.getKnife(jenkins).deleteNode(vm.getName());
    
    OpenstackCredential openstackCredential = new OpenstackCredential(jenkins.getTenant(), jenkins.getTenantUser(), jenkins.getTenantPassword());

    //remove node of jenkins server
    try {
      String subfix = jenkins.getName() + "/jenkins";
      new JenkinsSlave( new JenkinsMaster(jenkins.getPublicIP(), "http", subfix, 8080), vm.getPublicIP()).release();
    } catch (IOException e) {
      Logger.debug("Could not release jenkins node ", e);
    }

    //remove node from guacamole
    boolean isGui = vm.getBoolean("gui");
    Session session = SSHClient.getSession(jenkins.getPublicIP(), 22, jenkins.getUsername(), jenkins.getPassword());
    ChannelExec channel = (ChannelExec) session.openChannel("exec");
    String command = "";
    if (isGui) {
      try {
        channel = (ChannelExec) session.openChannel("exec");
        command = "sudo -S -p '' /etc/guacamole/manage_con.sh "
            + vm.getPublicIP() + " 5900 '"
            + VMHelper.getSystemProperty("default-password") + "' vnc 1";
        channel.setCommand(command);
        OutputStream out = channel.getOutputStream();
        channel.connect();

        out.write((jenkins.getPassword() + "\n").getBytes());
        out.flush();
        channel.disconnect();
      } catch (Exception e) {
        Logger.error("Exception when run:" + e.getMessage());
      }

      Logger.info("Command run add connection guacamole: " + command);

    } else {

      try {
        channel = (ChannelExec) session.openChannel("exec");
        command = "sudo -S -p '' /etc/guacamole/manage_con.sh "
            + vm.getPublicIP() + " 22 '"
            + VMHelper.getSystemProperty("default-password") + "' ssh 1";
        channel.setCommand(command);
        OutputStream out = channel.getOutputStream();
        channel.connect();

        out.write((jenkins.getPassword() + "\n").getBytes());
        out.flush();
        channel.disconnect();
      } catch (Exception e) {
        Logger.error("Exception when run:" + e.getMessage());
      }

      Logger.info("Command run add connection guacamole: " + command);

    }

    //delete virtual machine from azure
  
   // AzureClient azureClient = VMHelper.getAzureClient();
    //azureClient.deleteVirtualMachineByName(vmId);
    
    JCloudsNova jCloudNova = VMHelper.getJCloudsNovaInstance(openstackCredential);
    String serverId = jCloudNova.getServerIdByName(vm.getName());
    jCloudNova.serverAction(serverId, ServerAction.DELETE);
    
    Logger.info("Destroying vm " + vmId);
  }
  
  public static String createNormalGuiVM(Group company) throws Exception {
    return createNormalVM(company, "Gui", "cats-ui-image");
  }
  
  public static String createNormalNonGuiVM(Group company) throws Exception {
    return createNormalVM(company, "Non-Gui", "cats-non-ui-image");
  }
  
  public static Map<String, ConcurrentLinkedQueue<String>> QueueHolder = new HashMap<String, ConcurrentLinkedQueue<String>>();

  public static String createNormalVM(Group company, final String subfix, String template, final String ...recipes) throws Exception {

    //AzureClient azureClient = VMHelper.getAzureClient();
    
    OfferingModel offering = OfferingHelper.getDefaultOfferingOfGroup(company.getId()).getOffering();
    final VMModel jenkins = VMHelper.getVMsByGroupID(company.getId(), new BasicDBObject("system", true).append("jenkins", true)).get(0);
    
    OpenstackCredential openstackCredential = new OpenstackCredential(jenkins.getTenant(), jenkins.getTenantUser(), jenkins.getPassword());
    
    //create instance
    String normalName = getAvailableName(company, subfix, 0);
    final String name = normalizeVMName(new StringBuilder(normalName).append("-").append(company.getId()).toString());
    QueueHolder.put(name, new ConcurrentLinkedQueue<String>());
    
    //Future<OperationStatusResponse> response = azureClient.createVM(name, template, offering.getId());
    
    JCloudsNova jCloudNova = VMHelper.getJCloudsNovaInstance(openstackCredential);
    Future<ServerCreated> serverFuture = jCloudNova.createServerAsync(name, template, offering.getId(), null);
    Logger.info("Submited request to create test vm");
    while (!serverFuture.isDone()) {
      System.out.print('.');
      Thread.sleep(3000);
    }
    
//    OperationStatusResponse status = response.get();
    ServerCreated serverCreated = serverFuture.get();
    
    //add floatingIP to vm 
    
    JCloudsNeutron jCloudNeutron = VMHelper.getJCloudsNeutronInstance(openstackCredential);
    String floatingIp = null;
    ArrayList<FloatingIP> listFloatingIpAvailable = jCloudNova.listFloatingIpAvailable();
    if(listFloatingIpAvailable==null){
      floatingIp = jCloudNeutron.createFloatingIP().getFloatingIpAddress();
    }else{
      floatingIp = listFloatingIpAvailable.get(0).getIp();
    }
    Server server = jCloudNova.getServerByName(name);
    jCloudNova.addFloatingIpToServer(floatingIp, serverCreated.getId());
    String privateIp = jCloudNova.getFirstIpOfServer(server);
//    Logger.info("Create " + subfix + " vm " + name + " has been " + status.getStatus());    
    
    //get vm by name
//    final RoleInstance vm = azureClient.getVirutalMachineByName(name);
    final Server vm = jCloudNova.getServerByName(name);
    Logger.info("Create " + subfix + " vm " + name + " has been " + vm.getStatus());    
    VMModel vmModel = VMHelper.getVMByName(name);
    if (vmModel == null) {
      vmModel = new VMModel(name, name, company.getId(), template, template, 
          jCloudNova.getFirstIpOfServer(vm), VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"));
      vmModel.put("gui", "Non-Gui".equals(subfix) ? false : true);
      vmModel.put("system", false);
      vmModel.put("offering_id", offering.getId());
      vmModel.setStatus(VMStatus.Initializing);
      vmModel.put("normal_name", normalName);
      vmModel.setPrivateIp(privateIp);
      VMHelper.createVM(vmModel);
    } else {
      vmModel.put("public_ip",jCloudNova.getFirstIpOfServer(vm) );
      vmModel.setPrivateIp(privateIp);
      VMHelper.updateVM(vmModel);
    }
    final String publicIp = floatingIp;
    //Run recipes
    Thread thread = new Thread() {
      @Override
      public void run() {
         
        ConcurrentLinkedQueue<String> queue = QueueHolder.get(name);
        String ip = publicIp;
        try {
          queue.add("Checking SSHD on " + ip);
          if (SSHClient.checkEstablished(ip, 22, 300)) {
            
            queue.add("Connection is established");
            Session session = SSHClient.getSession(ip, 22, VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"));
            
            //sudo
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            //create jenkins slave
            Logger.debug("Ip Jenkins Master: "+jenkins.getPublicIP());
            String jenkins_subfix = jenkins.getName() + "/jenkins";
            JenkinsMaster master = new JenkinsMaster(jenkins.getPublicIP(), "http", jenkins_subfix, 8080);           
            Logger.debug("Ip new vm: " + ip);
            
            Map<String, String> env = new HashMap<String, String>();
            if ("Gui".equals(subfix)) env.put("DISPLAY", ":0");
            JenkinsSlave slave = new JenkinsSlave(master, ip, env);

            if (slave.join()) {
              queue.add("Create slave " + ip + " sucessfully");
            } else {
              queue.add("Can not create slave" + ip);
            }
            
            //run Jmeter
            if("Non-Gui".equals(subfix)) {
              String command = "nohup jmeter-start > jmeter-server.log 2>&1 &";
              channel.setCommand(command);
              channel.connect();            
              queue.add("Execute command: " + command);
              SSHClient.printOut(queue, channel);
              channel.disconnect();
            }
            
            //Point jenkins ip to cloud-ats.cloudapp.net
            channel = (ChannelExec) session.openChannel("exec");
            String command = "sed 's/127.0.1.1/" + jenkins.getPublicIP() + "/' /etc/hosts > /tmp/hosts";
            channel.setCommand(command);
            channel.connect();
            queue.add("Executed command: " + command);
            channel.disconnect();
            
            //replace /etc/hosts
            channel = (ChannelExec) session.openChannel("exec");
            command = "sudo -S -p '' cp /tmp/hosts /etc/hosts";
            channel.setCommand(command);
            OutputStream out = channel.getOutputStream();
            channel.connect();
            out.write((VMHelper.getSystemProperty("default-password") + "\n").getBytes());
            out.flush();
            
            //disconnect session
            session.disconnect();
            
            Logger.debug("Starting bootstrap node");
            Knife knife = VMHelper.getKnife(jenkins);
            knife.bootstrap(ip, name, VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"), queue, recipes);
            
            //register guacamole
            if("Gui".equals(subfix)){
              command = "sudo -S -p '' /etc/guacamole/manage_con.sh " 
                  + ip + " 5900 '"+VMHelper.getSystemProperty("default-password") + "' vnc 0";                
            } else if ("Non-Gui".equals(subfix)) {
              command = "sudo -S -p '' /etc/guacamole/manage_con.sh " 
                  + ip + " 22 '"+VMHelper.getSystemProperty("default-password") + "' ssh 0";             
            }
            
            session = SSHClient.getSession(jenkins.getPublicIP(), 22, jenkins.getUsername(), jenkins.getPassword());              
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            out = channel.getOutputStream();
            channel.connect();

            out.write((VMHelper.getSystemProperty("default-password") + "\n").getBytes());
            out.flush();
            channel.disconnect();
            session.disconnect();
            queue.add("Command run add connection guacamole: "+ command);
            
            //Update vm status to Ready
            VMModel vmModel = VMHelper.getVMByName(name);
            vmModel.setStatus(VMStatus.Ready);
            VMHelper.updateVM(vmModel);
          } else {
            queue.add("Cloud not establish connection in 120s");
          }
        } catch (Exception e) {
          Logger.debug("Has an error when create vm", e);
          queue.add("setup.vm.error");
        } finally {
          queue.add("log.exit");
        }
      }
    };
    
    //
    thread.start();
    return name;
  }
  
  public static String getAvailableName(Group company, String subfix, int index) throws Exception {
   // AzureClient azureClient = VMHelper.getAzureClient();   
    final VMModel jenkins = VMHelper.getVMsByGroupID(company.getId(), new BasicDBObject("system", true).append("jenkins", true)).get(0);
    OpenstackCredential openstackCredential = new OpenstackCredential(jenkins.getTenant(), jenkins.getUsername(), jenkins.getPassword());
    JCloudsNova jCloudsNova  = VMHelper.getJCloudsNovaInstance(openstackCredential);
    String normalName = new StringBuilder(company.getString("name")).append("-").append(subfix).append("-").append(index).toString();
    String name = normalizeVMName(new StringBuilder(normalName).append("-").append(company.getId()).toString());    
   // RoleInstance vm = azureClient.getVirutalMachineByName(name);
    
    if (jCloudsNova.checkServerNameExist(name)) return normalName;
    return getAvailableName(company, subfix, index + 1);
  }
  
  /**
   * Instance name can not be longer than 63 characters. 
   * Only ASCII letters a~z, A~Z, digits 0~9, hyphen are allowed. Must start with a letter and end with a letter or a digit.
   */
  public static String normalizeVMName(String name) {
    StringBuilder sb = new StringBuilder();
    char[] chars = name.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      char ch = chars[i];
      if (ch >= 'a' && ch <= 'z') sb.append(ch);
      else if (ch >= 'A' && ch <= 'Z') sb.append(ch);
      else if (ch >= '0' && ch <= '9') sb.append(ch);
      else if (ch == '-' && i != 0 && i != 62 && i != chars.length -1) sb.append(ch);
      else {
        if (i == 0) sb.append('a');
        else if (i == 62) sb.append('a');
        else if (i == chars.length -1) sb.append('z');
        else sb.append('-');
      }
    }
    name = sb.length() > 63 ? sb.substring(0, 63) : sb.toString();
    return name;
  }
}
