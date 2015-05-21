package openstack;

public class OpenstackCredential {
  private String tenant;
  private String username;
  private String password;
  public OpenstackCredential() {
   
  }
  public OpenstackCredential(String tenant, String username, String password) {
    this.tenant = tenant;
    this.username = username;
    this.password = password;
  }
  public String getTenant() {
    return tenant;
  }
  public void setTenant(String tenant) {
    this.tenant = tenant;
  }
  public String getUsername() {
    return username;
  }
  public void setUsername(String username) {
    this.username = username;
  }
  public String getPassword() {
    return password;
  }
  public void setPassword(String password) {
    this.password = password;
  }

}
