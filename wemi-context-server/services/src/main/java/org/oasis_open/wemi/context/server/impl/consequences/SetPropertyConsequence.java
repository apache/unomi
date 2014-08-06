package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.ops4j.pax.cdi.api.Properties;
import org.ops4j.pax.cdi.api.Property;

import javax.enterprise.context.ApplicationScoped;

/**
 * Created by toto on 26/06/14.
 */
@ApplicationScoped
@OsgiServiceProvider
@Properties({
    @Property(name = "consequenceExecutorId", value = "setProperty")
})
public class SetPropertyConsequence implements ConsequenceExecutor {
    public SetPropertyConsequence() {
    }

    public String getConsequenceId() {
        return "setPropertyConsequence";
    }

    public boolean execute(Consequence consequence, User user, Object context) {
        user.setProperty(
                (String) consequence.getParameterValues().get("propertyName"),
                (String) consequence.getParameterValues().get("propertyValue"));
        return true;
    }

}
