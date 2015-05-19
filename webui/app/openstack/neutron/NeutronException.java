package openstack.neutron;

public class NeutronException extends Exception{
  /**
   * 
   */


  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private String message = null;
  
  public NeutronException() {
      super();
  }

  public NeutronException(String message) {
      super(message);
      this.message = message;
  }

  public NeutronException(Throwable cause) {
      super(cause);
  }

  @Override
  public String toString() {
      return message;
  }

  @Override
  public String getMessage() {
      return message;
  }



}
