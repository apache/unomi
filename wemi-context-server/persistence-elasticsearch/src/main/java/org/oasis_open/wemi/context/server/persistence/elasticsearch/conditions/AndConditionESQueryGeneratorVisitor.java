package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ConditionVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
* Created by toto on 27/06/14.
*/
class AndConditionESQueryGeneratorVisitor extends AbstractESQueryGeneratorVisitor {
    @Override
    public String getConditionId() {
        return "andCondition";
    }

    @Override
    public void visit(Condition condition, Stack<FilterBuilder> stack) {
        List<Object> conditions = condition.getConditionParameterValues().get("subConditions").getParameterValues();

        List<FilterBuilder> l = new ArrayList<FilterBuilder>();
        for (Object o : conditions) {
            l.add(0,stack.pop());
        }
        stack.push(FilterBuilders.andFilter(l.toArray(new FilterBuilder[l.size()])));
    }
}
