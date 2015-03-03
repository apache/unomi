package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Map;

/**
 * Builder for NOT condition.
 */
public class NotConditionESQueryBuilder implements ConditionESQueryBuilder {

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        Condition subCondition = (Condition) condition.getParameterValues().get("subCondition");
        return FilterBuilders.notFilter(dispatcher.buildFilter(subCondition, context));
    }
}
