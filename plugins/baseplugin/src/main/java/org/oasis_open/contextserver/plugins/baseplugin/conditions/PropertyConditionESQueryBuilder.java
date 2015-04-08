package org.oasis_open.contextserver.plugins.baseplugin.conditions;

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
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;

import java.util.List;
import java.util.Map;

public class PropertyConditionESQueryBuilder implements ConditionESQueryBuilder {

    public PropertyConditionESQueryBuilder() {
    }

    @Override
    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        String op = (String) condition.getParameter("comparisonOperator");
        String name = (String) condition.getParameter("propertyName");

        String expectedValue = (String) condition.getParameter("propertyValue");
        Object expectedValueInteger = condition.getParameter("propertyValueInteger");
        Object expectedValueDate = condition.getParameter("propertyValueDate");
        Object expectedValueDateExpr = condition.getParameter("propertyValueDateExpr");

        List<?> expectedValues = (List<?>) condition.getParameter("propertyValues");
        List<?> expectedValuesInteger = (List<?>) condition.getParameter("propertyValuesInteger");
        List<?> expectedValuesDate = (List<?>) condition.getParameter("propertyValuesDate");
        List<?> expectedValuesDateExpr = (List<?>) condition.getParameter("propertyValuesDateExpr");

        Object value = ObjectUtils.firstNonNull(expectedValue,expectedValueInteger,expectedValueDate,expectedValueDateExpr);
        @SuppressWarnings("unchecked")
        List<?> values = ObjectUtils.firstNonNull(expectedValues,expectedValuesInteger,expectedValuesDate,expectedValuesDateExpr);

        switch (op) {
            case "equals":
                return FilterBuilders.termFilter(name, value);
            case "notEquals":
                return FilterBuilders.notFilter(FilterBuilders.termFilter(name, value));
            case "greaterThan":
                return FilterBuilders.rangeFilter(name).gt(value);
            case "greaterThanOrEqualTo":
                return FilterBuilders.rangeFilter(name).gte(value);
            case "lessThan":
                return FilterBuilders.rangeFilter(name).lt(value);
            case "lessThanOrEqualTo":
                return FilterBuilders.rangeFilter(name).lte(value);
            case "between":
                return FilterBuilders.rangeFilter(name).gte(values.get(0)).lte(values.get(1));
            case "exists":
                return FilterBuilders.existsFilter(name);
            case "missing":
                return FilterBuilders.missingFilter(name);
            case "contains":
                return FilterBuilders.regexpFilter(name, ".*" + expectedValue + ".*");
            case "startsWith":
                return FilterBuilders.prefixFilter(name, expectedValue);
            case "endsWith":
                return FilterBuilders.regexpFilter(name, ".*" + expectedValue);
            case "matchesRegex":
                return FilterBuilders.regexpFilter(name, expectedValue);
            case "in":
                return FilterBuilders.inFilter(name, values.toArray());
            case "notIn":
                return FilterBuilders.notFilter(FilterBuilders.inFilter(name, values.toArray()));
            case "all":
                return FilterBuilders.termsFilter(name, values.toArray()).execution("and");
        }
        return null;
    }

}
