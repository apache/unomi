package org.oasis_open.wemi.context.server.api.conditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in the segment definition expression tree
 */
public class ConditionNode {
    ConditionTypeNode conditionTypeNode;
    List<ConditionParameterValue> conditionParameterValues = new ArrayList<ConditionParameterValue>();

    public ConditionNode() {
    }

    public ConditionTypeNode getConditionTypeNode() {
        return conditionTypeNode;
    }

    public void setConditionTypeNode(ConditionTypeNode conditionTypeNode) {
        this.conditionTypeNode = conditionTypeNode;
    }

    public List<ConditionParameterValue> getConditionParameterValues() {
        return conditionParameterValues;
    }

    public void setConditionParameterValues(List<ConditionParameterValue> conditionParameterValues) {
        this.conditionParameterValues = conditionParameterValues;
    }

    public void accept(ConditionNodeVisitor visitor) {
        visitor.visit(this);
    }

}
