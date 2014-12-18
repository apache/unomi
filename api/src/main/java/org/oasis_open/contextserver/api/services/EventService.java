package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;
import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public interface EventService {

    boolean send(Event event);

    List<String> getEventProperties();

    Set<String> getEventTypeIds();

    PartialList<Event> searchEvents(Condition condition, int offset, int size);

    boolean hasEventAlreadyBeenRaised(Event event, boolean session);
}
