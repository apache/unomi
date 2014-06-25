package org.oasis_open.wemi.context.server.api.conditions;

/**
 * Created by loom on 25.06.14.
 */
public class ConditionParameterValue {

    String parameterType;
    String parameterName;
    String parameterValue;

    public ConditionParameterValue() {
    }

    public ConditionParameterValue(String parameterType, String parameterName, String parameterValue) {
        this.parameterType = parameterType;
        this.parameterName = parameterName;
        this.parameterValue = parameterValue;
    }
}
