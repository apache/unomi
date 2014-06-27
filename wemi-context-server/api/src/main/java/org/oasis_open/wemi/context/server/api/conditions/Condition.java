package org.oasis_open.wemi.context.server.api.conditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in the segment definition expression tree
 */
public class Condition {
    ConditionType conditionType;
    List<ConditionParameterValue> conditionParameterValues = new ArrayList<ConditionParameterValue>();

    public Condition() {
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public List<ConditionParameterValue> getConditionParameterValues() {
        return conditionParameterValues;
    }

    public void setConditionParameterValues(List<ConditionParameterValue> conditionParameterValues) {
        this.conditionParameterValues = conditionParameterValues;
    }

    public void accept(ConditionVisitor visitor) {
        visitor.visit(this);
    }

}
