package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.ops4j.pax.cdi.api.OsgiService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 10.06.14.
 */
@ApplicationScoped
@Default
@OsgiServiceProvider
public class EventServiceImpl implements EventService {

    @Inject
    @OsgiService
    private PersistenceService persistenceService;

    public Event load(String eventId) {
        return persistenceService.load(eventId, Event.EVENT_ITEM_TYPE, Event.class);
    }

    public boolean save(Event event) {
        persistenceService.save(event);
        return false;
    }

    public List<String> getEventProperties() {
        Map<String,Map<String,String>> mappings = persistenceService.getMapping(Event.EVENT_ITEM_TYPE);
        return new ArrayList<String>(mappings.keySet());
    }

}
