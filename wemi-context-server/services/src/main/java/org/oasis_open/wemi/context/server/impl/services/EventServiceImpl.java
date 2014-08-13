package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 10.06.14.
 */
public class EventServiceImpl implements EventService {

    private PersistenceService persistenceService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public Event load(String eventId) {
        return persistenceService.load(eventId, Event.class);
    }

    public boolean save(Event event) {
        persistenceService.save(event);
        return false;
    }

    public List<String> getEventProperties() {
        Map<String,Map<String,String>> mappings = persistenceService.getMapping(Event.ITEM_TYPE);
        return new ArrayList<String>(mappings.keySet());
    }

}
