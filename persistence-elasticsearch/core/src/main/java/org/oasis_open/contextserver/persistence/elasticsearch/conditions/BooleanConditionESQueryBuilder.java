package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;
import java.util.Map;

/**
 * ES query builder for boolean conditions.
 */
public class BooleanConditionESQueryBuilder implements ConditionESQueryBuilder {

    @Override
    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context,
            ConditionESQueryBuilderDispatcher dispatcher) {
        boolean isAndOperator = "and".equalsIgnoreCase((String) condition.getParameterValues().get("operator"));
        @SuppressWarnings("unchecked")
        List<Condition> conditions = (List<Condition>) condition.getParameterValues().get("subConditions");

        int conditionCount = conditions.size();

        if (conditionCount == 1) {
            return dispatcher.buildFilter(conditions.get(0), context);
        }

        FilterBuilder[] l = new FilterBuilder[conditionCount];
        for (int i = 0; i < conditionCount; i++) {
            l[i] = dispatcher.buildFilter(conditions.get(i), context);
        }

        return isAndOperator ? FilterBuilders.andFilter(l) : FilterBuilders.orFilter(l);
    }
}
