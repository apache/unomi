package org.oasis_open.wemi.context.server.api;

import org.oasis_open.wemi.context.server.api.conditions.ConditionNode;
/**
 * Created by loom on 24.04.14.
 */
public class SegmentDefinition {

    String expression;
    ConditionNode rootConditionNode;

    public SegmentDefinition() {
    }

    public SegmentDefinition(String expression) {
        this.expression = expression;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
