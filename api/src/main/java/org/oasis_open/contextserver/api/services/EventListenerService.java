package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.Event;

/**
 * @todo maybe we should add a canHandle method to avoid calling listeners that don't care about all events and
 * optimize performance
 */
public interface EventListenerService {

    boolean canHandle(Event event);

    boolean onEvent(Event event);

}
