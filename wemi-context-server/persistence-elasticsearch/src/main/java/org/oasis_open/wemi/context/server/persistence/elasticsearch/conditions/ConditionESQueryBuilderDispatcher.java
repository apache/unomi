package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.*;

/**
 * Created by loom on 26.06.14.
 */
public class ConditionESQueryBuilderDispatcher {

    private Map<String, AbstractESQueryBuilder> visitors = new HashMap<String, AbstractESQueryBuilder>();

    public ConditionESQueryBuilderDispatcher() {
        addVisitor(new UserPropertyConditionESQueryBuilder());
        addVisitor(new AndConditionESQueryBuilder());
        addVisitor(new HoverEventConditionESQueryBuilder());
        addVisitor(new PageViewEventConditionESQueryBuilder());
        addVisitor(new MatchAllConditionESQueryBuilder());
    }

    public String getQuery(Condition condition) {
        return "{\"query\": " + QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), buildFilter(condition)).toString() + "}";
    }

    public void addVisitor(AbstractESQueryBuilder visitor) {
        visitors.put(visitor.getConditionId(), visitor);
    }

    public FilterBuilder buildFilter(Condition condition) {
        return visitors.get(condition.getConditionType().getId()).buildFilter(condition, this);
    }


}
