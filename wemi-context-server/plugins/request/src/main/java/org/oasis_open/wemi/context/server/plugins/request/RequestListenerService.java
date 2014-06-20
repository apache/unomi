package org.oasis_open.wemi.context.server.plugins.request;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;

import javax.inject.Singleton;

/**
 * Created by loom on 20.06.14.
 */
@Singleton
@OsgiServiceProvider
public class RequestListenerService implements EventListenerService {

    public boolean canHandle(Event event) {
        return "contextloaded".equals(event.getEventType());
    }

    public boolean onEvent(Event event) {
        return false;
    }
}
