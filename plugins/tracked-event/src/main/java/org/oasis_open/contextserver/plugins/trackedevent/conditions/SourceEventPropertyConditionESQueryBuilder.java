package org.oasis_open.contextserver.plugins.trackedevent.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by kevan on 28/01/15.
 */
public class SourceEventPropertyConditionESQueryBuilder implements ConditionESQueryBuilder {

    public SourceEventPropertyConditionESQueryBuilder() {
    }

    private void appendFilderIfPropExist(List<FilterBuilder> filterBuilders, Condition condition, String prop){
        if (condition.getParameterValues().get(prop) != null && !"".equals(condition.getParameterValues().get(prop))) {
            filterBuilders.add(FilterBuilders.termFilter("source." + prop, (String) condition.getParameterValues().get(prop)));
        }
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        List<FilterBuilder> l = new ArrayList<FilterBuilder>();
        for (String prop : new String[]{"id", "path", "scope", "type"}){
            appendFilderIfPropExist(l, condition, prop);
        }

        if (l.size() >= 1) {
            return l.size() == 1 ? l.get(0) : FilterBuilders.andFilter(l.toArray(new FilterBuilder[l.size()]));
        } else {
            return null;
        }
    }
}