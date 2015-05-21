package openstack.nova;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import openstack.OpenstackConfiguration;
import openstack.exception.RegionException;

import org.jclouds.ContextBuilder;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.Flavor;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.Image;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.extensions.AttachInterfaceApi;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ImageApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset.Entry;
import com.google.common.io.Closeables;
import com.google.inject.Module;

public class JCloudsNova implements Closeable {
    private final NovaApi novaApi;    
    private final Set<String> regions; 
    private final String provider = OpenstackConfiguration.NOVA_PROVIDER;  
    private final String endpoint = OpenstackConfiguration.KEYSTONE_AUTH_URL;
    private final ExecutorService pool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws IOException {
        JCloudsNova jcloudsNova = new JCloudsNova("demo", "demo", "DEMO_PASS");

        try {
//            jcloudsNova.listServers();
//            jcloudsNova.listImage();
//            jcloudsNova.listFlavor();
//            jcloudsNova.createServer();
//          jcloudsNova.test();
            //jcloudsNova.close();
//          for(FloatingIP floatingIp : jcloudsNova.listFloatingIpAvailable()){
//            //if(floatingIp.getFixedIp()==null){
//              System.out.println(floatingIp.toString()); 
//            //}
//            
//          }
//          for(FloatingIP floatingIp : jcloudsNova.listFloatingIp()){
//            //if(floatingIp.getFixedIp()==null){
//              System.out.println(floatingIp.toString()); 
//            //}
//            
//          }
//          jcloudsNova.createServer("testMultiSubnet", "2013ba5b-89f4-4fe7-ad5d-f463d79258d7", "1", "30971863-97e7-4838-ae76-d7f0fa174926");
          
          jcloudsNova.getFirstIpOfServer("demo-instance1");
          System.out.println(jcloudsNova.getFirstIpOfServer("demo-instance1"));
          System.out.println(jcloudsNova.getFirstIpOfServer(jcloudsNova.getServerByName("demo-instance1")));
          System.out.println(jcloudsNova.getFixIpByFloatingIp("192.168.100.29"));
          
//          Multimap<String, Address> addresses  = jcloudsNova.getServerByName("demo-instance1").getAddresses();
//          
//          
//          
//          Iterator <Address> entries =  addresses.get("demo-net").iterator();
//          while(entries.hasNext()){
//            Address address = (Address) entries.next();
//            System.out.println(address);
//          }
//          
////          Iterator entries = addresses.asMap().entrySet().iterator();
////          while(entries.hasNext()){
////            
////          }
//          
          
          
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jcloudsNova.close();
        }
    }

    public JCloudsNova(String tenantName, String userName, String password) {
        Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
           
        String identity = tenantName+ ":" + userName; // tenantName:userName
        String credential = password;

        novaApi = ContextBuilder.newBuilder(provider)
                .endpoint(endpoint)
                .credentials(identity, credential)
                .modules(modules)
                .buildApi(NovaApi.class);        
        regions = novaApi.getConfiguredRegions();
        
        
    }
    public String getRegionOne() throws RegionException{
      for(String region:regions){
        return region;
      }
      throw new RegionException("Region not found!");
    }
    
    //SEVER
    
    public ArrayList<Server> listServers() throws RegionException {
      ServerApi serverApi = novaApi.getServerApi(getRegionOne());
      
      return Lists.newArrayList(serverApi.listInDetail().concat());        
    }
    
    public String getServerIdByName(String serverName) throws RegionException, NotFoundByNameException, NotFoundAny{
      ArrayList<Server> list = listServers();
      if(list !=null ){
        for (Server server : list){
          if(serverName.equals(server.getName())){
              return server.getId();
            }
          }
        throw new NotFoundByNameException();
      }else{
        throw new NotFoundAny();
      }
    }
    
    public Server getServerByName(String serverName) throws RegionException, NotFoundByNameException, NotFoundAny{
      ArrayList<Server> list = listServers();
      if(list !=null ){
        for (Server server : list){
          if(serverName.equals(server.getName())){
              return server;
            }
          }
        throw new NotFoundByNameException();
      }else{
        throw new NotFoundAny();
      }
    }
    
    public boolean checkServerNameExist(String serverName) throws Exception{
      try {
        if (getServerIdByName(serverName) != null ) {
          return true;
        }
      } catch (Exception e) {
        if (!(e instanceof NovaException)){
          throw e;
        }
      }
      return false;      
      
    }

    
    
    public ServerCreated createServer(String serverName, String imageId, String flavorId, String networkId) throws RegionException{
        
      ServerApi serverApi = novaApi.getServerApi(getRegionOne()); 
      return  serverApi.create(serverName, imageId, flavorId, CreateServerOptions.Builder.networks(networkId));
      
    }
    
    public Future<ServerCreated> createServerAsync(final String serverName, final String imageId, final String flavorId, final String networkId) throws RegionException{
      return pool.submit(new Callable<ServerCreated>(){
        
        @Override
        public ServerCreated call() throws Exception {
          // TODO Auto-generated method stub
         return  createServer(serverName, imageId, flavorId, networkId);
          
        }
        
      });
    }
    
    
    public Future<ServerCreated> createSystemVM(String name , String networkId) throws Exception {
      
      String imageId = getImageIdByName("cats-sys-image");
      String flavorId = getFlavorIdByName("cloud-sys");
      return createServerAsync(name, imageId, flavorId, networkId);
    }

    public Future<ServerCreated> createNormalGuiVM(String name, String networkId) throws Exception {
      String imageId = getImageIdByName("cats-ui-image");
      String flavorId = getFlavorIdByName("m1.small");
      return createServerAsync(name, imageId, flavorId, networkId);
    }

    public Future<ServerCreated>  createNormalNonGuiVM(String name, String networkId) throws Exception {
      String imageId = getImageIdByName("cats-non-ui-image");
      String flavorId = getFlavorIdByName("m1.small");
      return createServerAsync(name, imageId, flavorId, networkId);
      
    }
    
    
    public Void serverAction(String serverId, ServerAction action) throws RegionException, NovaException{
      
      ServerApi serverApi = novaApi.getServerApi(getRegionOne());
      switch (action) {
      case START:
        serverApi.start(serverId);
        break;
      case STOP: 
        serverApi.stop(serverId);
      case DELETE:
        serverApi.delete(serverId);
      default:
        throw new  NovaException("Action not support");       
      }
      return null;
      
    }
    public Future<Void > serverActionAsync (final String serverId, final ServerAction action)throws RegionException, NovaException{
      return pool.submit(new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          // TODO Auto-generated method stub
          return  serverAction(serverId, action);
        }
      });
    }
    
    //IP OF SERVER
    public void addFloatingIpToServer(String ipAddress, String serverId)
        throws RegionException, NovaException {
      Optional<? extends FloatingIPApi> floatingIpApiExtension = novaApi
          .getFloatingIPApi(getRegionOne());
      if (floatingIpApiExtension.isPresent()) {
        FloatingIPApi floatingIpApi = floatingIpApiExtension.get();
        
        floatingIpApi.addToServer(ipAddress, serverId);
      } else {
        
        throw new NovaException("Floating IP net present");
      }

    }
    
    public String  getFirstIpOfServer(String serverName) throws NotFoundByNameException, NotFoundAny, RegionException{
      Server server = getServerByName(serverName);
      
      Iterator<java.util.Map.Entry<String, Address>> iterator = server.getAddresses().entries().iterator();
      while(iterator.hasNext()){
        return iterator.next().getValue().getAddr();        
      }
      return null;
    }
    public String  getFirstIpOfServer(Server server) throws NotFoundByNameException, NotFoundAny, RegionException{      
      
      Iterator<java.util.Map.Entry<String, Address>> iterator = server.getAddresses().entries().iterator();
      while(iterator.hasNext()){
        return iterator.next().getValue().getAddr();        
      }
      return null;
    }
    
    public void addFixIpToServer(String serverId, String portId) throws RegionException, NovaException {
      Optional<? extends AttachInterfaceApi> attachInterfaceApiExtension = novaApi.getAttachInterfaceApi(getRegionOne());
      if (attachInterfaceApiExtension.isPresent()) {
        AttachInterfaceApi attachInterfaceApi = attachInterfaceApiExtension.get();
        attachInterfaceApi.create(serverId, portId);
        
      } else {
        
        throw new NovaException("Floating IP net present");
      }

    }

    
    
    public void removeFloatingIpFromServer(String ipAddress, String serverId)    throws RegionException, NovaException {
      Optional<? extends FloatingIPApi> floatingIpApiExtension = novaApi.getFloatingIPApi(getRegionOne());
      if (floatingIpApiExtension.isPresent()) {
        FloatingIPApi floatingIpApi = floatingIpApiExtension.get();        
        floatingIpApi.removeFromServer(ipAddress, serverId);
        
      } else {
        
        throw new NovaException("Floating IP net present");
      }

    }

    
    public ArrayList<FloatingIP> listFloatingIp() throws NovaException, RegionException{
      Optional<? extends FloatingIPApi> floatingIpApiExtension = novaApi.getFloatingIPApi(getRegionOne());
      if (floatingIpApiExtension.isPresent()) {
        FloatingIPApi floatingIpApi = floatingIpApiExtension.get();
         return Lists.newArrayList(floatingIpApi.list());       
      } else {
        
        throw new NovaException("Floating IP net present");
      }
    }
    
    public ArrayList<FloatingIP> listFloatingIpAvailable() throws NovaException, RegionException{
      ArrayList<FloatingIP> list = listFloatingIp();
      ArrayList<FloatingIP> listAvailable = new ArrayList<FloatingIP>();
      if(list != null){
      for(FloatingIP floatIp:list){
       if(floatIp.getFixedIp() == null){
         listAvailable.add(floatIp);
       }
      }
      return listAvailable;      
      } else {
        
        throw new NovaException("Floating IP net present");
      }
    }
    
    public String getFixIpByFloatingIp(String floatingIp) throws NovaException, RegionException{
      
    ArrayList<FloatingIP> list = listFloatingIp();
    if (list != null) {
      for (FloatingIP floatIp : list) {
        if (floatingIp.equals(floatIp.getIp())) {
          return floatIp.getFixedIp();
        }
      }

    }
    return null;
    }
    
    
    //IMAGE
    
    public  ArrayList<Image> listImage() throws RegionException {
      
      ImageApi imageApi = novaApi.getImageApi(getRegionOne());
      return Lists.newArrayList(imageApi.listInDetail().concat());
    }
    public String getImageIdByName (String imageName) throws RegionException, NotFoundByNameException, NotFoundAny{
      ArrayList<Image> list = listImage();
      if(list!=null){
        for(Image image : list){
          if (imageName.equals(image.getName())){
            return image.getId();
          }
        }
        throw new NotFoundByNameException();
      }else{
        throw new NotFoundAny();
      }
    }
    
    //FLAVOR
    
    public ArrayList<Flavor> listFlavor() throws RegionException {
      FlavorApi flavorApi = novaApi.getFlavorApi(getRegionOne());
      return Lists.newArrayList(flavorApi.listInDetail().concat());
      
    }
    
    public String getFlavorIdByName (String flavorName) throws RegionException, NotFoundByNameException, NotFoundAny{
      ArrayList<Flavor> list = listFlavor();
      if(list!=null){
        for(Flavor flavor : list){
          if (flavorName.equals(flavor.getName())){
            return flavor.getId();
          }
        }
        throw new NotFoundByNameException();
      }else{
        throw new NotFoundAny();
      }
    }
    
  
    
    public void close() throws IOException {
        Closeables.close(novaApi, true);
    }
}