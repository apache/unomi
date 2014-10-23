package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.Map;

/**
 * Created by toto on 20/08/14.
 */
public class SessionDurationConditionESQueryBuilder implements ConditionESQueryBuilder {
    @Override
    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        Integer min = (Integer) condition.getParameterValues().get("minimumDuration");
        Integer max = (Integer) condition.getParameterValues().get("maximumDuration");
        RangeFilterBuilder builder = FilterBuilders.rangeFilter("duration");
        if (min != null) {
            builder = builder.gt(min * 1000);
        }
        if (max != null) {
            builder = builder.lt(max * 1000);
        }
        return builder;
    }
}
