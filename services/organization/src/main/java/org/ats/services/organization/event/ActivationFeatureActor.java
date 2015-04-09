/**
 * 
 */
package org.ats.services.organization.event;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.ats.common.MapBuilder;
import org.ats.common.PageList;
import org.ats.services.data.MongoDBService;
import org.ats.services.event.Event;
import org.ats.services.organization.ActivationService;
import org.ats.services.organization.FeatureService;
import org.ats.services.organization.TenantService;
import org.ats.services.organization.entity.Feature;
import org.ats.services.organization.entity.Tenant;
import org.ats.services.organization.entity.fatory.ReferenceFactory;
import org.ats.services.organization.entity.reference.FeatureReference;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import akka.actor.UntypedActor;

/**
 * @author NamBV2
 *
 * Apr 3, 2015
 */
public class ActivationFeatureActor extends UntypedActor{

  @Inject
  private Logger logger;
  
  @Inject
  private FeatureService featureService;
  
  @Inject
  private MongoDBService mongo;
  
  @Inject
  private TenantService tenantService;
  
  @Inject
  private ReferenceFactory<FeatureReference> refFactory;
  
  @Inject
  private ActivationService activationService;
  
  @Override
  public void onReceive(Object message) throws Exception {

    if(message instanceof Event) {
      Event event = (Event) message;
      if("inactive-feature".equals(event.getName())) {
        Feature feature = (Feature) event.getSource();
        FeatureReference ref = refFactory.create(feature.getId());
        processInActive(ref);
      } else if("inactive-feature-ref".equals(event.getName())) {
        FeatureReference ref = (FeatureReference) event.getSource();
        processInActive(ref);
      } else if("active-feature".equals(event.getName())){
        Feature feature = (Feature) event.getSource();
        FeatureReference ref = refFactory.create(feature.getId());
        processActive(ref);
      } else if("active-feature-ref".equals(event.getName())) {
        FeatureReference ref = (FeatureReference) event.getSource();
        processActive(ref);
      } else {
        unhandled(message);
      }
    }
  }

  private void processActive(FeatureReference ref) throws InterruptedException {
    DBCollection tenantCol = mongo.getDatabase().getCollection("inactived-tenant");
    DBCollection featureCol = mongo.getDatabase().getCollection("inactived-feature");
    DBObject dbObj = featureCol.findOne(new BasicDBObject("_id",ref.getId()));
    Feature feature = featureService.transform(dbObj);
    
    featureService.create(feature);
    featureCol.remove(feature);
    
    PageList<DBObject> listTenantObj = activationService.findTenantIntoInactiveFeature("features", ref);
    listTenantObj.setSortable(new MapBuilder<String,Boolean>("created_date", true).build());
    List<Tenant> listTenant = new ArrayList<Tenant>();
    while(listTenantObj.hasNext()) {
      for(DBObject obj:listTenantObj.next()) {
        listTenant.add(tenantService.transform(obj));
      }
    }

    for(Tenant t:listTenant) {
      Tenant tenant = tenantService.get(t.getId());
      tenant.addFeature(refFactory.create(ref.getId()));
      tenantService.update(tenant);
      if(tenantService.get(t.getId()).getFeatures().size() == t.getFeatures().size()) {
        tenantCol.remove(new BasicDBObject("_id",t.getId()));
      }
    }
    
    if (!"deadLetters".equals(getSender().path().name())) {
      getSender().tell(ref, getSelf());
    }
    
  }

  private void processInActive(FeatureReference ref) throws InterruptedException {
    logger.info("Process event source: " + ref);
    
    featureService.delete(ref.getId());
    
    while (tenantService.findIn("features", ref).count() != 0) {
    }
    
    if (!"deadLetters".equals(getSender().path().name())) {
      getSender().tell(ref, getSelf());
    }
  }

}
