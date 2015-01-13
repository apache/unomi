package org.oasis_open.contextserver.api.query;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by kevan on 12/01/15.
 */
public class Aggregate {
    private String type;
    Map<String, Object> parameters = new HashMap<>();

    public Aggregate() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
}
