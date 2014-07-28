package org.oasis_open.wemi.context.server.api.consequences;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by toto on 26/06/14.
 */
public class Consequence {
    protected ConsequenceType consequenceType;

    protected Map<String,Object> consequencesParameterValues = new HashMap<String, Object>();

    public ConsequenceType getConsequenceType() {
        return consequenceType;
    }

    public void setConsequenceType(ConsequenceType consequenceType) {
        this.consequenceType = consequenceType;
    }

    public Map<String, Object> getConsequencesParameterValues() {
        return consequencesParameterValues;
    }

    public void setConsequencesParameterValues(Map<String, Object> consequencesParameterValues) {
        this.consequencesParameterValues = consequencesParameterValues;
    }

}
