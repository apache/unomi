package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
 * Created by toto on 20/08/14.
 */
public class SessionDurationConditionESQueryBuilder implements ESQueryBuilder {
    @Override
    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        String min = (String) condition.getParameterValues().get("minimumDuration");
        String max = (String) condition.getParameterValues().get("maximumDuration");
        RangeFilterBuilder builder = FilterBuilders.rangeFilter("duration");
        if (min != null && !"".equals(min)) {
            builder = builder.gt(Integer.parseInt(min) * 1000);
        }
        if (max != null && !"".equals(max)) {
            builder = builder.lt(Integer.parseInt(max) * 1000);
        }
        return builder;
    }
}
