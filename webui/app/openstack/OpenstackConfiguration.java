package openstack;

public class OpenstackConfiguration {
  public static final String KEYSTONE_AUTH_URL = "http://172.27.4.101:5000/v2.0";
  
  public static final String ADMIN_KEYSTONE_USERNAME = "admin";
  
  public static final String ADMIN_KEYSTONE_PASSWORD = "ADMIN_PASS";
  
  public static final String ADMIN_KEYSTONE_ENDPOINT = "http://172.27.4.101:35357/v2.0";
  
  public static final String ADMIN_TENANT_NAME = "admin";

  public static final String NOVA_ENDPOINT = "http://172.27.4.101:8774/v2/";
  public static final String NEUTRON_ENDPOINT = "http://controller:9696/v2.0";
  
  public static final String CEILOMETER_ENDPOINT = "";
  public static final String EXTERNAL_NETWORK = "ext-net";
  
  public static final String KEYSTONE_PROVIDER = "openstack-keystone";
  public static final String NEUTRON_PROVIDER = "openstack-neutron";
  public static final String NOVA_PROVIDER = "openstack-nova";
  

}
