package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Event;

/**
 * Created by loom on 10.06.14.
 * @todo maybe we should add a canHandle method to avoid calling listeners that don't care about all events and
 * optimize performance
 */
public interface EventListenerService {

    boolean canHandle(Event event);

    boolean onEvent(Event event);

}
