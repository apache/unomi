package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Event;

import java.util.List;

/**
 * Created by loom on 24.04.14.
 */
public interface EventService {

    Event load(String eventId);

    boolean save(Event event);

    public List<String> getEventProperties();

    public List<String> getEventTypeIds();

}
