package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BooleanConditionESQueryBuilder implements ConditionESQueryBuilder {

    public BooleanConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        boolean op = "and".equalsIgnoreCase((String) condition.getParameterValues().get("operator"));
        List<Condition> conditions = (List<Condition>) condition.getParameterValues().get("subConditions");

        List<FilterBuilder> l = new ArrayList<FilterBuilder>();
        for (Object sub : conditions) {
            l.add(dispatcher.buildFilter((Condition) sub, context));
        }

        if (l.size() == 1) {
            return l.get(0);
        }
        if (op) {
            return FilterBuilders.andFilter(l.toArray(new FilterBuilder[l.size()]));
        } else {
            return FilterBuilders.orFilter(l.toArray(new FilterBuilder[l.size()]));
        }
    }
}
