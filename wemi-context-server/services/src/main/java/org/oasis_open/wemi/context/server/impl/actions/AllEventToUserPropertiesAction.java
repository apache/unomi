package org.oasis_open.wemi.context.server.impl.actions;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;
import org.oasis_open.wemi.context.server.api.services.UserService;

/**
 * Created by loom on 08.08.14.
 */
public class AllEventToUserPropertiesAction implements ActionExecutor {

    private UserService userService;

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public boolean execute(Action action, Event event) {
        boolean changed = false;
        for (String eventPropertyName : event.getProperties().stringPropertyNames()) {
            if (event.getUser().getProperty(eventPropertyName) == null || !event.getUser().getProperty(eventPropertyName).equals(event.getProperty(eventPropertyName))) {
                String propertyMapping = userService.getUserPropertyMapping(eventPropertyName);
                if (propertyMapping != null) {
                    event.getUser().setProperty(propertyMapping, event.getProperty(eventPropertyName));
                } else {
                    event.getUser().setProperty(eventPropertyName, event.getProperty(eventPropertyName));
                }
                changed = true;
            }
        }
        return changed;
    }
}
