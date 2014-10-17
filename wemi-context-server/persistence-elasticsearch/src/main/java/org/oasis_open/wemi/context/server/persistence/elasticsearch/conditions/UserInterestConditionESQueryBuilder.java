package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.Map;

/**
* Created by toto on 27/06/14.
*/
public class UserInterestConditionESQueryBuilder implements ConditionESQueryBuilder {

    public UserInterestConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        String name = (String) condition.getParameterValues().get("propertyName");
        Integer value = (Integer) condition.getParameterValues().get("propertyValue");
        return FilterBuilders.rangeFilter("properties.interest."+name).gt(value);
    }
}
