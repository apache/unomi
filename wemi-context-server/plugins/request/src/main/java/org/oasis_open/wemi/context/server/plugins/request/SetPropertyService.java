package org.oasis_open.wemi.context.server.plugins.request;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;

import javax.inject.Singleton;


@Singleton
@OsgiServiceProvider
public class SetPropertyService implements EventListenerService {
    @Override
    public boolean canHandle(Event event) {
        return event.getEventType().equals("setproperty");
    }

    @Override
    public boolean onEvent(Event event) {
        if (event.getUser().getProperty(event.getProperty("name")) == null || !event.getUser().getProperty(event.getProperty("name")).equals(event.getProperty("value"))) {
            event.getUser().setProperty(event.getProperty("name"), event.getProperty("value"));

            return true;
        }
        return false;
    }
}
