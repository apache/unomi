package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.conditions.ParameterValue;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;

/**
 * Created by toto on 26/06/14.
 */
public class SetPropertyConsequence extends Consequence {
    public SetPropertyConsequence() {
    }

    @Override
    public boolean apply(User user) {
        user.setProperty(
                (String) this.consequencesParameterValues.get("propertyName").getParameterValue(),
                (String) this.consequencesParameterValues.get("propertyValue").getParameterValue());
        return true;
    }
}
