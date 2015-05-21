/**
 * 
 */
package helpervm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.vm.VMModel;
import models.vm.VMModel.VMStatus;
import openstack.OpenstackCredential;
import openstack.OpenstackFactory;
import openstack.keystone.JCloudsKeyStone;
import openstack.neutron.JCloudsNeutron;
import openstack.nova.JCloudsNova;

import org.ats.cloudstack.CloudStackClient;
import org.ats.component.usersmgt.DataFactory;
import org.ats.knife.Knife;

//import azure.AzureClient;


import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;

import controllers.Application;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Sep 10, 2014
 */
public class VMHelper extends AbstractHelper {
  
 

  public static long vmCount() {
    DB vmDB = DataFactory.getDatabase(Application.dbName);
    return vmDB.getCollection(vmColumn).count();
  }
  
  public static boolean createVM(VMModel... vms) {
    DB db = getDatabase();
    DBCollection col = db.getCollection(vmColumn);
    col.insert(vms, WriteConcern.ACKNOWLEDGED);
    boolean exist = false;
    for (DBObject index : col.getIndexInfo()) {
      if ("VM Index".equals(index.get("name"))) exist = true;
    }
    if (!exist) {
      col.createIndex(new BasicDBObject("name", "text"));
      System.out.println("Create VM Index");
    }
    return true;
  }
  
  public static VMModel updateVM(VMModel vm) {
    DB db = getDatabase();
    DBCollection col = db.getCollection(vmColumn);
    col.save(vm);
    return vm;
  }
  
  public static boolean removeVM(VMModel vm) {
    DB db = getDatabase();
    DBCollection col = db.getCollection(vmColumn);
    col.remove(vm);
    return true;
  }
 
  
  public static List<VMModel> getVMsByGroupID(String groupId) {
    DB vmDB = getDatabase();
    DBCursor cursor = vmDB.getCollection(vmColumn).find(new BasicDBObject("group_id", groupId));
    List<VMModel> vms = new ArrayList<VMModel>();
    while (cursor.hasNext()) {
      vms.add(new VMModel().from(cursor.next()));
    }
    Collections.sort(vms, new Comparator<VMModel>() {
      @Override
      public int compare(VMModel o1, VMModel o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return vms;
  }
  
  public static List<VMModel> getReadyVMs(String groupId, DBObject filter) {
    filter.put("group_id", groupId);
    filter.put("status", VMStatus.Ready.toString());
    
    DB vmDB = getDatabase();
    DBCursor cursor = vmDB.getCollection(vmColumn).find(filter);
    List<VMModel> vms = new ArrayList<VMModel>();
    while (cursor.hasNext()) {
      vms.add(new VMModel().from(cursor.next()));
    }
    return vms;
  }
  
  public static List<VMModel> getVMsByGroupID(String groupId, DBObject filter) {
    filter.put("group_id", groupId);
    DB vmDB = getDatabase();
    DBCursor cursor = vmDB.getCollection(vmColumn).find(filter);
    List<VMModel> vms = new ArrayList<VMModel>();
    while (cursor.hasNext()) {
      vms.add(new VMModel().from(cursor.next()));
    }
    Collections.sort(vms, new Comparator<VMModel>() {
      @Override
      public int compare(VMModel o1, VMModel o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return vms;
  }
  
  public static VMModel getVMByID(String vmId) {
    DB vmDB = getDatabase();
    DBCursor cursor = vmDB.getCollection(vmColumn).find(new BasicDBObject("_id", vmId));
    return cursor.hasNext() ? new VMModel().from(cursor.next()) : null;
  }
  
  public static VMModel getVMByName(String vmName) {
    DB vmDB = getDatabase();
    DBCursor cursor = vmDB.getCollection(vmColumn).find(new BasicDBObject("name", vmName));
    return cursor.hasNext() ? new VMModel().from(cursor.next()) : null;
  }
  
  public static List<VMModel> getVMs(DBObject filter) {
    DB vmDB = getDatabase();
    DBCursor cursor = vmDB.getCollection(vmColumn).find(filter);
    List<VMModel> list = new ArrayList<VMModel>();
    while (cursor.hasNext()) {
      list.add(new VMModel().from(cursor.next()));
    }
    return list;
  }
  
  public static void setSystemProperties(Map<String, String> properties) {
    DB db = getDatabase();
    DBCollection col = db.getCollection(propertiesColumn);
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      col.insert(BasicDBObjectBuilder.start("_id", entry.getKey()).append("value", entry.getValue()).append("system_property", true).get());
    }
  }
  
  public static void updateSystemProperties(Map<String, String> properties) {
    DB db = getDatabase();
    DBCollection col = db.getCollection(propertiesColumn);
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      col.save(BasicDBObjectBuilder.start("_id", entry.getKey()).append("value", entry.getValue()).append("system_property", true).get());
    }
  }
  
  public static Map<String, String> getSystemProperties() {
    DB db = getDatabase();
    DBCollection col = db.getCollection(propertiesColumn);
    DBCursor cursor = col.find(new BasicDBObject("system_property", true));
    Map<String, String> map = new HashMap<String, String>();
    while (cursor.hasNext()) {
      DBObject current = cursor.next();
      map.put((String)current.get("_id"), (String)current.get("value"));
    }
    
    return map;
  }
  
  public static String getSystemProperty(String name) {
    DB db = getDatabase();
    DBCollection col = db.getCollection(propertiesColumn);
    DBObject obj = col.findOne(new BasicDBObject("_id", name));
    return (String) obj.get("value");
  }
  
  @Deprecated
  public static CloudStackClient getCloudStackClient() {
    Map<String, String> properties = getSystemProperties();
    String cloudstackApiUrl = properties.get("cloudstack-api-url");
    String cloudstackApiKey = properties.get("cloudstack-api-key");
    String cloudstackApiSecret = properties.get("cloudstack-api-secret");
    return new CloudStackClient(cloudstackApiUrl, cloudstackApiKey, cloudstackApiSecret);
  }
  
//  public static AzureClient getAzureClient() throws IOException {
//    Map<String, String> properties = getSystemProperties();
//    
//    String subcriptionIdAzure = properties.get("subscription-id");
//    String keystorePassword = properties.get("keystore-password");
//    String keystorePath = properties.get("keystore-path"); 
//    
//    String rootPath=play.Play.application().path().getAbsolutePath();
//    String keystoreLocation = rootPath + keystorePath;
//    return new AzureClient(subcriptionIdAzure, keystoreLocation, keystorePassword);
//  }
  
  public static JCloudsKeyStone getJCloudsKeyStoneInstance(){
    return OpenstackFactory.getJCloudsKeyStoneInstance();
  }
  
  public static JCloudsNeutron getJCloudsNeutronInstance(String tenantName, String userName, String password){
   return OpenstackFactory.getJCloudsNeutronInstance(tenantName, userName, password);
  }
  public static JCloudsNeutron getJCloudsNeutronInstance(OpenstackCredential credential){
    return OpenstackFactory.getJCloudsNeutronInstance(credential);
   }
  public static JCloudsNova getJCloudsNovaInstance(OpenstackCredential credential){
    return OpenstackFactory.getJCloudsNovaInstance(credential);
  }
  public static JCloudsNova getJCloudsNovaInstance(String tenantName, String userName, String password){
    
    return OpenstackFactory.getJCloudsNovaInstance(tenantName, userName, password);
  }
  
  
  
  public static Knife getKnife(VMModel jenkins) {
    Knife knife = new Knife(jenkins.getPublicIP(), VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"));
    return knife;
  }  
}
