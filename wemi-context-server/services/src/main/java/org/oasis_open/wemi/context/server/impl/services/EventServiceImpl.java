package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 10.06.14.
 */
public class EventServiceImpl implements EventService {

    private List<EventListenerService> eventListeners;

    private PersistenceService persistenceService;

    private UserService userService;

    public void setEventListeners(List<EventListenerService> eventListeners) {
        this.eventListeners = eventListeners;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public Event load(String eventId) {
        return persistenceService.load(eventId, Event.class);
    }

    public boolean save(Event event) {
        persistenceService.save(event);

        boolean changed = false;
        if (event.getUser() != null) {
            for (EventListenerService eventListenerService : eventListeners) {
                if (eventListenerService.canHandle(event)) {
                    changed |= eventListenerService.onEvent(event);
                }
            }

            if (changed) {
                userService.save(event.getUser());
                userService.saveSession(event.getSession());
            }
        }
        return changed;
    }

    public List<String> getEventProperties() {
        Map<String,Map<String,String>> mappings = persistenceService.getMapping(Event.ITEM_TYPE);
        return new ArrayList<String>(mappings.keySet());
    }

}
