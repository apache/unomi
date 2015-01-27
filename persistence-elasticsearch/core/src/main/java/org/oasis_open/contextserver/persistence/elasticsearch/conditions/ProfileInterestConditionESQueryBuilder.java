package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import java.util.Map;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;

/**
 * Elasticsearch query builder for the profile interest condition.
 */
public class ProfileInterestConditionESQueryBuilder implements ConditionESQueryBuilder {

    @Override
    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context,
            ConditionESQueryBuilderDispatcher dispatcher) {
        String name = (String) condition.getParameterValues().get("propertyName");
        Integer value = (Integer) condition.getParameterValues().get("propertyValue");
        return FilterBuilders.rangeFilter("properties.interest." + name).gt(value);
    }
}
