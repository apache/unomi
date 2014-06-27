package org.oasis_open.wemi.context.server.api.conditions;

import java.util.List;

/**
 * Created by loom on 25.06.14.
 */
public class ParameterValue {

    private String parameterId;
    private List<Object> parameterValues;

    public ParameterValue() {
    }

    public ParameterValue(String parameterId, List<Object> parameterValues) {
        this.parameterId = parameterId;
        this.parameterValues = parameterValues;
    }

    public String getParameterId() {
        return parameterId;
    }

    public Object getParameterValue() {
        return parameterValues.get(0);
    }

    public List<Object> getParameterValues() {
        return parameterValues;
    }
}
