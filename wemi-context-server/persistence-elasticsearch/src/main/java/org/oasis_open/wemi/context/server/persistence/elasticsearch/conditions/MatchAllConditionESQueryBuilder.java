package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.Map;

/**
* Created by toto on 27/06/14.
*/
public class MatchAllConditionESQueryBuilder implements ConditionESQueryBuilder {

    public MatchAllConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        return FilterBuilders.matchAllFilter();
    }
}
