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
 * Created by loom on 08.08.14.
 */
@ApplicationScoped
@OsgiServiceProvider
@Properties({
    @Property(name = "consequenceExecutorId", value = "allEventToUserProperties")
})
public class AllEventToUserPropertiesConsequence implements ConsequenceExecutor {
    public boolean execute(Consequence consequence, User user, Object context) {
        boolean changed = false;
        Event event = (Event) context;
        for (String eventPropertyName : event.getProperties().stringPropertyNames()) {
            if (user.getProperty(eventPropertyName) == null || !user.getProperty(eventPropertyName).equals(event.getProperty(eventPropertyName))) {
                user.setProperty(eventPropertyName, event.getProperty(eventPropertyName));
                changed = true;
            }
        }
        return changed;
    }
}
