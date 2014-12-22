package org.oasis_open.contextserver.impl.actions;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;

/**
 * A action to copy an event property to a profile property
 */
public class EventToProfilePropertyAction implements ActionExecutor {

    public boolean execute(Action action, Event event) {
        boolean changed = false;
        String eventPropertyName = (String) action.getParameterValues().get("eventPropertyName");
        String profilePropertyName = (String) action.getParameterValues().get("profilePropertyName");
        if (event.getProfile().getProperty(profilePropertyName) == null || !event.getProfile().getProperty(profilePropertyName).equals(event.getProperty(eventPropertyName))) {
            event.getProfile().setProperty(profilePropertyName, event.getProperty(eventPropertyName));
            changed = true;
        }
        return changed;
    }
}
