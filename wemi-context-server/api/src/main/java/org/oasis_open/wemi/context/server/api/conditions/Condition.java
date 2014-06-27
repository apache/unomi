package org.oasis_open.wemi.context.server.api.conditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a node in the segment definition expression tree
 */
public class Condition {
    ConditionType conditionType;
    Map<String, ParameterValue> conditionParameterValues = new HashMap<String, ParameterValue>();

    public Condition() {
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public Map<String, ParameterValue> getConditionParameterValues() {
        return conditionParameterValues;
    }

    public void setConditionParameterValues(Map<String, ParameterValue> conditionParameterValues) {
        this.conditionParameterValues = conditionParameterValues;
    }

    public void accept(ConditionVisitor visitor) {
        for (Parameter parameter : conditionType.getConditionParameters()) {
            if ("Condition".equals(parameter.getType())) {
                for (Object o : conditionParameterValues.get(parameter.getId()).getParameterValues()) {
                    visitor.visit((Condition)o);
                }
            }
        }
        visitor.visit(this);
    }

}
