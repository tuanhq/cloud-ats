/**
 * 
 */
package azure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Future;

import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.junit.Assert;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.microsoft.windowsazure.core.pipeline.apache.ApacheConfigurationProperties;
import com.microsoft.windowsazure.core.utils.KeyStoreType;
import com.microsoft.windowsazure.management.ManagementClient;
import com.microsoft.windowsazure.management.ManagementService;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.ComputeManagementService;
import com.microsoft.windowsazure.management.compute.models.HostedServiceGetDetailedResponse;
import com.microsoft.windowsazure.management.compute.models.HostedServiceGetDetailedResponse.Deployment;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse;
import com.microsoft.windowsazure.management.compute.models.PostShutdownAction;
import com.microsoft.windowsazure.management.compute.models.Role;
import com.microsoft.windowsazure.management.compute.models.RoleInstance;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineRoleSize;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineRoleType;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineShutdownParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineShutdownRolesParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineStartRolesParameters;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;
import com.microsoft.windowsazure.management.models.RoleSizeListResponse.RoleSize;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Jan 13, 2015
 */
public class AzureClient {

  /** .*/
  private final String serviceName = "cloud-ats";

  /** .*/
  private final String deploymentName = "cats-portal";

  /** .*/
  private String subscriptionId;

  /** .*/
  private String keyStoreLocation;

  /** .*/
  private String keyStorePassword;

  /** .*/
  private ManagementClient mgmClient;

  /** .*/
  private ComputeManagementClient computeClient;

  /** .*/
  private Configuration config;

  public AzureClient(String subcriptionId, String keyStoreLocation, String keyStorePassword) throws IOException {
    this.subscriptionId = subcriptionId;
    this.keyStoreLocation = keyStoreLocation;
    this.keyStorePassword = keyStorePassword;
    
    this.mgmClient = ManagementService.create(getConfiguration());
    this.computeClient = ComputeManagementService.create(getConfiguration());
  }

  public Configuration getConfiguration() throws IOException {
    if (config == null) {
      config = ManagementConfiguration.configure(null, subscriptionId, keyStoreLocation, keyStorePassword, KeyStoreType.jks); 
      config.setProperty(ApacheConfigurationProperties.PROPERTY_RETRY_HANDLER, new DefaultHttpRequestRetryHandler());
    }
    return config;
  }

  public ComputeManagementClient getComputeManagementClient() throws IOException {
    return computeClient;
  }

  public ManagementClient getManagementClient() throws IOException {
    return mgmClient;
  }
  
  public static String[] getAvailabilityOfferingNames() {
    return new String[] { VirtualMachineRoleSize.EXTRASMALL, VirtualMachineRoleSize.SMALL, VirtualMachineRoleSize.MEDIUM };
  }
  
  public RoleSize getOfferingByName(String name) throws Exception {
    for (RoleSize offering : mgmClient.getRoleSizesOperations().list().getRoleSizes()) {
      if (name.equals(offering.getName())) return offering;
    }
    return null;
  }

  public Future<OperationStatusResponse> createSystemVM(String name)  throws Exception {
    return createVM(name, "cats-sys-image", VirtualMachineRoleSize.MEDIUM);
  }
  
  public Future<OperationStatusResponse> createNormalGuiVM(String name) throws Exception {
    return createVM(name, "cats-ui-image", VirtualMachineRoleSize.SMALL);
  }
  
  public Future<OperationStatusResponse> createNormalNonGuiVM(String name) throws Exception {
    return createVM(name, "cats-non-ui-image", VirtualMachineRoleSize.SMALL);
  } 
  
  public Future<OperationStatusResponse> createVM(String name, String template, String offering) throws Exception {
    VirtualMachineCreateParameters parameters = new VirtualMachineCreateParameters();
    parameters.setRoleName(name);
    parameters.setProvisionGuestAgent(true);
    parameters.setRoleSize(offering);
    parameters.setVMImageName(template);
    parameters.setAvailabilitySetName("cloud-ats-set");
    Future<OperationStatusResponse> result = getComputeManagementClient().getVirtualMachinesOperations().createAsync(serviceName, deploymentName, parameters);
    return result;
  }
  
  public RoleInstance getVirutalMachineByName(String name) throws Exception {
    ArrayList<Deployment> deployments = computeClient.getHostedServicesOperations().getDetailed(serviceName).getDeployments();
    for (Deployment deployment : deployments) {
      if (deploymentName.equals(deployment.getName())) {
        for (RoleInstance instance : deployment.getRoleInstances()) {
          if(instance.getRoleName().equals(name)) return instance;
        }
      }
    }
    //
    return null;
  }
  
  public Future<OperationStatusResponse> deleteVirtualMachineByName(String name) throws Exception {
    return computeClient.getVirtualMachinesOperations().deleteAsync(serviceName, deploymentName, name, true);
  }
  
  public Future<OperationStatusResponse> startVirtualMachineByName(String name) throws Exception {
    return computeClient.getVirtualMachinesOperations().startAsync(serviceName, deploymentName, name);
  }
  
  public Future<OperationStatusResponse> stopVirtualMachineByName(String name) throws Exception {
    VirtualMachineShutdownParameters parameters = new VirtualMachineShutdownParameters();
    parameters.setPostShutdownAction(PostShutdownAction.Stopped);
    return computeClient.getVirtualMachinesOperations().shutdownAsync(serviceName, deploymentName, name, parameters);
  }
  
  public Future<OperationStatusResponse> stopVirtualMachinesByNames(String... names) throws Exception {
    VirtualMachineShutdownRolesParameters parameters = new VirtualMachineShutdownRolesParameters();
    parameters.setPostShutdownAction(PostShutdownAction.Stopped);
    parameters.setRoles(new ArrayList<String>(Arrays.asList(names)));
    return computeClient.getVirtualMachinesOperations().shutdownRolesAsync(serviceName, deploymentName, parameters);
  }
  
  public Future<OperationStatusResponse> startVirtualMachinesByNames(String... names) throws Exception {
    VirtualMachineStartRolesParameters parameters = new VirtualMachineStartRolesParameters();
    parameters.setRoles(new ArrayList<String>(Arrays.asList(names)));
    return computeClient.getVirtualMachinesOperations().startRolesAsync(serviceName, deploymentName, parameters);
  }
  
  public ArrayList<RoleInstance> waitVirtualMachinesForStatus(String status, long timeout, String... vmNames) throws Exception {
    return waitVirtualMachinesForStatus(status, System.currentTimeMillis(), timeout, vmNames);
  }
  public ArrayList<RoleInstance> waitVirtualMachinesForStatus(String status, long start, long timeout, String... vmNames) throws Exception {
    ArrayList<RoleInstance> vms = new ArrayList<RoleInstance>();
    for (String name : vmNames) {
      RoleInstance vm = getVirutalMachineByName(name);
      if (status.equals(vm.getInstanceStatus())) {
        vms.add(vm);
      }
    }
    
    if (System.currentTimeMillis() - start > timeout) {
      return vms; 
    }
    
    if (vms.size() != vmNames.length) {
      Thread.sleep(1000);
      return waitVirtualMachinesForStatus(status, start, timeout, vmNames);
    }
    return vms;
  }

  /**
   * there is no dedicated vm list methods, has to filter through hosted service, and deployment, rolelist to find out the vm list
   * role that has VirtualMachineRoleType.PersistentVMRole property is a vm
   * @return
   * @throws Exception
   */
  public ArrayList<Role> listVirtualMachines() throws Exception {
    ArrayList<Role> vmlist = new ArrayList<Role>();
    HostedServiceListResponse hostedServiceListResponse = computeClient.getHostedServicesOperations().list();
    ArrayList<HostedServiceListResponse.HostedService> hostedServicelist = hostedServiceListResponse.getHostedServices();
    Assert.assertNotNull(hostedServicelist); 

    for (HostedServiceListResponse.HostedService hostedService : hostedServicelist) {
      if (hostedService.getServiceName().contains(serviceName)) {
        HostedServiceGetDetailedResponse hostedServiceGetDetailedResponse = computeClient.getHostedServicesOperations().getDetailed(hostedService.getServiceName());

        ArrayList<HostedServiceGetDetailedResponse.Deployment> deploymentlist = hostedServiceGetDetailedResponse.getDeployments();

        for (HostedServiceGetDetailedResponse.Deployment deployment : deploymentlist) {
          ArrayList<Role> rolelist = deployment.getRoles();
          Assert.assertNotNull(rolelist);

          for (Role role : rolelist) {
            if ((role.getRoleType()!=null) && (role.getRoleType().equalsIgnoreCase(VirtualMachineRoleType.PersistentVMRole.toString()))) {
              vmlist.add(role);
            }
          }
        }
      }
    }
    
    //
    return vmlist;
  }
}
