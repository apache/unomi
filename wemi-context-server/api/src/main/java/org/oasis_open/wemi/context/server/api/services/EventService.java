package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.List;
import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public interface EventService {

    Event load(String eventId);

    boolean save(Event event);

    public List<String> getEventProperties();

    public Set<String> getEventTypeIds();

    public List<Event> searchEvents(Condition condition);

    public boolean hasEventAlreadyBeenRaised(Event event, boolean session);
}
