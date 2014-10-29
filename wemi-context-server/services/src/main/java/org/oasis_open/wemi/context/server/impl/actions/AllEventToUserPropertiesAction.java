package org.oasis_open.wemi.context.server.impl.actions;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;
import org.oasis_open.wemi.context.server.api.services.UserService;

import java.util.Map;

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
        for (Map.Entry<String, Object> entry : event.getTarget().getProperties().entrySet()) {
            if (event.getUser().getProperty(entry.getKey()) == null || !event.getUser().getProperty(entry.getKey()).equals(event.getProperty(entry.getKey()))) {
                String propertyMapping = userService.getPropertyTypeMapping(entry.getKey());
                if (propertyMapping != null) {
                    event.getUser().setProperty(propertyMapping, entry.getValue());
                } else {
                    event.getUser().setProperty(entry.getKey(), entry.getValue());
                }
                changed = true;
            }
        }
        return changed;
    }
}
