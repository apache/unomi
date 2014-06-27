package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.Stack;

/**
* Created by toto on 27/06/14.
*/
class MatchAllConditionESQueryGeneratorVisitor extends AbstractESQueryGeneratorVisitor  {

    @Override
    public String getConditionId() {
        return "matchAll";
    }

    @Override
    public void visit(Condition condition, Stack<FilterBuilder> stack) {
        stack.push(FilterBuilders.matchAllFilter());
    }
}
