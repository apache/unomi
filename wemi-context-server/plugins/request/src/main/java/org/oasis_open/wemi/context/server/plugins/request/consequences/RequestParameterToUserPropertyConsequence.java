package org.oasis_open.wemi.context.server.plugins.request.consequences;

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
    @Property(name = "consequenceExecutorId", value = "requestParameterToUserProperty")
})
public class RequestParameterToUserPropertyConsequence implements ConsequenceExecutor {
    public boolean execute(Consequence consequence, User user, Object context) {
        return false;
    }
}
