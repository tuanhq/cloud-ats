/**
 * 
 */
package listener;

import helpervm.OfferingHelper;
import helpervm.VMHelper;

import java.util.List;

import models.vm.VMModel;

import org.ats.cloudstack.VirtualMachineAPI;
import org.ats.component.usersmgt.Event;
import org.ats.component.usersmgt.EventExecutedException;
import org.ats.component.usersmgt.EventListener;
import org.ats.component.usersmgt.group.Group;
import org.ats.jenkins.JenkinsMaster;
import org.ats.jenkins.JenkinsSlave;

import play.Logger;

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
        
        List<VMModel> list = VMHelper.getVMsByGroupID(group.getId(), new BasicDBObject("jenkins", true));
        if (list.size() == 1) { 
          VMModel jenkins = list.get(0);
          List<VMModel> vms = VMHelper.getVMsByGroupID(group.getId());
          for (VMModel vm : vms) {
            VMHelper.removeVM(vm);
            VirtualMachineAPI.destroyVM(VMHelper.getCloudStackClient(), vm.getId(), true);
            if (!vm.getBoolean("system")) {
              new JenkinsSlave(new JenkinsMaster(jenkins.getPublicIP(), "http", 8080), vm.getPublicIP()).release();
              VMHelper.getKnife().deleteNode(vm.getName());
            }
          }
        }
        
        OfferingHelper.removeDefaultOfferingOfGroup(group.getId());
      }
    } catch (Exception e) {
      e.printStackTrace();
      Logger.error("DeleteGroupListener error", e);
    }
  }
  
}
