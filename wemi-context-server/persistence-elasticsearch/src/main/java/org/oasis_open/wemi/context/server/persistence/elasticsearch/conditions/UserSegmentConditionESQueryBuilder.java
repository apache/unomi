package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
 * Created by toto on 14/08/14.
 */
public class UserSegmentConditionESQueryBuilder implements ESQueryBuilder {

    public UserSegmentConditionESQueryBuilder() {
    }

    @Override
    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        return FilterBuilders.termFilter("segment", condition.getParameterValues().get("segment"));
    }
}
