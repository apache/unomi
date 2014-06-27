package org.oasis_open.wemi.context.server.api.conditions;

import java.util.List;

/**
 * Created by loom on 25.06.14.
 */
public class ParameterValue {

    private String parameterName;
    private List<Object> parameterValues;

    public ParameterValue() {
    }

    public ParameterValue(String parameterName, List<Object> parameterValues) {
        this.parameterName = parameterName;
        this.parameterValues = parameterValues;
    }

    public String getParameterName() {
        return parameterName;
    }

    public Object getParameterValue() {
        return parameterValues.get(0);
    }

    public List<Object> getParameterValues() {
        return parameterValues;
    }
}
