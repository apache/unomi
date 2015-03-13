package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

/*
 * #%L
 * context-server-persistence-elasticsearch-core
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.commons.lang3.ObjectUtils;
import org.elasticsearch.common.base.MoreObjects;
import org.elasticsearch.common.base.Objects;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Date;
import java.util.List;
import java.util.Map;

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
            return FilterBuilders.rangeFilter(name).gte(values.get(0)).lte(values.get(1));
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
