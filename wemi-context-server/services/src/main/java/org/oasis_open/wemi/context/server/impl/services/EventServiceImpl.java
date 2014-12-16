package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.oasis_open.wemi.context.server.persistence.spi.Aggregate;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.*;

/**
 * Created by loom on 10.06.14.
 */
public class EventServiceImpl implements EventService {

    private List<EventListenerService> eventListeners = new ArrayList<EventListenerService>();

    private PersistenceService persistenceService;

    private UserService userService;

    private DefinitionsService definitionsService;

    private BundleContext bundleContext;

    private Set<String> predefinedEventTypeIds = new LinkedHashSet<String>();

    public void setPredefinedEventTypeIds(Set<String> predefinedEventTypeIds) {
        this.predefinedEventTypeIds = predefinedEventTypeIds;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
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

        User user = event.getUser();
        final Session session = event.getSession();

        if (user != null) {
            Map<String,Object> previousProperties = new HashMap<String, Object>(user.getProperties());
            Set<String> previousSegments = new HashSet<String>(user.getSegments());

            for (EventListenerService eventListenerService : eventListeners) {
                if (eventListenerService.canHandle(event)) {
                    changed |= eventListenerService.onEvent(event);
                }
            }

            if (session.getUser() != null && !session.getUser().getId().equals(user.getId())) {
                // this can happen when users are merged for example.
                user = session.getUser();
            }

            if (changed && (!user.getProperties().equals(previousProperties) || !user.getSegments().equals(previousSegments))) {
                Event userUpdated = new Event("userUpdated", session, user, event.getSource(), new EventTarget(user.getId(), User.ITEM_TYPE), event.getTimeStamp());
                userUpdated.setPersistent(false);
                userUpdated.getAttributes().putAll(event.getAttributes());
                send(userUpdated);

                userService.save(user);
            }

            if (session != null) {
                session.setLastEventDate(event.getTimeStamp());
                userService.saveSession(session);
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

        Condition userIdCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        if (session) {
            userIdCondition.getParameterValues().put("propertyName", "sessionId");
            userIdCondition.getParameterValues().put("propertyValue", event.getSessionId());
        } else {
            userIdCondition.getParameterValues().put("propertyName", "userId");
            userIdCondition.getParameterValues().put("propertyValue", event.getUserId());
        }
        userIdCondition.getParameterValues().put("comparisonOperator", "equals");
        conditions.add(userIdCondition);

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
