package org.oasis_open.wemi.context.server.impl.actions;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;

/**
 * A action to copy an event property to a user property
 */
public class EventToUserPropertyAction implements ActionExecutor {

    public boolean execute(Action action, Event event) {
        boolean changed = false;
        String eventPropertyName = (String) action.getParameterValues().get("eventPropertyName");
        String userPropertyName = (String) action.getParameterValues().get("userPropertyName");
        if (event.getUser().getProperty(userPropertyName) == null || !event.getUser().getProperty(userPropertyName).equals(event.getProperty(eventPropertyName))) {
            event.getUser().setProperty(userPropertyName, event.getProperty(eventPropertyName));
            changed = true;
        }
        return changed;
    }
}
