package org.oasis_open.contextserver.impl.services;

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.EventListenerService;
import org.oasis_open.contextserver.api.services.EventService;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.oasis_open.contextserver.persistence.spi.Aggregate;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.*;

/**
 * Created by loom on 10.06.14.
 */
public class EventServiceImpl implements EventService {

    private List<EventListenerService> eventListeners = new ArrayList<EventListenerService>();

    private PersistenceService persistenceService;

    private ProfileService profileService;

    private DefinitionsService definitionsService;

    private BundleContext bundleContext;

    private Set<String> predefinedEventTypeIds = new LinkedHashSet<String>();

    public void setPredefinedEventTypeIds(Set<String> predefinedEventTypeIds) {
        this.predefinedEventTypeIds = predefinedEventTypeIds;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public boolean send(Event event) {
        if (event.isPersistent()) {
            persistenceService.save(event);
        }

        boolean changed = false;

        Profile profile = event.getProfile();
        final Session session = event.getSession();

        if (profile != null) {
            Map<String,Object> previousProperties = new HashMap<String, Object>(profile.getProperties());
            Set<String> previousSegments = new HashSet<String>(profile.getSegments());

            for (EventListenerService eventListenerService : eventListeners) {
                if (eventListenerService.canHandle(event)) {
                    changed |= eventListenerService.onEvent(event);
                }
            }

            if (session.getProfile() != null && !session.getProfile().getId().equals(profile.getId())) {
                // this can happen when profiles are merged for example.
                profile = session.getProfile();
            }

            if (changed && (!profile.getProperties().equals(previousProperties) || !profile.getSegments().equals(previousSegments))) {
                Event profileUpdated = new Event("profileUpdated", session, profile, event.getScope(), event.getSource(), new EventTarget(profile.getId(), Profile.ITEM_TYPE), event.getTimeStamp());
                profileUpdated.setPersistent(false);
                profileUpdated.getAttributes().putAll(event.getAttributes());
                send(profileUpdated);

                profileService.save(profile);
            }

            if (session != null) {
                session.setLastEventDate(event.getTimeStamp());
                profileService.saveSession(session);
            }
        }
        return changed;
    }

    public List<String> getEventProperties() {
        Map<String, Map<String, String>> mappings = persistenceService.getMapping(Event.ITEM_TYPE);
        return new ArrayList<String>(mappings.keySet());
    }

    public Set<String> getEventTypeIds() {
        Map<String, Long> dynamicEventTypeIds = persistenceService.aggregateQuery(null, new Aggregate(Aggregate.Type.TERMS, "eventType"), Event.ITEM_TYPE);
        Set<String> eventTypeIds = new LinkedHashSet<String>(predefinedEventTypeIds);
        eventTypeIds.addAll(dynamicEventTypeIds.keySet());
        return eventTypeIds;
    }

    @Override
    public PartialList<Event> searchEvents(Condition condition, int offset, int size) {
        return persistenceService.query(condition, "timeStamp", Event.class, offset, size);
    }

    public boolean hasEventAlreadyBeenRaised(Event event, boolean session) {
        List<Condition> conditions = new ArrayList<Condition>();

        Condition profileIdCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        if (session) {
            profileIdCondition.getParameterValues().put("propertyName", "sessionId");
            profileIdCondition.getParameterValues().put("propertyValue", event.getSessionId());
        } else {
            profileIdCondition.getParameterValues().put("propertyName", "profileId");
            profileIdCondition.getParameterValues().put("propertyValue", event.getProfileId());
        }
        profileIdCondition.getParameterValues().put("comparisonOperator", "equals");
        conditions.add(profileIdCondition);

        Condition condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.getParameterValues().put("propertyName", "eventType");
        condition.getParameterValues().put("propertyValue", event.getEventType());
        condition.getParameterValues().put("comparisonOperator", "equals");
        conditions.add(condition);

        condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.getParameterValues().put("propertyName", "target.id");
        condition.getParameterValues().put("propertyValue", event.getTarget().getId());
        condition.getParameterValues().put("comparisonOperator", "equals");
        conditions.add(condition);

        condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.getParameterValues().put("propertyName", "target.type");
        condition.getParameterValues().put("propertyValue", event.getTarget().getType());
        condition.getParameterValues().put("comparisonOperator", "equals");
        conditions.add(condition);

        Condition andCondition = new Condition(definitionsService.getConditionType("andCondition"));
        andCondition.getParameterValues().put("subConditions", conditions);
        long size = persistenceService.queryCount(andCondition, Event.ITEM_TYPE);
        return size > 0;
    }


    public void bind(ServiceReference<EventListenerService> serviceReference) {
        EventListenerService eventListenerService = bundleContext.getService(serviceReference);
        eventListeners.add(eventListenerService);
    }

    public void unbind(ServiceReference<EventListenerService> serviceReference) {
        EventListenerService eventListenerService = bundleContext.getService(serviceReference);
        eventListeners.remove(eventListenerService);
    }

}
