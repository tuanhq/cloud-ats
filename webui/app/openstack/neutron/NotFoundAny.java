package openstack.neutron;

import openstack.exception.ExeceptionMessage;

public class NotFoundAny extends NeutronException{

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private String message = null;
  
  public NotFoundAny() {
     this.message = ExeceptionMessage.NetworkNotFoundByName;
  }

  public NotFoundAny(String message) {
      super(message);
      this.message = message;
  }

  public NotFoundAny(Throwable cause) {
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
