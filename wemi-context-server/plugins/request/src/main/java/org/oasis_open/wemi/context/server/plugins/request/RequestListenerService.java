package org.oasis_open.wemi.context.server.plugins.request;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.ops4j.pax.cdi.api.OsgiService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

/**
 * Created by loom on 20.06.14.
 */
@Singleton
@OsgiServiceProvider
public class RequestListenerService implements EventListenerService {

    @Inject
    @OsgiService
    private UserService userService;

    public RequestListenerService() {
        System.out.println("Creating request listener...");
    }

    public boolean canHandle(Event event) {
        return "contextloaded".equals(event.getEventType());
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
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get("http_request");
        if (httpServletRequest == null) {
            return false;
        }
        // we can now copy interesting request attributes to the user profile
        // @todo implement the copying
        return true;
    }
}
