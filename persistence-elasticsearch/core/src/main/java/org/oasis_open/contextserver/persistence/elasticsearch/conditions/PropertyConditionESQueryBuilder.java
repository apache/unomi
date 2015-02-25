package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.apache.commons.lang3.ObjectUtils;
import org.elasticsearch.common.base.MoreObjects;
import org.elasticsearch.common.base.Objects;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by toto on 27/06/14.
 */
public class PropertyConditionESQueryBuilder implements ConditionESQueryBuilder {

    public PropertyConditionESQueryBuilder() {
    }

    @Override
    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        String op = (String) condition.getParameterValues().get("comparisonOperator");
        String name = (String) condition.getParameterValues().get("propertyName");

        String expectedValue = (String) condition.getParameter("propertyValue");
        Object expectedValueInteger = condition.getParameter("propertyValueInteger");
        Object expectedValueDate = condition.getParameter("propertyValueDate");
        Object expectedValueDateExpr = condition.getParameter("propertyValueDateExpr");

        List expectedValues = (List) condition.getParameter("propertyValues");
        List expectedValuesInteger = (List) condition.getParameter("propertyValuesInteger");
        List expectedValuesDate = (List) condition.getParameter("propertyValuesDate");
        List expectedValuesDateExpr = (List) condition.getParameter("propertyValuesDateExpr");

        Object value = ObjectUtils.firstNonNull(expectedValue,expectedValueInteger,expectedValueDate,expectedValueDateExpr);
        List values = ObjectUtils.firstNonNull(expectedValues,expectedValuesInteger,expectedValuesDate,expectedValuesDateExpr);

        if (op.equals("equals")) {
            return FilterBuilders.termFilter(name, value);
        } else if (op.equals("notEquals")) {
            return FilterBuilders.notFilter(FilterBuilders.termFilter(name, value));
        } else if (op.equals("greaterThan")) {
            return FilterBuilders.rangeFilter(name).gt(value);
        } else if (op.equals("greaterThanOrEqualTo")) {
            return FilterBuilders.rangeFilter(name).gte(value);
        } else if (op.equals("lessThan")) {
            return FilterBuilders.rangeFilter(name).lt(value);
        } else if (op.equals("lessThanOrEqualTo")) {
            return FilterBuilders.rangeFilter(name).lte(value);
        } else if (op.equals("between")) {
            return FilterBuilders.rangeFilter(name).gte(values.get(0)).lt(values.get(1));
        } else if (op.equals("exists")) {
            return FilterBuilders.existsFilter(name);
        } else if (op.equals("missing")) {
            return FilterBuilders.missingFilter(name);
        } else if (op.equals("contains")) {
            return FilterBuilders.regexpFilter(name, ".*" + expectedValue + ".*");
        } else if (op.equals("startsWith")) {
            return FilterBuilders.prefixFilter(name, expectedValue);
        } else if (op.equals("endsWith")) {
            return FilterBuilders.regexpFilter(name, ".*" + expectedValue);
        } else if (op.equals("matchesRegex")) {
            return FilterBuilders.regexpFilter(name, expectedValue);
        } else if (op.equals("in")) {
            return FilterBuilders.inFilter(name, values.toArray());
        } else if (op.equals("notIn")) {
            return FilterBuilders.notFilter(FilterBuilders.inFilter(name, values.toArray()));
        } else if (op.equals("all")) {
            return FilterBuilders.termsFilter(name, values.toArray()).execution("and");
        }
        return null;
    }

}
