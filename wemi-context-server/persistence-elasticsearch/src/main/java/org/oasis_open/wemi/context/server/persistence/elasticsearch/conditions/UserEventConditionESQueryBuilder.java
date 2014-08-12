package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
 * Created by toto on 11/08/14.
 */
public class UserEventConditionESQueryBuilder implements ESQueryBuilder {
    public UserEventConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        String numberOfDays = (String) condition.getParameterValues().get("numberOfDays");
        RangeFilterBuilder builder = FilterBuilders.rangeFilter((String) condition.getParameterValues().get("generatedPropertyKey"));
        if (numberOfDays != null) {
            builder = builder.gt("now-" + numberOfDays + "d").lt("now");
        }
        return builder;
    }
}
