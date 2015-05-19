package openstack.exception;

public class RegionException extends Exception{
  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private String message = null;
  
  public RegionException() {
      super();
  }

  public RegionException(String message) {
      super(message);
      this.message = message;
  }

  public RegionException(Throwable cause) {
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

