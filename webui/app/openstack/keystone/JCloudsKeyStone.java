package openstack.keystone;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;

import openstack.OpenstackConfiguration;

import org.jclouds.ContextBuilder;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.keystone.v2_0.KeystoneApi;
import org.jclouds.openstack.keystone.v2_0.domain.Role;
import org.jclouds.openstack.keystone.v2_0.domain.Tenant;
import org.jclouds.openstack.keystone.v2_0.domain.User;
import org.jclouds.openstack.keystone.v2_0.extensions.RoleAdminApi;
import org.jclouds.openstack.keystone.v2_0.extensions.TenantAdminApi;
import org.jclouds.openstack.keystone.v2_0.extensions.UserAdminApi;
import org.jclouds.openstack.keystone.v2_0.features.TenantApi;
import org.jclouds.openstack.keystone.v2_0.features.UserApi;
import org.jclouds.openstack.keystone.v2_0.options.CreateTenantOptions;
import org.jclouds.openstack.keystone.v2_0.options.CreateUserOptions;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.google.inject.Module;

public class JCloudsKeyStone implements Closeable {
  private final KeystoneApi keystoneApi;

  private String provider = OpenstackConfiguration.KEYSTONE_PROVIDER;
  private String identity = OpenstackConfiguration.ADMIN_TENANT_NAME + ":" + OpenstackConfiguration.ADMIN_KEYSTONE_USERNAME; // tenantName:userName
  private String password = OpenstackConfiguration.ADMIN_KEYSTONE_PASSWORD;
  private String endpoint = OpenstackConfiguration.ADMIN_KEYSTONE_ENDPOINT;

  public static void main(String[] args) {
    JCloudsKeyStone jCloudsKeyStone = new JCloudsKeyStone();

    try {
      
      //create tenant and user
      
     Tenant testTenant = jCloudsKeyStone.createTenant("testTenant1", "Test tenant 1", true);
     User user = jCloudsKeyStone.createUser(testTenant.getId(), "usertest1", "123456", "admin@xyzx.com", true);
     String roleDefaultId = jCloudsKeyStone.getRoleIdByName("_member_");
     jCloudsKeyStone.addRoleOnTenant(testTenant.getId(), user.getId(), roleDefaultId);
     
     //create network 
     
     
      
      
      for (Role role : jCloudsKeyStone.listRole()) {
        System.out.println(role);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        jCloudsKeyStone.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

    }
  }

  public JCloudsKeyStone() {
    Iterable<Module> modules = ImmutableSet
        .<Module> of(new SLF4JLoggingModule());


    keystoneApi = ContextBuilder.newBuilder(provider)
        .credentials(identity, password)
        .endpoint(endpoint).modules(modules)
        .buildApi(KeystoneApi.class);

  }

  // USER
  public ArrayList<User> listUser() {
    Optional<? extends UserApi> userApiExtension = keystoneApi.getUserApi();
    if (userApiExtension.isPresent()) {
      UserApi userApi = userApiExtension.get();
      return Lists.newArrayList(userApi.list().concat());

    } else {
      return null;
    }
  }

  public User createUser(String tenantId, String username, String password,
      String email, boolean enable) {

    Optional<? extends UserAdminApi> userAdminApiExtension = keystoneApi
        .getUserAdminApi();

    if (userAdminApiExtension.isPresent()) {
      UserAdminApi userAdminApi = userAdminApiExtension.get();
      CreateUserOptions userOptions = CreateUserOptions.Builder
          .tenant(tenantId).email(email).enabled(enable);
      return userAdminApi.create(username, password, userOptions);

    } else {
      return null;
    }
  }

  public String getUserIdByName(String userName) {
    ArrayList<User> listUser = listUser();
    if (listUser != null) {
      for (User user : listUser) {
        if (userName.equalsIgnoreCase(user.getName())) {
          return user.getId();
        }
      }
    }
    return null;
  }

  // TENANT

  public ArrayList<Tenant> listTenant() {
    Optional<? extends TenantApi> tenantApiExtension = keystoneApi
        .getTenantApi();

    if (tenantApiExtension.isPresent()) {
      TenantApi tenantApi = tenantApiExtension.get();
      return Lists.newArrayList(tenantApi.list().concat());
    } else {
      return null;
    }
  }

  public String getTenantIdByName(String tenantName) {
    ArrayList<Tenant> listTenant = listTenant();
    if (listTenant != null) {
      for (Tenant tenant : listTenant) {
        if (tenantName.equalsIgnoreCase(tenant.getName())) {
          return tenant.getId();
        }
      }
    }
    return null;
  }

  public Tenant createTenant(String tenantName, String description,
      boolean enable) {

    Optional<? extends TenantAdminApi> tenantAdminApiExtension = keystoneApi
        .getTenantAdminApi();
    if (tenantAdminApiExtension.isPresent()) {

      TenantAdminApi tenantAdminApi = tenantAdminApiExtension.get();
      CreateTenantOptions tenantOptions = CreateTenantOptions.Builder
          .description(description).enabled(enable);
      Tenant tenant = tenantAdminApi.create(tenantName, tenantOptions);
      return tenant;
    } else {
      return null;
    }
  }

  // ROLE

  public ArrayList<Role> listRole() {
    Optional<? extends RoleAdminApi> roleApiExtension = keystoneApi
        .getRoleAdminApi();

    if (roleApiExtension.isPresent()) {
      RoleAdminApi roleAdminApi = roleApiExtension.get();
      return Lists.newArrayList(roleAdminApi.list());
    } else {
      return null;
    }
  }

  public boolean addRoleOnTenant(String tenantId, String userId, String roleId) {
    Optional<? extends TenantAdminApi> tenantAdminApiExtension = keystoneApi
        .getTenantAdminApi();

    if (tenantAdminApiExtension.isPresent()) {    
      TenantAdminApi tenantAdminApi = tenantAdminApiExtension.get();
      return tenantAdminApi.addRoleOnTenant(tenantId, userId, roleId);

    } else {
      return false;
    }
  }

  public Role createRole(String name) {
    Optional<? extends RoleAdminApi> roleAdminApiExtension = keystoneApi
        .getRoleAdminApi();
    if (roleAdminApiExtension.isPresent()) {
      RoleAdminApi roleAdminApi = roleAdminApiExtension.get();
      return roleAdminApi.create(name);
    } else {
      return null;
    }
  }
 
  public String getRoleIdByName(String roleName){
    ArrayList<Role> list = listRole();
    if(list !=null ){
      for(Role role:list){
        if(roleName.equals(role.getName())) {
          return role.getId();
        }
      }
    }
    return null;
  }
  
  @Override
  public void close() throws IOException {
    Closeables.close(keystoneApi, true);
  }

}
