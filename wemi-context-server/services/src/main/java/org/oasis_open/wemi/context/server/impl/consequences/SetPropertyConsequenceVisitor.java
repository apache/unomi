package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;

/**
 * Created by toto on 26/06/14.
 */
public class SetPropertyConsequenceVisitor extends AbstractConsequenceExecutorVisitor {
    public SetPropertyConsequenceVisitor() {
    }

    @Override
    public String getConsequenceId() {
        return "setPropertyConsequence";
    }

    @Override
    public boolean visit(Consequence consequence, User user) {
        user.setProperty(
                (String) consequence.getConsequencesParameterValues().get("propertyName").getParameterValue(),
                (String) consequence.getConsequencesParameterValues().get("propertyValue").getParameterValue());
        return true;
    }

}
