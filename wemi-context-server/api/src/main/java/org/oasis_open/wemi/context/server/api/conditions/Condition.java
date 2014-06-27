package org.oasis_open.wemi.context.server.api.conditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in the segment definition expression tree
 */
public class Condition {
    ConditionType conditionType;
    List<ParameterValue> conditionParameterValues = new ArrayList<ParameterValue>();

    public Condition() {
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public List<ParameterValue> getConditionParameterValues() {
        return conditionParameterValues;
    }

    public void setConditionParameterValues(List<ParameterValue> conditionParameterValues) {
        this.conditionParameterValues = conditionParameterValues;
    }

    public void accept(ConditionVisitor visitor) {
        visitor.visit(this);
    }

}
