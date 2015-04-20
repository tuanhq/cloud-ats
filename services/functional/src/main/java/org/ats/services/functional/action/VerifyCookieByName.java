/**
 * 
 */
package org.ats.services.functional.action;

import java.io.IOException;

import org.ats.services.functional.Value;
import org.rythmengine.Rythm;

/**
 * @author TrinhTV3
 *
 * Email: TrinhTV3@fsoft.com.vn
 */
public class VerifyCookieByName implements IAction {

  private boolean negated;
  
  private Value name;
  
  private Value value;
  
  /**
   * 
   */
  public VerifyCookieByName(Value name, Value value, boolean negated) {

    this.name = name;
    this.value = value;
    this.negated = negated;
  }
  public String transform() throws IOException {
    
    StringBuilder sb = new StringBuilder("if (").append(negated ? "" : "!");
    sb.append("wd.manage().getCookieNamed(@name).getValue().equals(@value)) {\n");
    sb.append("System.out.println(\"").append(negated ? "!" : "").append("verifyCookieByName failed\");\n");
    sb.append("}\n");
    return Rythm.render(sb.toString(), name.transform(), value.transform());
  }

  public String getAction() {
    return "verifyCookieByName";
  }

  
}
