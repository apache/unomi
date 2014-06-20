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

    @Inject
    @OsgiService
    private UserService userService;

    public boolean canHandle(Event event) {
        return "login".equals(event.getEventType());
    }

    public boolean onEvent(Event event) {
        User user = event.getUser();
        if (user == null && event.getVisitorID() != null) {
            user = userService.load(event.getVisitorID());
            if (user != null) {
                event.setUser(user);
            }
        }
        if (user == null) {
            return false;
        }
        for (String eventPropertyName : event.getProperties().stringPropertyNames()) {
            user.setProperty(eventPropertyName, event.getProperty(eventPropertyName));
        }
        userService.save(user);
        return true;
    }
}
