package org.oasis_open.wemi.context.server.api;

/**
 * Created by loom on 24.04.14.
 */
public class SegmentDefinition {

    String expression;

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
