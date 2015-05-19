package openstack.nova;

import openstack.exception.ExeceptionMessage;

public class NotFoundByNameException extends NovaException {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private String message = null;
  
  public NotFoundByNameException() {
     this.message = ExeceptionMessage.NovaNotFoundByName;
  }

  public NotFoundByNameException(String message) {
      super(message);
      this.message = message;
  }

  public NotFoundByNameException(Throwable cause) {
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
