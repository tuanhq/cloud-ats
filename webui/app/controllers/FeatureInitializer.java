/**
 * 
 */
package controllers;

import models.test.TestProjectModel.TestProjectType;

import org.ats.component.usersmgt.UserManagementException;
import org.ats.component.usersmgt.feature.Feature;
import org.ats.component.usersmgt.feature.FeatureDAO;
import org.ats.component.usersmgt.feature.Operation;
import org.ats.component.usersmgt.feature.OperationDAO;
import org.ats.component.usersmgt.group.Group;
import org.ats.component.usersmgt.group.GroupDAO;
import org.ats.component.usersmgt.role.Permission;
import org.ats.component.usersmgt.role.Role;
import org.ats.component.usersmgt.role.RoleDAO;
import org.ats.component.usersmgt.user.User;
import org.ats.component.usersmgt.user.UserDAO;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Oct 15, 2014
 */
public class FeatureInitializer {
  
  public static void createOrganizationFeature(String adminEmail, String adminPassword) throws UserManagementException {
    Feature organization = new Feature(Application.dbName, "Organization");
    organization.put("desc", "This is organization management feature");
    organization.put("system", true);
    
    Operation ad = new Operation(Application.dbName, "Administration");
    organization.addOperation(ad);
    
    Group system = new Group(Application.dbName, "System Admin");
    system.put("desc", "This is group of system");
    system.put("system", true);
    system.put("level", 0);
    system.addFeature(organization);
    
    User root = new User(Application.dbName, adminEmail, adminEmail);
    root.put("system", true);
    root.put("password", adminPassword);
    root.joinGroup(system);
    root.put("joined", true);
    
    system.addUser(root);
    
    Role administration = new Role(Application.dbName, "Administration", system.getId());
    administration.put("desc", "This is administration role for organization management");
    administration.put("system", true);
    administration.addPermission(new Permission(Application.dbName, organization.getId(), ad.getId()));
    administration.addUser(root);
    root.addRole(administration);
    system.addRole(administration);

    //persist
    FeatureDAO.getInstance(Application.dbName).create(organization);
    OperationDAO.getInstance(Application.dbName).create(ad);
    UserDAO.getInstance(Application.dbName).create(root);
    GroupDAO.getInstance(Application.dbName).create(system);
    RoleDAO.getInstance(Application.dbName).create(administration);
  }

  public static void createVMFeature(User rootUser, Group systemGroup) throws UserManagementException {
    Feature feature = new Feature(Application.dbName, "Virtual Machine");
    
    Operation o1 = new Operation(Application.dbName, "Manage System VM");
    Operation o2 = new Operation(Application.dbName, "Manage Test VM");
    
    feature.addOperation(o1);
    feature.addOperation(o2);
    
    Role vmRole = new Role(Application.dbName, "VM Management", systemGroup.getId());
    vmRole.addPermission(new Permission(Application.dbName, feature.getId(), o1.getId()));
    vmRole.addPermission(new Permission(Application.dbName, feature.getId(), o2.getId()));
    vmRole.addUser(rootUser);
    
    rootUser.addRole(vmRole);
    
    systemGroup.addFeature(feature);
    systemGroup.addRole(vmRole);
    
    FeatureDAO.getInstance(Application.dbName).create(feature);
    OperationDAO.getInstance(Application.dbName).create(o1, o2);
    RoleDAO.getInstance(Application.dbName).create(vmRole);
    
    UserDAO.getInstance(Application.dbName).update(rootUser);
    GroupDAO.getInstance(Application.dbName).update(systemGroup);
  }
  
  public static void createTestFeature(Group systemGroup, TestProjectType type) throws UserManagementException {
    Feature feature = null;
    
    switch (type) {
    case performance:
      feature = new Feature(Application.dbName, "Performance");
      break;
    case functional:
      feature = new Feature(Application.dbName, "Functional");
      break;
    }
    
    Operation o1 = new Operation(Application.dbName, "Administration");
    Operation o2 = new Operation(Application.dbName, "Test");
    feature.addOperation(o1);
    feature.addOperation(o2);
    
    FeatureDAO.getInstance(Application.dbName).create(feature);
    OperationDAO.getInstance(Application.dbName).create(o1, o2);
    
    systemGroup.addFeature(feature);
    GroupDAO.getInstance(Application.dbName).update(systemGroup);
  }
}
