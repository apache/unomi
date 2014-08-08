package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.ops4j.pax.cdi.api.Properties;
import org.ops4j.pax.cdi.api.Property;

import javax.enterprise.context.ApplicationScoped;

/**
 * A consequence to copy an event property to a user property
 */
@ApplicationScoped
@OsgiServiceProvider
@Properties({
    @Property(name = "consequenceExecutorId", value = "eventToUserProperty")
})
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
