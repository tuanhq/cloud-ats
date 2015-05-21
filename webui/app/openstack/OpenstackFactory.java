package openstack;

import java.util.concurrent.ConcurrentHashMap;

import openstack.keystone.JCloudsKeyStone;
import openstack.neutron.JCloudsNeutron;
import openstack.nova.JCloudsNova;



public class OpenstackFactory {
  private static ConcurrentHashMap<String, JCloudsKeyStone> hashKeystone ;
  private static ConcurrentHashMap<String , JCloudsNova> hashNova;
  private static ConcurrentHashMap<String , JCloudsNeutron> hashNeutron;
  static{
    hashKeystone = new ConcurrentHashMap<String, JCloudsKeyStone>();
    hashNova = new ConcurrentHashMap<String , JCloudsNova>();
    hashNeutron = new ConcurrentHashMap<String, JCloudsNeutron>();
  }
  public static JCloudsKeyStone getJCloudsKeyStoneInstance(){
    JCloudsKeyStone jCloudsKeystone = hashKeystone.get(OpenstackConfiguration.ADMIN_KEYSTONE_USERNAME);
    if (jCloudsKeystone == null){
      jCloudsKeystone = new JCloudsKeyStone();
      hashKeystone.put(OpenstackConfiguration.ADMIN_KEYSTONE_USERNAME, jCloudsKeystone);
    }
    return jCloudsKeystone;
  }
  public static JCloudsNeutron getJCloudsNeutronInstance(String tenantName, String userName, String password){
    String key = tenantName + "_" + userName + "_" + password;
    JCloudsNeutron jCloudNeutron = hashNeutron.get(key);
    if (jCloudNeutron == null){
      jCloudNeutron = new JCloudsNeutron(tenantName, userName, password);
      hashNeutron.put(key, jCloudNeutron);
    }
    return jCloudNeutron;
  }
  public static JCloudsNeutron getJCloudsNeutronInstance(OpenstackCredential credential){
    return getJCloudsNeutronInstance(credential.getTenant(), credential.getUsername(), credential.getPassword());
    
  }
  public static JCloudsNova getJcloudNovaAdmin(){
    return getJCloudsNovaInstance(OpenstackConfiguration.ADMIN_TENANT_NAME, OpenstackConfiguration.ADMIN_KEYSTONE_USERNAME, OpenstackConfiguration.ADMIN_KEYSTONE_PASSWORD);
  }
  public static JCloudsNova getJCloudsNovaInstance(String tenantName, String userName, String password){
    String key = tenantName + "_" + userName + "_" + password;
    JCloudsNova jcloudsNova = hashNova.get(key);
    if (jcloudsNova == null){
      jcloudsNova = new JCloudsNova(tenantName, userName, password);
      hashNova.put(key, jcloudsNova);
    }
    return jcloudsNova;
    
  }
  public static JCloudsNova getJCloudsNovaInstance(OpenstackCredential credential){
    return getJCloudsNovaInstance(credential.getTenant(), credential.getUsername(), credential.getPassword());
  }
  
  
}
