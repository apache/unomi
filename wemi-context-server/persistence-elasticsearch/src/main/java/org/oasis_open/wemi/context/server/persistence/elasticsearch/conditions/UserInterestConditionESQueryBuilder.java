package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
* Created by toto on 27/06/14.
*/
public class UserInterestConditionESQueryBuilder implements ESQueryBuilder {

    public UserInterestConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        String name = (String) condition.getParameterValues().get("propertyName");
        String value = (String) condition.getParameterValues().get("propertyValue");
        return FilterBuilders.rangeFilter("properties.interest."+name).gt(value);
    }
}
