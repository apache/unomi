package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.apache.commons.collections.CollectionUtils;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by toto on 27/06/14.
 */
public class PropertyConditionESQueryBuilder implements ConditionESQueryBuilder {

    public PropertyConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        String op = (String) condition.getParameterValues().get("comparisonOperator");
        String name = (String) condition.getParameterValues().get("propertyName");
        Object value = condition.getParameterValues().get("propertyValue");

        if(value instanceof Map) {
            List<FilterBuilder> filters = getRecursiveFilters((Map<String, Object>) value, op, name);
            if (CollectionUtils.isNotEmpty(filters)){
                return FilterBuilders.andFilter(filters.toArray(new FilterBuilder[filters.size()]));
            }
        } else {
            return getFilter(op, name, value);
        }

        return null;
    }

    private List<FilterBuilder> getRecursiveFilters(Map<String, Object> value, String op, String name) {
        List<FilterBuilder> result = new ArrayList<FilterBuilder>();
        for(Map.Entry<String, Object> entry : value.entrySet()) {
            if(entry.getValue() instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) entry.getValue();
                List<FilterBuilder> filters = getRecursiveFilters(m, op, name + "." + entry.getKey());
                if (CollectionUtils.isNotEmpty(filters)){
                    result.addAll(filters);
                }
            } else {
                result.add(getFilter(op, name + "." + entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }

    private FilterBuilder getFilter(String op, String name, Object value) {
        if (op.equals("equals")) {
            return FilterBuilders.termFilter(name, value);
        } else if (op.equals("greaterThan")) {
            return FilterBuilders.rangeFilter(name).gt(value);
        } else if (op.equals("greaterThanOrEqualTo")) {
            return FilterBuilders.rangeFilter(name).gte(value);
        } else if (op.equals("lessThan")) {
            return FilterBuilders.rangeFilter(name).lt(value);
        } else if (op.equals("lessThanOrEqualTo")) {
            return FilterBuilders.rangeFilter(name).lte(value);
        } else if (op.equals("exists")) {
            return FilterBuilders.existsFilter(name);
        } else if (op.equals("missing")) {
            return FilterBuilders.missingFilter(name);
        } else if (op.equals("contains")) {
            return FilterBuilders.termFilter(name, value);
        } else if (op.equals("startsWith")) {
            return FilterBuilders.prefixFilter(name, value.toString());
        } else if (op.equals("endsWith")) {
            return FilterBuilders.regexpFilter(name, ".*" + value);
        } else if (op.equals("matchesRegex")) {
            return FilterBuilders.regexpFilter(name, value.toString());
        }
        return null;
    }
}
