package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
* Created by toto on 27/06/14.
*/
class UserPropertyConditionESQueryBuilder extends AbstractESQueryBuilder {

    @Override
    public String getConditionId() {
        return "userPropertyCondition";
    }

    @Override
    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        String op = (String) condition.getParameterValues().get("comparisonOperator");
        String name = (String) condition.getParameterValues().get("propertyName");
        String value = (String) condition.getParameterValues().get("propertyValue");
        if (op.equals("equals")) {
            return FilterBuilders.termFilter(name, value);
        } else if (op.equals("greaterThan")) {
            return FilterBuilders.rangeFilter(name).gt(Integer.parseInt(value));
        } else if (op.equals("lessThan")) {
            return FilterBuilders.rangeFilter(name).lt(Integer.parseInt(value));
        }
        return null;
    }
}
