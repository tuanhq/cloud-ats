/**
 * 
 */
package controllers.organization;

import interceptor.AuthenticationInterceptor;
import interceptor.Authorization;
import interceptor.WizardInterceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.ats.component.usersmgt.UserManagementException;
import org.ats.component.usersmgt.feature.Feature;
import org.ats.component.usersmgt.feature.FeatureDAO;
import org.ats.component.usersmgt.group.Group;
import org.ats.component.usersmgt.group.GroupDAO;
import org.ats.component.usersmgt.role.Permission;
import org.ats.component.usersmgt.role.Role;
import org.ats.component.usersmgt.role.RoleDAO;
import org.ats.component.usersmgt.user.User;
import org.ats.component.usersmgt.user.UserDAO;

import play.api.templates.Html;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import scala.collection.mutable.StringBuilder;
import views.html.organization.index;
import views.html.organization.indexajax;
import views.html.organization.leftmenu;
import views.html.organization.feature.feature;
import views.html.organization.feature.features;
import views.html.organization.group.group;
import views.html.organization.group.groups;
import views.html.organization.role.role;
import views.html.organization.role.roles;
import views.html.organization.user.user;
import views.html.organization.user.users;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.BasicDBObject;

import controllers.Application;
/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Aug 4, 2014
 */
@With({WizardInterceptor.class, AuthenticationInterceptor.class})
@Authorization(feature = "Organization", operation = "Administration")
public class Organization extends Controller {
  
  /**
   * Set session current group by group id pass through query string.
   * Perform set if current user has right administration permission on this group 
   * Or has right administration permission on parent of this group
   * @param requestGroupId
   * @return
   * @throws UserManagementException
   */
  public static Group setCurrentGroup(String requestGroupId) throws UserManagementException {
    User currentUser = UserDAO.getInstance(Application.dbName).findOne(session("user_id"));
    
    //Check right permission on group pass through query string
    if (requestGroupId != null) {
      
      if (isSystem(currentUser)) {
        return GroupDAO.getInstance(Application.dbName).findOne(requestGroupId);
      }
      
      //Check have right administration permission on exactly group
      BasicDBObject query = new BasicDBObject("group_id", requestGroupId);
      query.put("name", "Administration");
      query.put("user_ids", Pattern.compile(currentUser.getId()));
      Collection<Role> administration = RoleDAO.getInstance(Application.dbName).find(query);
      
      if (!administration.isEmpty()) {
        return GroupDAO.getInstance(Application.dbName).findOne(requestGroupId);
      }
      
      //Or on parent group
      Group requestGroup = GroupDAO.getInstance(Application.dbName).findOne(requestGroupId);
      for (Group g : getAministrationGroup(currentUser)) {
        if (g.getAllChildren().contains(requestGroup)) return requestGroup;
      }
      
    }
    
    if (session("group_id") == null) {
      Collection<Group> adGroups = getAdministrationGroup();
      return adGroups.isEmpty() ? null : adGroups.iterator().next();
    }
    
    Group currentGroup = GroupDAO.getInstance(Application.dbName).findOne(session("group_id"));
    if (currentGroup == null) {
      session().remove("group_id");
      currentGroup = setCurrentGroup(null);
    } else if (!isSystem(currentUser)) {
      //double check current group
      BasicDBObject query = new BasicDBObject("name", "Administration");
      query.put("system", true);
      query.put("user_ids", Pattern.compile(currentUser.getId()));
      query.put("group_id", currentGroup.getId());
      if (RoleDAO.getInstance(Application.dbName).find(query).isEmpty()) {
        LinkedList<Group> parents = GroupDAO.getInstance(Application.dbName).buildParentTree(currentGroup);
        for (Group group : parents) {
          query.put("group_id", group.getId());
          if (!RoleDAO.getInstance(Application.dbName).find(query).isEmpty()) {
            return currentGroup;
          }
        }
        session().remove("group_id");
        currentGroup = setCurrentGroup(null);
      }
    }
    return currentGroup;
  }

  /**
   * Present a full of content
   * @return
   * @throws UserManagementException
   */
  public static Result index() throws UserManagementException {
    Group current = setCurrentGroup(request().getQueryString("group"));
    if (current == null) return forbidden(views.html.forbidden.render());
    
    session().put("group_id", current.getId());
    
    Html body = bodyComposite(request().queryString());
    return ok(index.render(request().getQueryString("nav") == null ? "group" : request().getQueryString("nav"), body, current.getId()));
  }
  
  /**
   * Present a part of content to call by ajax.
   * @return
   * @throws UserManagementException
   */
  public static Result indexAjax() throws UserManagementException {
    Group current = setCurrentGroup(request().getQueryString("group"));
    if (current == null) return forbidden(views.html.forbidden.render());
    
    session().put("group_id", current.getId());
    
    Html body = bodyComposite(request().queryString());
    return ok(indexajax.render("group", body, current.getId()));
  }
  
  public static Result body() throws UserManagementException {
    Group currentGroup = setCurrentGroup(request().getQueryString("group"));
    if (currentGroup == null) return forbidden(views.html.forbidden.render());
    
    session().put("group_id", currentGroup.getId());
    
    ObjectNode json = Json.newObject();

    Html leftMenu = leftmenu.render(request().getQueryString("nav") == null ? "group" : request().getQueryString("nav"), currentGroup.getId());
    
    Html body = bodyComposite(request().queryString());
    
    Html breadcrumb = groupBreadcrumb(request().getQueryString("nav") == null ? "group" : request().getQueryString("nav"), currentGroup.getId());
    
    StringBuilder sb = new StringBuilder();
    LinkedList<Group> parents = GroupDAO.getInstance(Application.dbName).buildParentTree(currentGroup);
    for (Group parent : parents) {
      sb.append(" / ").append(parent.getString("name"));
    }
    sb.append(" / ").append(currentGroup.getString("name"));
    
    json.put("breadcrumb", breadcrumb.toString());
    json.put("navbar", sb.toString());
    json.put("leftmenu", leftMenu.toString());
    json.put("body", body.toString());
    json.put("group", currentGroup.getId());
    
    return ok(json);
  }
  
  /**
   * Build a part of content body base on current state.
   * @param parameters
   * @return
   * @throws UserManagementException
   */
  private static Html bodyComposite(Map<String, String[]> parameters) throws UserManagementException {
    
    String nav = parameters.containsKey("nav") ? parameters.get("nav")[0] : "group";
    User currentUser = UserDAO.getInstance(Application.dbName).findOne(session("user_id"));
    Group currentGroup = GroupDAO.getInstance(Application.dbName).findOne(session("group_id"));
    
    if ("group".equals(nav)) {
      
      StringBuilder sb = new StringBuilder();
      List<Group> all = listGroupVisible();
      for (Group g : all) {
        sb.append(group.render(g));
      }
      
      return groups.render(new Html(sb), isSystem(currentUser));
    
    } else if ("user".equals(nav)) {
      
      StringBuilder sb = new StringBuilder();
      List<User> all = listUserVisible();
      for (User u : all) {
        sb.append(user.render(u, isSystem(currentUser), currentGroup.getRoles()));
      }
      
      return users.render(new Html(sb), isSystem(currentUser));
          
    } else if ("role".equals(nav)) {
      StringBuilder sb = new StringBuilder();
      for (Role r : currentGroup.getRoles()) {
        sb.append(role.render(r, new ArrayList<Permission>(r.getPermissions())));
      }
      return roles.render(new Html(sb));
    } else if ("feature".equals(nav)) {
      StringBuilder sb = new StringBuilder();
      for (Feature f : currentGroup.getFeatures()) {
        sb.append(feature.render(f, isSystem(currentUser)));
      }
      return features.render(new Html(sb));
    }
    return null;
  }
  
  /**
   * Return user's group with highest level
   * @param u
   * @return
   * @throws UserManagementException
   */
  public static Group getHighestGroupBelong(User u) throws UserManagementException {
    List<Group> list = new ArrayList<Group>(u.getGroups());
    Collections.sort(list, new Comparator<Group>() {
      @Override
      public int compare(Group o1, Group o2) {
        int l1 = o1.getInt("level");
        int l2 = o2.getInt("level");
        return l2 - l1;
      }
    });
    return list.isEmpty() ? null : list.get(0);
  }
  
  /**
   * List all of groups those are visible for current group;
   * @return
   * @throws UserManagementException
   */
  private static List<Group> listGroupVisible() throws UserManagementException {
    Group current = GroupDAO.getInstance(Application.dbName).findOne(session("group_id"));
    
    List<Group> all = null;
    if (current.getBoolean("system")) {
      all = new ArrayList<Group>(GroupDAO.getInstance(Application.dbName).find(new BasicDBObject()));
    }  else {
      all = new ArrayList<Group>(current.getAllChildren());
    }
    
    Collections.sort(all, new Comparator<Group>() {
      @Override
      public int compare(Group o1, Group o2) {
        int l1 = o1.getInt("level");
        int l2 = o2.getInt("level");
        return l1 - l2;
      }
    });
    return all;
  }
  
  private static List<User> listUserVisible() throws UserManagementException {
    Group current = GroupDAO.getInstance(Application.dbName).findOne(session("group_id"));
    List<User> all = null;
    if (current.getBoolean("system")) {
      all = new ArrayList<User>(UserDAO.getInstance(Application.dbName).find(new BasicDBObject()));
    } else {
      Pattern p = Pattern.compile(current.getId());
      Collection<User> users = UserDAO.getInstance(Application.dbName).find(new BasicDBObject("group_ids", p));
      all = new ArrayList<User>(users);
    }
    return all;
  }
  
  /**
   * Build a list of group have administration permission of current user
   * @return The list of groups order by lowest level.
   * @throws UserManagementException
   */
  static Collection<Group> getAdministrationGroup() throws UserManagementException {
    User user = UserDAO.getInstance(Application.dbName).findOne(session("user_id"));
    
    if (user.getBoolean("system")) {
      return GroupDAO.getInstance(Application.dbName).find(new BasicDBObject("system", true));
    }
    
    return getAministrationGroup(user);
  }
  
  /**
   * List all groups have administration permission of specified user
   * @param user
   * @return
   * @throws UserManagementException
   */
  static Collection<Group> getAministrationGroup(User user) throws UserManagementException {
    List<Group> list = new ArrayList<Group>();
    
    for (Group g : user.getGroups()) {
      BasicDBObject query = new BasicDBObject("name", "Administration");
      query.put("system", true);
      query.put("group_id", g.getId());
      query.put("user_ids", Pattern.compile(user.getId()));
      if (!RoleDAO.getInstance(Application.dbName).find(query).isEmpty()) {
        list.add(g);
      }
    }
    
    Collections.sort(list, new Comparator<Group>() {
      @Override
      public int compare(Group o1, Group o2) {
        int l1 = o1.getInt("level");
        int l2 = o2.getInt("level");
        return l1 - l2;
      }
    });
    return list;
  }
  
  /**
   * Build the group path which depends on current group. The group path presents the relationship of current group.
   * @param nav
   * @return
   * @throws UserManagementException
   */
  public static Html groupBreadcrumb(String nav, String group_id) throws UserManagementException {
    User currentUser = UserDAO.getInstance(Application.dbName).findOne(session("user_id"));
    Group currentGroup = setCurrentGroup(group_id);
    session().put("group_id", currentGroup.getId());
    
    StringBuilder sb = new StringBuilder();
    if (currentGroup.getBoolean("system")) {
      sb.append("<li class='active'>").append(currentGroup.get("name")).append("</li>");
      return new Html(sb);
    } else if (isSystem(currentUser)) {
      Group sys = GroupDAO.getInstance(Application.dbName).find(new BasicDBObject("system", true)).iterator().next();
      String href = routes.Organization.index().toString() + "?nav=" + nav + "&group=" + sys.getId();
      String ajax = routes.Organization.body().toString() + "?nav=" + nav + "&group=" + sys.getId();
      sb.append("<li>").append("<a href='").append(href).append("' ajax-url='").append(ajax).append("'>").append(sys.get("name")).append("</a> <span class='divider'>/</span></li>");
    }
    
    LinkedList<Group> parents = GroupDAO.getInstance(Application.dbName).buildParentTree(currentGroup);
    
    if (!isSystem(currentUser)) {
      //Prevent current user lookup parent group with no right permission
      Collection<Group> adGroup = getAministrationGroup(currentUser);
      Set<Group> allChildren = new HashSet<Group>();
      for (Group g : adGroup) {
        allChildren.addAll(g.getAllChildren());
      }
      
      for (Group p : parents) {
        if (adGroup.contains(p) || allChildren.contains(p)) {
          String href = routes.Organization.index().toString() + "?nav=" + nav + "&group=" + p.getId();
          String ajax = routes.Organization.body().toString() + "?nav=" + nav + "&group=" + p.getId();
          sb.append("<li>").append("<a href='").append(href).append("' ajax-url='").append(ajax).append("'>").append(p.get("name"));
        } else {
          sb.append("<li class='active'>").append(p.get("name")).append("</li>");
        }
        sb.append("</a> <span class='divider'> / </span></li>");
      }
    } else {
      for (Group p : parents) {
        String href = routes.Organization.index().toString() + "?nav=" + nav + "&group=" + p.getId();
        String ajax = routes.Organization.body().toString() + "?nav=" + nav + "&group=" + p.getId();
        sb.append("<li>").append("<a href='").append(href).append("' ajax-url='").append(ajax).append("'>").append(p.get("name"));
        sb.append("</a> <span class='divider'> / </span></li>");
      }
    }
    
    sb.append("<li class='active'>").append(currentGroup.get("name")).append("</li>");
    return new Html(sb);
  }

  /**
   * Count entities of current group. Use for left menu.
   * @param nav The alias of entity
   * @return
   * @throws UserManagementException
   */
  public static long count(String nav) throws UserManagementException {
    Group current = GroupDAO.getInstance(Application.dbName).findOne(session("group_id"));
    if ("role".equals(nav))
      return current.getRoles().size();
    else if ("feature".equals(nav))
      return current.getFeatures().size();
    else if ("group".equals(nav)) {
      if (current.getBoolean("system")) {
        return GroupDAO.getInstance(Application.dbName).count();
      }
      return current.getAllChildren().size();
    } 
    else if ("user".equals(nav)) {
      if (current.getBoolean("system")) {
        return UserDAO.getInstance(Application.dbName).count();
      }
      return current.getUsers().size();
    }
    return -1;
  }
  
  /**
   * Filter group and build presentation of content. The content should update by ajax.
   * @return
   * @throws UserManagementException
   */
  public static Result filter(String nav) throws UserManagementException {
    
    Map<String, String[]> parameters = request().queryString();
    
    Group current = GroupDAO.getInstance(Application.dbName).findOne(session("group_id"));
    
    if ("group".equals(nav)) {

      Set<Group> filter = new HashSet<Group>();
      
      if (current.getBoolean("system")) {
        
        BasicDBObject query = new BasicDBObject();
        
        if (parameters.containsKey("name")) {
          String name = parameters.get("name")[0];
          query.put("$text", new BasicDBObject("$search", name));
        }
        if (parameters.containsKey("level")) {
          int level = Integer.parseInt(parameters.get("level")[0]);
          query.put("level", level);
        }
        
        filter.addAll(GroupDAO.getInstance(Application.dbName).find(query));
        
        ObjectNode json = Json.newObject();
        ArrayNode array = json.putArray("groups");
        for (Group g : filter) {
          array.add(g.getId());
        }
        
        return ok(json);
      } else {
        List<Group> all = listGroupVisible();
        BasicDBObject query = new BasicDBObject();
        
        if (parameters.containsKey("name")) {
          String name = parameters.get("name")[0];
          query.put("$text", new BasicDBObject("$search", name));
        }
        if (parameters.containsKey("level")) {
          int level = Integer.parseInt(parameters.get("level")[0]);
          query.put("level", level);
        }
        
        ObjectNode json = Json.newObject();
        ArrayNode array = json.putArray("groups");
        
        if (query.isEmpty()) {
          for (Group g : all) {
            array.add(g.getId());
          }
          return ok(json);
        }
        
        filter.addAll(GroupDAO.getInstance(Application.dbName).find(query));
        for (Group g : all) {
          if (filter.contains(g)) array.add(g.getId());
        }
        
        return ok(json);
      }
    } else if ("user".equals(nav)) {
      
      Set<User> filter = new HashSet<User>();
      BasicDBObject query = new BasicDBObject();
      if (parameters.containsKey("email")) {
        String name = parameters.get("email")[0];
        query.put("$text", new BasicDBObject("$search", name));
      }
      
      filter.addAll(UserDAO.getInstance(Application.dbName).find(query));
      
      ObjectNode json = Json.newObject();
      ArrayNode array = json.putArray("users");
      
      if (current.getBoolean("system")) {
        for (User u : filter) {
          array.add(u.getId());
        }
      } else {
        for (User u : current.getUsers()) {
          if (filter.contains(u)) array.add(u.getId());
        }
      }
      return ok(json);
    } else if ("role".equals(nav)) {
      Set<Role> filter = new HashSet<Role>();
      ObjectNode json = Json.newObject();
      ArrayNode array = json.putArray("roles");

      BasicDBObject query = new BasicDBObject();
      if (parameters.containsKey("name")) {
        String name = parameters.get("name")[0];
        query.put("$text", new BasicDBObject("$search", name));
      } else {
        for (Role r : current.getRoles()) {
          array.add(r.getId());
        }
      }
      filter.addAll(RoleDAO.getInstance(Application.dbName).find(query));
      for (Role r : current.getRoles()) {
        if (filter.contains(r)) array.add(r.getId());
      }
      return ok(json);
    } else if ("feature".equals(nav)) {
      Set<Feature> filter = new HashSet<Feature>();
      ObjectNode json = Json.newObject();
      ArrayNode array = json.putArray("features");
      
      BasicDBObject query = new BasicDBObject();
      if (parameters.containsKey("name")) {
        String name = parameters.get("name")[0];
        query.put("$text", new BasicDBObject("$search", name));
      } else {
        for (Feature f : current.getFeatures()) {
          array.add(f.getId());
        }
      }
      filter.addAll(FeatureDAO.getInstance(Application.dbName).find(query));
      for (Feature f : current.getFeatures()) {
        if (filter.contains(f)) array.add(f.getId());
      }
      return ok(json);
      
    }
    return status(404);
  }
  
  public static boolean isSystem(User user) throws UserManagementException {
    Group systemGroup = GroupDAO.getInstance(Application.dbName).find(new BasicDBObject("system", true)).iterator().next();
    return systemGroup.getUsers().contains(user);
  }
  
  public static boolean isRightAdministration(Group group_) throws UserManagementException {
    Collection<Group> adGroup = Organization.getAdministrationGroup();
    Set<Group> childrenGroup = new HashSet<Group>();
    for (Group ag : adGroup) {
      childrenGroup.addAll(ag.getAllChildren());
    }
    
    if (! (adGroup.contains(group_) || childrenGroup.contains(group_))) return false;
    
    return true;
  }
}
