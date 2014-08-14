package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 10.06.14.
 */
public class EventServiceImpl implements EventService {

    private List<EventListenerService> eventListeners = new ArrayList<EventListenerService>();

    private PersistenceService persistenceService;

    private UserService userService;

    private BundleContext bundleContext;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
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
                Event userUpdated = new Event("userUpdated", event.getSession(), event.getUser());
                userUpdated.getAttributes().putAll(event.getAttributes());
                save(userUpdated);

                userService.save(event.getUser());
                if (event.getSession() != null) {
                    userService.saveSession(event.getSession());
                }
            }
        }
        return changed;
    }

    public List<String> getEventProperties() {
        Map<String,Map<String,String>> mappings = persistenceService.getMapping(Event.ITEM_TYPE);
        return new ArrayList<String>(mappings.keySet());
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
