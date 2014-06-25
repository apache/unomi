package org.oasis_open.wemi.context.server.api.conditions;

import java.util.List;

/**
 * Represents a node in the segment definition expression tree
 */
public abstract class ConditionNode {
    String name;
    String description;

    public ConditionNode(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public abstract List<ConditionParameter> getParameters();

    public abstract Object eval(Object context, List<ConditionParameterValue> conditionParameterValues);

}
