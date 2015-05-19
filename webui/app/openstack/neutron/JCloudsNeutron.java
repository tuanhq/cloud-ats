package openstack.neutron;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import openstack.OpenstackConfiguration;
import openstack.exception.RegionException;

import org.jclouds.ContextBuilder;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.domain.ExternalGatewayInfo;
import org.jclouds.openstack.neutron.v2.domain.FloatingIP;
import org.jclouds.openstack.neutron.v2.domain.FloatingIP.CreateFloatingIP;
import org.jclouds.openstack.neutron.v2.domain.Network;
import org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork;
import org.jclouds.openstack.neutron.v2.domain.Router;
import org.jclouds.openstack.neutron.v2.domain.Router.CreateRouter;
import org.jclouds.openstack.neutron.v2.domain.RouterInterface;
import org.jclouds.openstack.neutron.v2.domain.Subnet;
import org.jclouds.openstack.neutron.v2.domain.Subnet.CreateSubnet;
import org.jclouds.openstack.neutron.v2.extensions.FloatingIPApi;
import org.jclouds.openstack.neutron.v2.extensions.RouterApi;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;
import org.jclouds.openstack.neutron.v2.features.SubnetApi;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.google.inject.Module;
 

public class JCloudsNeutron implements Closeable {
  
   private final NeutronApi neutronApi;
   private final Set<String> regions; 
   private final String endpoint = OpenstackConfiguration.KEYSTONE_AUTH_URL;
   private final String provider = OpenstackConfiguration.NEUTRON_PROVIDER;
 
   public static void main(String[] args) throws IOException {
      System.out.format("Create, List, and Delete Networks");
 
      JCloudsNeutron jcloudsNeutron = new JCloudsNeutron("testTenant1", "usertest1", "123456");
     // JCloudsNeutron jcloudsNeutron = new JCloudsNeutron("demo", "demo", "DEMO_PASS");
 
      try {
         //Network network = jcloudsNeutron.createNetwork("abc");
         //jcloudsNeutron.listNetworks();
        // jcloudsNeutron.deleteNetwork(network);
        Network network = jcloudsNeutron.createNetwork("network5");
//          System.out.println("NETWORKID" + network.getId());
       //  String  networkId = jcloudsNeutron.getNetworkIdByName("network1");
//         
           
          Subnet subnet = jcloudsNeutron.createSubnet("subnetnew", network.getId(), "192.168.4.0/24");
//         String externalNetworkID = jcloudsNeutron.getNetworkIdByName("ext-net");
//         Router router = jcloudsNeutron.createRouter("testRouter", externalNetworkID);
        String routerId = jcloudsNeutron.getRouterIdByName("testRouter");
        
         jcloudsNeutron.addRouterToInterface(routerId, subnet.getId());
         
         
         jcloudsNeutron.close();
      }
      catch (Exception e) {
         e.printStackTrace();
      }
      finally {
         jcloudsNeutron.close();
      }
   }
 
   public JCloudsNeutron(String tenantName, String userName, String password) {
      Iterable<Module> modules = ImmutableSet.<Module> of(new SLF4JLoggingModule());
       
      String identity = tenantName + ":" + userName; // tenantName:userName      
 
      neutronApi = ContextBuilder.newBuilder(provider)
            .credentials(identity, password)
            .endpoint(endpoint)
            .modules(modules)
            .buildApi(NeutronApi.class);
      regions = neutronApi.getConfiguredRegions();
   } 

   public String getRegionOne() throws RegionException{
     for(String region:regions){
       return region;
     }
     throw new RegionException("Region not found!");
   }
   
   //NETWORK
   public Network createNetwork(String networkName) throws RegionException, NeutronException{
     
      if(!checkNetworkNameExist(networkName)){
       Network network = null ;       
       NetworkApi networkApi = neutronApi.getNetworkApi(getRegionOne());          
       network = networkApi.create(CreateNetwork.createBuilder(networkName).build());
       return network;       
       
      }else{
        
        throw new NeutronException("Network name exists on this tenant!");
      }
   }
 
   public ArrayList<Network> listNetworks() throws RegionException {
      
     NetworkApi networkApi = neutronApi.getNetworkApi(getRegionOne());   
     return Lists.newArrayList(networkApi.list().concat());     
   }
 
   public boolean deleteNetwork(String networkId) throws RegionException {
      NetworkApi networkApi = neutronApi.getNetworkApi(getRegionOne());
      return networkApi.delete(networkId); 
      
   }
   
  public String getNetworkIdByName(String networkName) throws NeutronException,  RegionException {
    ArrayList<Network> list = listNetworks();
    if (list != null) {
      for (Network network : list) {
        if (networkName.equals(network.getName())) {
          return network.getId();
        }
      }
      
      throw new NotFoundByNameException();
    } else {
      
      throw new NotFoundAny();
    }
  }
  
  public boolean checkNetworkNameExist(String networkName) throws RegionException{
    try {
      getNetworkIdByName(networkName);
    } catch (NeutronException e) {
      return false;
    } 
    return true;
  }
   
  //Subnet
  
  public Subnet createSubnet(String subnetName, String networkId, String cidr) throws RegionException, NeutronException{
    
    if(checkSubnetNameExist(subnetName, networkId)){
      throw new NeutronException("Subnet name exist on this network of tenant");
      
    }
    if (checkNetworkAddrExist(cidr)){
      throw new NeutronException("Network Addres exist on this network of tenant");
    }
      
    CreateSubnet subnet = CreateSubnet.createBuilder(networkId, cidr).ipVersion(4).name(subnetName).build();
    SubnetApi subnetApi = neutronApi.getSubnetApi(getRegionOne());
    return subnetApi.create(subnet);
    
  }
  
  public ArrayList<Subnet> listSubnet() throws RegionException{
    SubnetApi subnetApi = neutronApi.getSubnetApi(getRegionOne());
    return Lists.newArrayList(subnetApi.list().concat());
  }
  
  public String getSubnetIdByName(String subnetName, String networkId) throws NeutronException, RegionException{
    ArrayList<Subnet> list = listSubnet();
    
    if( list != null ){
      for(Subnet subnet : list){
        if (subnetName.equals(subnet.getName()) && networkId.equals(subnet.getNetworkId())){
          return subnet.getId();
        }
      }
      
      throw new NotFoundByNameException();
    } else {
      
      throw new NotFoundAny();
    }
  }
  
  public boolean checkSubnetNameExist(String subnetName, String networkId) throws RegionException{
    try {
      getSubnetIdByName(subnetName, networkId);
    } catch (NeutronException e) {
      return false;
    } 
    return true;
  }
  
  public boolean checkNetworkAddrExist(String cidr) throws RegionException{
    ArrayList<Subnet> list = listSubnet();
    for(Subnet subnet : list){
      if (cidr.equalsIgnoreCase(subnet.getCidr())){
        return true;
      }
    }
    return false;
  }
  
  public boolean deleteSubnet(String subnetId) throws RegionException{
    
    SubnetApi subnetApi = neutronApi.getSubnetApi(getRegionOne());    
    return subnetApi.delete(subnetId);
  }
 
  
  //ROUTER
  
  public Router createRouter(String routerName, String externalNetworkId) throws RegionException, NeutronException{
    if (!checkRouterNameExist(routerName)){
      Optional<? extends RouterApi> routerApiExtension = neutronApi.getRouterApi(getRegionOne());
      
      if(routerApiExtension.isPresent()){
        RouterApi routerApi = routerApiExtension.get();
        ExternalGatewayInfo externalGateway = ExternalGatewayInfo.builder()
                                                            .networkId(externalNetworkId)
                                          //                  .enableSnat(true)
                                                            .build();
       return routerApi.create(CreateRouter.createBuilder()
                                          .name(routerName).adminStateUp(true)                                       
                                          .externalGatewayInfo(externalGateway)
                                          .build());
        
      }else{
        throw new NeutronException("RouterApi is not present");
      }
      
    }else{
      
      throw new NeutronException("Router name exists on this tenant!");
    }
    
    
    
    
  }
  
  public boolean deleteRouter(String routerId) throws NeutronException, RegionException{
    Optional<? extends RouterApi> routerApiExtension = neutronApi.getRouterApi(getRegionOne());
    
    if(routerApiExtension.isPresent()){
      RouterApi routerApi = routerApiExtension.get();
      return routerApi.delete(routerId);
    }{
      throw new NeutronException("RouterApi is not present");
    }
    
    
  }
  
  public ArrayList<Router> listRouter() throws RegionException, NeutronException{
    
      Optional<? extends RouterApi> routerApiExtension = neutronApi.getRouterApi(getRegionOne());
      
      if(routerApiExtension.isPresent()){
        RouterApi routerApi = routerApiExtension.get();
        return Lists.newArrayList(routerApi.list().concat());
      }{
        throw new NeutronException("RouterApi is not present");
      }
     
  }
  
  public String getRouterIdByName(String routerName) throws RegionException, NeutronException{
    ArrayList<Router> list = listRouter();
    if (list != null){
      for (Router router : list){
        if (routerName.equals(router.getName())){
          return router.getId();
        }
      }
      
      throw new NotFoundByNameException();
    }else{
      throw new NotFoundAny();
    }
  }
  
  
  public boolean checkRouterNameExist(String routerName) throws RegionException{
    try {
      getRouterIdByName(routerName);
    } catch (NeutronException e) {
      return false;
    } 
    return true;
  }
  
  public RouterInterface  addRouterToInterface(String routerId, String subnetId) throws NeutronException, RegionException{
    
    Optional<? extends RouterApi> routerApiExtension = neutronApi.getRouterApi(getRegionOne());
    
    if(routerApiExtension.isPresent()){
      RouterApi routerApi = routerApiExtension.get();
      return routerApi.addInterfaceForSubnet(routerId, subnetId);
    }{
      throw new NeutronException("RouterApi is not present");
    }
    
    
  }
  
  //FLOATING IP
  
  public ArrayList<FloatingIP> listFloatingIp() throws RegionException,  NeutronException {

    Optional<? extends FloatingIPApi> floatingIPApiExtension = neutronApi
        .getFloatingIPApi(getRegionOne());

    if (floatingIPApiExtension.isPresent()) {
      FloatingIPApi floatingIPApi = floatingIPApiExtension.get();
      return Lists.newArrayList(floatingIPApi.list().concat());
    }
    {
      throw new NeutronException("FloatingIp is not present");
    }

  }
  
  public String getFloatingIPAvailable(String floatingIpAddr) throws RegionException, NeutronException{
    ArrayList<FloatingIP> list = listFloatingIp();
    if(list != null){
    for(FloatingIP floatingIp : list){
      if (floatingIpAddr.equals(floatingIp.getFloatingIpAddress())){
        return floatingIp.getId();
      }
      throw new NotFoundByNameException();
    }
    }else{
      throw new NotFoundAny();
    }
    return floatingIpAddr;
  }
  
  public FloatingIP createFloatingIP() throws RegionException, NeutronException{
    Optional<? extends FloatingIPApi> floatingIPApiExtension = neutronApi
        .getFloatingIPApi(getRegionOne());

    if (floatingIPApiExtension.isPresent()) {
      FloatingIPApi floatingIPApi = floatingIPApiExtension.get();
      
      floatingIPApi.create(CreateFloatingIP.createBuilder(getNetworkIdByName(OpenstackConfiguration.EXTERNAL_NETWORK)).build());
    }
    {
      throw new NeutronException("FloatingIp is not present");
    }
  }
   public void close() throws IOException {
      Closeables.close(neutronApi, true);
   }
}
