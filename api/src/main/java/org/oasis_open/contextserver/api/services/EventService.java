package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.EventProperty;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;
import java.util.Set;

/**
 * Event service.
 */
public interface EventService {

    boolean send(Event event);

    /**
     * Returns a list of available event properties.
     * 
     * @return a list of available event properties
     */
    List<EventProperty> getEventProperties();

    Set<String> getEventTypeIds();

    PartialList<Event> searchEvents(Condition condition, int offset, int size);

    boolean hasEventAlreadyBeenRaised(Event event, boolean session);
}
