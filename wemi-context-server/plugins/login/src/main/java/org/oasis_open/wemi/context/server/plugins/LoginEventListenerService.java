package org.oasis_open.wemi.context.server.plugins;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.ops4j.pax.cdi.api.OsgiService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Created by loom on 20.06.14.
 */
@Singleton
@OsgiServiceProvider
public class LoginEventListenerService implements EventListenerService {

    public LoginEventListenerService() {
        System.out.println("Creating login event listener...");
    }

    public boolean canHandle(Event event) {
        return "login".equals(event.getEventType());
    }

    public boolean onEvent(Event event) {
        boolean changed = false;
        final User user = event.getUser();
        for (String eventPropertyName : event.getProperties().stringPropertyNames()) {
            if (user.getProperty(eventPropertyName) == null || !user.getProperty(eventPropertyName).equals(event.getProperty(eventPropertyName))) {
                user.setProperty(eventPropertyName, event.getProperty(eventPropertyName));
                changed = true;
            }
        }
        return changed;
    }
}
