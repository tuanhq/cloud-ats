/**
 * 
 */
package models.vm;

import helpervm.OfferingHelper;

import org.ats.component.usersmgt.UserManagementException;
import org.ats.component.usersmgt.group.Group;
import org.ats.component.usersmgt.group.GroupDAO;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import controllers.Application;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Sep 6, 2014
 */
public class VMModel extends BasicDBObject {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  
  public VMModel(String id, String name, String groupId, String template, String templateId, String publicIp, String username, String password) {
    this.put("_id", id);
    this.put("name", name);
    this.put("group_id", groupId);
    this.put("public_ip", publicIp);
    this.put("username", username);
    this.put("password", password);
    this.put("template", template);
    this.put("template_id", templateId);
  }
  
  public VMModel() {
    this(null, null, null, null, null, null, null, null);
  }
  
  public String getId() {
    return this.getString("_id");
  }
  
  public String getName() {
    return this.getString("name");
  }
  
  public Group getGroup() {
    try {
      return GroupDAO.getInstance(Application.dbName).findOne(this.getString("group_id"));
    } catch (UserManagementException e) {
      throw new RuntimeException(e);
    }
  }
  
  public OfferingModel getOffering() {
    return OfferingHelper.getOffering(this.getString("offering_id"));
  }
  
  public String getTemplate() {
    return this.getString("template");
  }
  
  public String getTemplateId() {
    return this.getString("template_id");
  }
  
  public String getPublicIP() {
    return this.getString("public_ip");
  }
  
  public String getUsername() {
    return this.getString("username");
  }
  
  public String getPassword() {
    return this.getString("password");
  }
  
  public void setStatus(VMStatus status) {
    this.put("status", status.toString());
  }
  
  public VMStatus getStatus() {
    return VMStatus.valueOf(this.getString("status"));
  }
  
  public String getNormalName() {
    return this.getString("normal_name");
  }
  
  //additional
  
  public String getPrivateIp(){
    return this.getString("private_ip");
  }
  public void setPrivateIp(String privateIp){
    this.put("private_ip", privateIp);
  }
  
  public String getTenant(){
    return this.getString("tenant");
  }
  public void setTenant(String tenant){
    this.put("tenant", tenant);
  }
  
  
  public String getTenantUser(){
    return this.getString("tenant_username");
  }
  public void setTenantUser(String tenantUserName){
    this.put("tenant_username", tenantUserName);
  }
  
  public String getTenantPassword(){
    return this.getString("tenant_password");
  }
  public void setTenantPassword(String tenantPassword){
    this.put("tenant_password", tenantPassword);
  }
  public VMModel from(DBObject source) {
    this.put("_id", source.get("_id"));
    this.put("name", source.get("name"));
    this.put("group_id", source.get("group_id"));
    this.put("public_ip", source.get("public_ip"));
    this.put("username", source.get("username"));
    this.put("password", source.get("password"));
    this.put("template", source.get("template"));
    this.put("template_id", source.get("template_id"));
    
    //Additional
    this.put("system", source.get("system"));
    this.put("jenkins", source.get("jenkins"));
    this.put("gui", source.get("gui"));
    this.put("offering_id", source.get("offering_id"));
    this.put("log", source.get("log"));
    this.put("status", source.get("status"));
    this.put("normal_name", source.get("normal_name"));
    
    //add private ip 
    
    this.put("private_ip", source.get("private_ip"));
    this.put("tenant", source.get("tenant"));
    this.put("tenant_username", source.get("tenant_username"));
    this.put("tenant_password", source.get("tenant_password"));
    return this;
  }
  
  public static enum VMStatus {
    Initializing, Ready, Running, Error, Starting, Stopping, Stopped
  }
}
