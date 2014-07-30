package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
* Created by toto on 27/06/14.
*/
class HoverEventConditionESQueryBuilder implements ESQueryBuilder {

    public String getConditionId() {
        return "hoverEventCondition";
    }

    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        return FilterBuilders.andFilter(
                FilterBuilders.termFilter("eventType", "hover"),
                FilterBuilders.termFilter("hoverContentName", ((String) condition.getParameterValues().get("contentName")).toLowerCase()));
    }
}
