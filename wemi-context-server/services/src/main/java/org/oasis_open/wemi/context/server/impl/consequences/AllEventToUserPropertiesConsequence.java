package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;

/**
 * Created by loom on 08.08.14.
 */
public class AllEventToUserPropertiesConsequence implements ConsequenceExecutor {
    public boolean execute(Consequence consequence, Event event) {
        boolean changed = false;
        for (String eventPropertyName : event.getProperties().stringPropertyNames()) {
            if (event.getUser().getProperty(eventPropertyName) == null || !event.getUser().getProperty(eventPropertyName).equals(event.getProperty(eventPropertyName))) {
                event.getUser().setProperty(eventPropertyName, event.getProperty(eventPropertyName));
                changed = true;
            }
        }
        return changed;
    }
}
