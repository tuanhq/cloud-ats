/**
 * 
 */
package org.ats.services.functional.action;

import java.io.IOException;

/**
 * @author TrinhTV3
 *
 * Email: TrinhTV3@fsoft.com.vn
 */
public class Pause implements IAction{

  private String waitTime;
  
  public Pause(String waitTime) {
    this.waitTime = waitTime;
  }
  
  public String transform() throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("try { Thread.sleep(");
    sb.append(Integer.parseInt(waitTime)).append("l)");
    sb.append("; } catch (Exception e) { throw new RuntimeException(e); }\n");
    return sb.toString();
  }

  public String getAction() {
    return "pause";
  }

}