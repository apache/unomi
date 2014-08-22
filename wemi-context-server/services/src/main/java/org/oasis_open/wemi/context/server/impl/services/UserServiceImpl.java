package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.oasis_open.wemi.context.server.persistence.spi.MapperHelper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 24.04.14.
 */
public class UserServiceImpl implements UserService {

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public UserServiceImpl() {
        System.out.println("Initializing user service...");
    }

    public List<User> findUsersByPropertyValue(String propertyName, String propertyValue) {
        return new ArrayList<User>();
    }

    public User load(String userId) {
        return persistenceService.load(userId, User.class);
    }

    public boolean save(User user) {
        persistenceService.save(user);
        return false;
    }

    public List<String> getUserProperties() {
        Map<String,Map<String,String>> mappings = persistenceService.getMapping(User.ITEM_TYPE);
        return new ArrayList<String>(mappings.keySet());
    }

    public Session loadSession(String sessionId) {
        return persistenceService.load(sessionId, Session.class);
    }

    public boolean saveSession(Session session) {
        persistenceService.save(session);
        return false;
    }

    @Override
    public boolean matchCondition(String conditionString, User user, Session session) {
        try {
            Condition condition = MapperHelper.getObjectMapper().readValue(conditionString, Condition.class);
            ParserHelper.resolveConditionType(definitionsService, condition);
            if (condition.getConditionTypeId().equals("userEventCondition")) {
                final Map<String, Object> parameters = condition.getParameterValues();
                parameters.put("target", session);
                List<Event> matchingEvents = persistenceService.query(condition, "timeStamp", Event.class);

                String occursIn = (String) condition.getParameterValues().get("eventOccurIn");
                if (occursIn != null && occursIn.equals("last")) {
                    if (matchingEvents.size() == 0) {
                        return false;
                    }
                    final Event lastEvent = matchingEvents.get(matchingEvents.size() - 1);
                    String eventType = lastEvent.getEventType();
                    List<Event> events = persistenceService.query("sessionId", session.getItemId(), "timeStamp", Event.class);
                    Collections.reverse(events);
                    for (Event event : events) {
                        if (event.getEventType().equals(eventType)) {
                            return event.getItemId().equals(lastEvent.getItemId());
                        }
                    }
                    return false;
                }

                Integer minimumEventCount = !parameters.containsKey("minimumEventCount") || "".equals(parameters.get("minimumEventCount")) ? 0 : Integer.parseInt((String) parameters.get("minimumEventCount"));
                Integer maximumEventCount = !parameters.containsKey("maximumEventCount") || "".equals(parameters.get("maximumEventCount")) ? Integer.MAX_VALUE : Integer.parseInt((String) parameters.get("maximumEventCount"));

                return matchingEvents.size() >= minimumEventCount && matchingEvents.size() <= maximumEventCount;
            } else if (condition.getConditionType().getTagIDs().contains("userCondition")) {
                return persistenceService.testMatch(condition, user);
            } else if (condition.getConditionType().getTagIDs().contains("sessionCondition")) {
                return persistenceService.testMatch(condition, session);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
