/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.plugins.baseplugin.conditions;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionContextHelper;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.elasticsearch.common.joda.time.DateTime;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;

import java.util.List;
import java.util.Map;

public class PropertyConditionESQueryBuilder implements ConditionESQueryBuilder {

    public PropertyConditionESQueryBuilder() {
    }

    @Override
    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        String op = (String) condition.getParameter("comparisonOperator");
        String name = (String) condition.getParameter("propertyName");

        if(op == null || name == null){
            throw new IllegalArgumentException("Impossible to build ES filter, condition is not valid, comparisonOperator and propertyName properties should be provided");
        }

        String expectedValue = ConditionContextHelper.foldToASCII((String) condition.getParameter("propertyValue"));
        Object expectedValueInteger = condition.getParameter("propertyValueInteger");
        Object expectedValueDate = condition.getParameter("propertyValueDate");
        Object expectedValueDateExpr = condition.getParameter("propertyValueDateExpr");

        List<?> expectedValues = ConditionContextHelper.foldToASCII((List<?>) condition.getParameter("propertyValues"));
        List<?> expectedValuesInteger = (List<?>) condition.getParameter("propertyValuesInteger");
        List<?> expectedValuesDate = (List<?>) condition.getParameter("propertyValuesDate");
        List<?> expectedValuesDateExpr = (List<?>) condition.getParameter("propertyValuesDateExpr");

        Object value = ObjectUtils.firstNonNull(expectedValue,expectedValueInteger,expectedValueDate,expectedValueDateExpr);
        @SuppressWarnings("unchecked")
        List<?> values = ObjectUtils.firstNonNull(expectedValues,expectedValuesInteger,expectedValuesDate,expectedValuesDateExpr);

        switch (op) {
            case "equals":
                checkRequiredValue(value, name, op, false);
                return FilterBuilders.termFilter(name, value);
            case "notEquals":
                checkRequiredValue(value, name, op, false);
                return FilterBuilders.notFilter(FilterBuilders.termFilter(name, value));
            case "greaterThan":
                checkRequiredValue(value, name, op, false);
                return FilterBuilders.rangeFilter(name).gt(value);
            case "greaterThanOrEqualTo":
                checkRequiredValue(value, name, op, false);
                return FilterBuilders.rangeFilter(name).gte(value);
            case "lessThan":
                checkRequiredValue(value, name, op, false);
                return FilterBuilders.rangeFilter(name).lt(value);
            case "lessThanOrEqualTo":
                checkRequiredValue(value, name, op, false);
                return FilterBuilders.rangeFilter(name).lte(value);
            case "between":
                checkRequiredValuesSize(values, name, op, 2);
                return FilterBuilders.rangeFilter(name).gte(values.get(0)).lte(values.get(1));
            case "exists":
                return FilterBuilders.existsFilter(name);
            case "missing":
                return FilterBuilders.missingFilter(name);
            case "contains":
                checkRequiredValue(expectedValue, name, op, false);
                return FilterBuilders.regexpFilter(name, ".*" + expectedValue + ".*");
            case "startsWith":
                checkRequiredValue(expectedValue, name, op, false);
                return FilterBuilders.prefixFilter(name, expectedValue);
            case "endsWith":
                checkRequiredValue(expectedValue, name, op, false);
                return FilterBuilders.regexpFilter(name, ".*" + expectedValue);
            case "matchesRegex":
                checkRequiredValue(expectedValue, name, op, false);
                return FilterBuilders.regexpFilter(name, expectedValue);
            case "in":
                checkRequiredValue(values, name, op, true);
                return FilterBuilders.inFilter(name, values.toArray());
            case "notIn":
                checkRequiredValue(values, name, op, true);
                return FilterBuilders.notFilter(FilterBuilders.inFilter(name, values.toArray()));
            case "all":
                checkRequiredValue(values, name, op, true);
                return FilterBuilders.termsFilter(name, values.toArray()).execution("and");
            case "isDay":
                checkRequiredValue(value, name, op, false);
                return getIsSameDayRange(value, name);
            case "isNotDay":
                checkRequiredValue(value, name, op, false);
                return FilterBuilders.notFilter(getIsSameDayRange(value, name));
        }
        return null;
    }

    private void checkRequiredValuesSize(List<?> values, String name, String operator, int expectedSize) {
        if(values == null || values.size() != expectedSize) {
            throw new IllegalArgumentException("Impossible to build ES filter, missing " + expectedSize + " values for a condition using comparisonOperator: " + operator + ", and propertyName: " + name);
        }
    }

    private void checkRequiredValue(Object value, String name, String operator, boolean multiple) {
        if(value == null) {
            throw new IllegalArgumentException("Impossible to build ES filter, missing value" + (multiple ? "s" : "") + " for condition using comparisonOperator: " + operator + ", and propertyName: " + name);
        }
    }

    private FilterBuilder getIsSameDayRange (Object value, String name) {
        DateTime date = new DateTime(value);
        DateTime dayStart = date.withTimeAtStartOfDay();
        DateTime dayAfterStart = date.plusDays(1).withTimeAtStartOfDay();
        return FilterBuilders.rangeFilter(name).gte(dayStart.toDate()).lte(dayAfterStart.toDate());
    }
}
