package org.oasis_open.wemi.context.server.api.consequences;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.conditions.ParameterValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by toto on 26/06/14.
 */
public abstract class Consequence {
    protected ConsequenceType type;

    protected Map<String,ParameterValue> consequencesParameterValues = new HashMap<String, ParameterValue>();

    public void setType(ConsequenceType type) {
        this.type = type;
    }

    public void setConsequencesParameterValues(Map<String, ParameterValue> consequencesParameterValues) {
        this.consequencesParameterValues = consequencesParameterValues;
    }

    public abstract boolean apply(User user);
}
