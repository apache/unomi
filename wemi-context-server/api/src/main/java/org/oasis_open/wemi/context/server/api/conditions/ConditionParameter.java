package org.oasis_open.wemi.context.server.api.conditions;

/**
 * Represents a condition parameter, that will be used in the segment building UI to either select parameters from a
 * choicelist or to enter a specific value.
 */
public class ConditionParameter {

    String parameterType;
    String parameterName;
    String choiceListInitializerClass;

    public ConditionParameter() {
    }

    public ConditionParameter(String parameterType, String parameterName, String choiceListInitializerClass) {
        this.parameterType = parameterType;
        this.parameterName = parameterName;
        this.choiceListInitializerClass = choiceListInitializerClass;
    }
}
