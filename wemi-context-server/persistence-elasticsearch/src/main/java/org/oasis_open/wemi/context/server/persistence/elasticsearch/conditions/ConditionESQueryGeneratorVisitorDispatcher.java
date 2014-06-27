package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ConditionVisitor;

import java.util.*;

/**
 * Created by loom on 26.06.14.
 */
public class ConditionESQueryGeneratorVisitorDispatcher extends ConditionVisitor {

    private Map<String, AbstractESQueryGeneratorVisitor> visitors = new HashMap<String, AbstractESQueryGeneratorVisitor>();

    private Stack<FilterBuilder> stack = new Stack<FilterBuilder>();

    public ConditionESQueryGeneratorVisitorDispatcher() {
        addVisitor(new UserPropertyConditionESQueryGeneratorVisitor());
        addVisitor(new AndConditionESQueryGeneratorVisitor());
        addVisitor(new HoverEventConditionESQueryGeneratorVisitor());
        addVisitor(new PageViewEventConditionESQueryGeneratorVisitor());
    }

    public String getQuery() {
        return "{\"query\": " + QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), stack.peek()).toString() + "}";
    }

    public void addVisitor(AbstractESQueryGeneratorVisitor visitor) {
        visitors.put(visitor.getConditionId(), visitor);
    }

    @Override
    public void visit(Condition condition) {
        visitors.get(condition.getConditionType().getId()).visit(condition, stack);
    }


}
