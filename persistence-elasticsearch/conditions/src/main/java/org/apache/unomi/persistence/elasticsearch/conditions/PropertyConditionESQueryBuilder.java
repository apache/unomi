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

package org.apache.unomi.persistence.elasticsearch.conditions;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilderDispatcher;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.*;

import static org.apache.unomi.persistence.spi.conditions.DateUtils.getDate;

public class PropertyConditionESQueryBuilder implements ConditionESQueryBuilder {

    DateTimeFormatter dateTimeFormatter;

    public PropertyConditionESQueryBuilder() {
        dateTimeFormatter = ISODateTimeFormat.dateTime();
    }

    @Override
    public QueryBuilder buildQuery(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        String comparisonOperator = (String) condition.getParameter("comparisonOperator");
        String name = (String) condition.getParameter("propertyName");

        if (comparisonOperator == null || name == null) {
            throw new IllegalArgumentException("Impossible to build ES filter, condition is not valid, comparisonOperator and propertyName properties should be provided");
        }

        String expectedValue = ConditionContextHelper.forceFoldToASCII(condition.getParameter("propertyValue"));
        Object expectedValueInteger = condition.getParameter("propertyValueInteger");
        Object expectedValueDouble = condition.getParameter("propertyValueDouble");
        Object expectedValueDate = convertDateToISO(condition.getParameter("propertyValueDate"));
        Object expectedValueDateExpr = condition.getParameter("propertyValueDateExpr");

        Collection<?> expectedValues = ConditionContextHelper.forceFoldToASCII((Collection<?>) condition.getParameter("propertyValues"));
        Collection<?> expectedValuesInteger = (Collection<?>) condition.getParameter("propertyValuesInteger");
        Collection<?> expectedValuesDouble = (Collection<?>) condition.getParameter("propertyValuesDouble");
        Collection<?> expectedValuesDate = convertDatesToISO((Collection<?>) condition.getParameter("propertyValuesDate"));
        Collection<?> expectedValuesDateExpr = (Collection<?>) condition.getParameter("propertyValuesDateExpr");

        Object value = ObjectUtils.firstNonNull(expectedValue, expectedValueInteger, expectedValueDouble, expectedValueDate, expectedValueDateExpr);
        @SuppressWarnings("unchecked")
        Collection<?> values = ObjectUtils.firstNonNull(expectedValues, expectedValuesInteger, expectedValuesDouble, expectedValuesDate, expectedValuesDateExpr);

        switch (comparisonOperator) {
            case "equals":
                checkRequiredValue(value, name, comparisonOperator, false);
                return QueryBuilders.termQuery(name, value);
            case "notEquals":
                checkRequiredValue(value, name, comparisonOperator, false);
                return QueryBuilders.boolQuery().mustNot(QueryBuilders.termQuery(name, value));
            case "greaterThan":
                checkRequiredValue(value, name, comparisonOperator, false);
                return QueryBuilders.rangeQuery(name).gt(value);
            case "greaterThanOrEqualTo":
                checkRequiredValue(value, name, comparisonOperator, false);
                return QueryBuilders.rangeQuery(name).gte(value);
            case "lessThan":
                checkRequiredValue(value, name, comparisonOperator, false);
                return QueryBuilders.rangeQuery(name).lt(value);
            case "lessThanOrEqualTo":
                checkRequiredValue(value, name, comparisonOperator, false);
                return QueryBuilders.rangeQuery(name).lte(value);
            case "between":
                checkRequiredValuesSize(values, name, comparisonOperator, 2);
                Iterator<?> iterator = values.iterator();
                return QueryBuilders.rangeQuery(name).gte(iterator.next()).lte(iterator.next());
            case "exists":
                return QueryBuilders.existsQuery(name);
            case "missing":
                return QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery((name)));
            case "contains":
                checkRequiredValue(expectedValue, name, comparisonOperator, false);
                return QueryBuilders.regexpQuery(name, ".*" + expectedValue + ".*");
            case "notContains":
                checkRequiredValue(expectedValue, name, comparisonOperator, false);
                return QueryBuilders.boolQuery().mustNot(QueryBuilders.regexpQuery(name, ".*" + expectedValue + ".*"));
            case "startsWith":
                checkRequiredValue(expectedValue, name, comparisonOperator, false);
                return QueryBuilders.prefixQuery(name, expectedValue);
            case "endsWith":
                checkRequiredValue(expectedValue, name, comparisonOperator, false);
                return QueryBuilders.regexpQuery(name, ".*" + expectedValue);
            case "matchesRegex":
                checkRequiredValue(expectedValue, name, comparisonOperator, false);
                return QueryBuilders.regexpQuery(name, expectedValue);
            case "in":
                checkRequiredValue(values, name, comparisonOperator, true);
                return QueryBuilders.termsQuery(name, values.toArray());
            case "notIn":
                checkRequiredValue(values, name, comparisonOperator, true);
                return QueryBuilders.boolQuery().mustNot(QueryBuilders.termsQuery(name, values.toArray()));
            case "all":
                checkRequiredValue(values, name, comparisonOperator, true);
                BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
                for (Object curValue : values) {
                    boolQueryBuilder.must(QueryBuilders.termQuery(name, curValue));
                }
                return boolQueryBuilder;
            case "inContains":
                checkRequiredValue(values, name, comparisonOperator, true);
                BoolQueryBuilder boolQueryBuilderInContains = QueryBuilders.boolQuery();
                for (Object curValue : values) {
                    boolQueryBuilderInContains.must(QueryBuilders.regexpQuery(name, ".*" + curValue + ".*"));
                }
                return boolQueryBuilderInContains;
            case "hasSomeOf":
                checkRequiredValue(values, name, comparisonOperator, true);
                boolQueryBuilder = QueryBuilders.boolQuery();
                for (Object curValue : values) {
                    boolQueryBuilder.should(QueryBuilders.termQuery(name, curValue));
                }
                return boolQueryBuilder;
            case "hasNoneOf":
                checkRequiredValue(values, name, comparisonOperator, true);
                boolQueryBuilder = QueryBuilders.boolQuery();
                for (Object curValue : values) {
                    boolQueryBuilder.mustNot(QueryBuilders.termQuery(name, curValue));
                }
                return boolQueryBuilder;
            case "isDay":
                checkRequiredValue(value, name, comparisonOperator, false);
                return getIsSameDayRange(getDate(value), name);
            case "isNotDay":
                checkRequiredValue(value, name, comparisonOperator, false);
                return QueryBuilders.boolQuery().mustNot(getIsSameDayRange(getDate(value), name));
            case "distance":
                final String unitString = (String) condition.getParameter("unit");
                final Object centerObj = condition.getParameter("center");
                final Double distance = (Double) condition.getParameter("distance");

                if (centerObj != null && distance != null) {
                    String centerString;
                    if (centerObj instanceof org.apache.unomi.api.GeoPoint) {
                        centerString = ((org.apache.unomi.api.GeoPoint) centerObj).asString();
                    } else if (centerObj instanceof String) {
                        centerString = (String) centerObj;
                    } else {
                        centerString = centerObj.toString();
                    }
                    DistanceUnit unit = unitString != null ? DistanceUnit.fromString(unitString) : DistanceUnit.DEFAULT;

                    return QueryBuilders.geoDistanceQuery(name)
                            .ignoreUnmapped(true)
                            .distance(distance, unit)
                            .point(new GeoPoint(centerString));
                }
        }
        return null;
    }

    private void checkRequiredValuesSize(Collection<?> values, String name, String operator, int expectedSize) {
        if (values == null || values.size() != expectedSize) {
            throw new IllegalArgumentException("Impossible to build ES filter, missing " + expectedSize + " values for a condition using comparisonOperator: " + operator + ", and propertyName: " + name);
        }
    }

    private void checkRequiredValue(Object value, String name, String operator, boolean multiple) {
        if (value == null) {
            throw new IllegalArgumentException("Impossible to build ES filter, missing value" + (multiple ? "s" : "") + " for condition using comparisonOperator: " + operator + ", and propertyName: " + name);
        }
    }

    private QueryBuilder getIsSameDayRange(Object value, String name) {
        DateTime date = new DateTime(value);
        DateTime dayStart = date.withTimeAtStartOfDay();
        DateTime dayAfterStart = date.plusDays(1).withTimeAtStartOfDay();
        return QueryBuilders.rangeQuery(name).gte(convertDateToISO(dayStart.toDate())).lte(convertDateToISO(dayAfterStart.toDate()));
    }

    private Object convertDateToISO(Object dateValue) {
        if (dateValue == null) {
            return dateValue;
        }
        if (dateValue instanceof Date) {
            return dateTimeFormatter.print(new DateTime(dateValue));
        } else {
            return dateValue;
        }
    }

    private Collection<?> convertDatesToISO(Collection<?> datesValues) {
        List<Object> results = new ArrayList<>();
        if (datesValues == null) {
            return null;
        }
        for (Object dateValue : datesValues) {
            if (dateValue != null) {
                results.add(convertDateToISO(dateValue));
            }
        }
        return results;
    }
}
