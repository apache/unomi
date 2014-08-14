package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;

/**
 * A consequence to copy an event property to a user property
 */
public class EventToUserPropertyConsequence implements ConsequenceExecutor {

    public boolean execute(Consequence consequence, Event event) {
        boolean changed = false;
        String eventPropertyName = (String) consequence.getParameterValues().get("eventPropertyName");
        String userPropertyName = (String) consequence.getParameterValues().get("userPropertyName");
        if (event.getUser().getProperty(userPropertyName) == null || !event.getUser().getProperty(userPropertyName).equals(event.getProperty(eventPropertyName))) {
            event.getUser().setProperty(userPropertyName, event.getProperty(eventPropertyName));
            changed = true;
        }
        return changed;
    }
}
