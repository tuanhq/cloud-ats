package openstack.nova;

public class NovaException extends Exception{
  /**
   * 
   */


  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private String message = null;
  
  public NovaException() {
      super();
  }

  public NovaException(String message) {
      super(message);
      this.message = message;
  }

  public NovaException(Throwable cause) {
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
