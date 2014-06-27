package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;

/**
 * Created by toto on 26/06/14.
 */
public class SetPropertyConsequence extends AbstractConsequenceExecutor {
    public SetPropertyConsequence() {
    }

    @Override
    public String getConsequenceId() {
        return "setPropertyConsequence";
    }

    @Override
    public boolean execute(Consequence consequence, User user) {
        user.setProperty(
                (String) consequence.getConsequencesParameterValues().get("propertyName").getParameterValue(),
                (String) consequence.getConsequencesParameterValues().get("propertyValue").getParameterValue());
        return true;
    }

}
