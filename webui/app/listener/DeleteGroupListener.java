/**
 * 
 */
package listener;

import helpervm.OfferingHelper;
import helpervm.VMHelper;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.Future;

import models.vm.VMModel;
import openstack.OpenstackCredential;
import openstack.nova.JCloudsNova;
import openstack.nova.ServerAction;

import org.ats.cloudstack.VirtualMachineAPI;
import org.ats.common.ssh.SSHClient;
import org.ats.component.usersmgt.Event;
import org.ats.component.usersmgt.EventExecutedException;
import org.ats.component.usersmgt.EventListener;
import org.ats.component.usersmgt.group.Group;
import org.ats.jenkins.JenkinsMaster;
import org.ats.jenkins.JenkinsSlave;
import org.jclouds.openstack.nova.v2_0.domain.Server;

import play.Logger;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.mongodb.BasicDBObject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Sep 19, 2014
 */
public class DeleteGroupListener implements EventListener {

  @Override
  public void execute(Event event) throws EventExecutedException {
    try {
      if ("delete-group".equals(event.getType())) {
        Group group = new Group().from(event.getSource());
        if (group.getInt("level") > 1) return;

        final List<VMModel> vms = VMHelper.getVMsByGroupID(group.getId());
        Session session = SSHClient.getSession("127.0.0.1", 22, VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"));
        for (VMModel vm : vms) {
          VMHelper.removeVM(vm);
          if (!vm.getBoolean("system")) continue;
          
          ChannelExec channel = (ChannelExec) session.openChannel("exec");
          String command = "sudo -S -p '' /etc/nginx/sites-available/manage_location.sh "
              + vm.getPublicIP() + " " + vm.getName() + " 1";
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
        }
        session.disconnect();
        
        Thread thread = new Thread(new Runnable() {
          @Override
          public void run() {
            for (VMModel vm : vms) {
              try {
                
//                Future<OperationStatusResponse> response = VMHelper.getAzureClient().deleteVirtualMachineByName(vm.getId());
//                OperationStatusResponse result = response.get();
                
                VMModel jenkins = VMHelper.getVMsByGroupID(vm.getGroup().getId(), new BasicDBObject("jenkins", true)).get(0);
                OpenstackCredential openstackCredential = new OpenstackCredential(jenkins.getTenant(), jenkins.getTenantUser(), jenkins.getTenantPassword());
                JCloudsNova jcloudNova = VMHelper.getJCloudsNovaInstance(openstackCredential);
                
                Server server = jcloudNova.getServerByName(vm.getName());
                jcloudNova.serverAction(server.getId(), ServerAction.DELETE);
                Logger.debug("Deleted vm " + vm.getId() + " is " + server.getStatus());
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          };
        }); thread.start();

        OfferingHelper.removeDefaultOfferingOfGroup(group.getId());
      }
    } catch (Exception e) {
      Logger.error("DeleteGroupListener error", e);
    }
  }
  
}
