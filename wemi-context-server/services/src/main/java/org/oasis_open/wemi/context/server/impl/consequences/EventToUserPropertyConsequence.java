package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;

/**
 * A consequence to copy an event property to a user property
 */
public class EventToUserPropertyConsequence implements ConsequenceExecutor {

    public boolean execute(Consequence consequence, User user, Object context) {
        boolean changed = false;
        Event event = (Event) context;
        String eventPropertyName = (String) consequence.getParameterValues().get("eventPropertyName");
        String userPropertyName = (String) consequence.getParameterValues().get("userPropertyName");
        if (user.getProperty(userPropertyName) == null || !user.getProperty(userPropertyName).equals(event.getProperty(eventPropertyName))) {
            user.setProperty(userPropertyName, event.getProperty(eventPropertyName));
            changed = true;
        }
        return changed;
    }
}
