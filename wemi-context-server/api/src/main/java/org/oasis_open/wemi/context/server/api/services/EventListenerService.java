package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Event;

/**
 * Created by loom on 10.06.14.
 */
public interface EventListenerService {

    public boolean onEvent(Event event);

}
