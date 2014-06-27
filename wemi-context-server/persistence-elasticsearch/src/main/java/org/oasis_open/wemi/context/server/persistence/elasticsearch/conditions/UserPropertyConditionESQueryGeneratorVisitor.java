package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ConditionVisitor;

import java.util.Stack;

/**
* Created by toto on 27/06/14.
*/
class UserPropertyConditionESQueryGeneratorVisitor extends AbstractESQueryGeneratorVisitor  {

    @Override
    public String getConditionId() {
        return "userPropertyCondition";
    }

    @Override
    public void visit(Condition condition, Stack<FilterBuilder> stack) {
        String op = (String) condition.getConditionParameterValues().get("comparisonOperator").getParameterValue();
        String name = (String) condition.getConditionParameterValues().get("propertyName").getParameterValue();
        String value = (String) condition.getConditionParameterValues().get("propertyValue").getParameterValue();
        if (op.equals("equals")) {
            stack.push(FilterBuilders.termFilter(name, value));
        } else if (op.equals("greaterThan")) {
            stack.push(FilterBuilders.rangeFilter(name).gt(Integer.parseInt(value)));
        } else if (op.equals("lessThan")) {
            stack.push(FilterBuilders.rangeFilter(name).lt(Integer.parseInt(value)));
        }
    }
}
