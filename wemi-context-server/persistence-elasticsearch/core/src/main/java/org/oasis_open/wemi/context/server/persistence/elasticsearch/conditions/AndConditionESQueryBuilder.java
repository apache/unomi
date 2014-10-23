package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by toto on 27/06/14.
 */
public class AndConditionESQueryBuilder implements ConditionESQueryBuilder {

    public AndConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        List<Condition> conditions = (List<Condition>) condition.getParameterValues().get("subConditions");

        List<FilterBuilder> l = new ArrayList<FilterBuilder>();
        for (Object sub : conditions) {
            l.add(dispatcher.buildFilter((Condition) sub, context));
        }
        return FilterBuilders.andFilter(l.toArray(new FilterBuilder[l.size()]));
    }
}
