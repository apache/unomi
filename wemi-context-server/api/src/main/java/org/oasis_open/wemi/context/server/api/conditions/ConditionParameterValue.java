package org.oasis_open.wemi.context.server.api.conditions;

import java.util.List;

/**
 * Created by loom on 25.06.14.
 */
public class ConditionParameterValue {

    String parameterName;
    List<String> parameterValues;

    public ConditionParameterValue() {
    }

    public ConditionParameterValue(String parameterName, List<String> parameterValues) {
        this.parameterName = parameterName;
        this.parameterValues = parameterValues;
    }
}
