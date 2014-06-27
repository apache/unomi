package org.oasis_open.wemi.context.server.api.consequences;

import org.oasis_open.wemi.context.server.api.conditions.ParameterValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by toto on 26/06/14.
 */
public class Consequence {
    protected ConsequenceType consequenceType;

    protected Map<String,ParameterValue> consequencesParameterValues = new HashMap<String, ParameterValue>();

    public ConsequenceType getConsequenceType() {
        return consequenceType;
    }

    public void setConsequenceType(ConsequenceType consequenceType) {
        this.consequenceType = consequenceType;
    }

    public Map<String, ParameterValue> getConsequencesParameterValues() {
        return consequencesParameterValues;
    }

    public void setConsequencesParameterValues(Map<String, ParameterValue> consequencesParameterValues) {
        this.consequencesParameterValues = consequencesParameterValues;
    }

}
