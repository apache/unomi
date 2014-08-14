package org.oasis_open.wemi.context.server.plugins.events.hover.querybuilders;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions.ESQueryBuilder;

/**
* Created by toto on 27/06/14.
*/
public class HoverEventConditionESQueryBuilder implements ESQueryBuilder {

    public HoverEventConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        return FilterBuilders.andFilter(
                FilterBuilders.termFilter("eventType", "hover"),
                FilterBuilders.termFilter("properties.hoverContentName", ((String) condition.getParameterValues().get("contentName"))));
    }
}
