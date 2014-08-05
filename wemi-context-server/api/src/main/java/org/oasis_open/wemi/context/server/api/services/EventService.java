package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Session;

/**
 * Created by loom on 24.04.14.
 */
public interface EventService {

    Event load(String eventId);

    boolean save(Event event);

}
