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

package org.apache.unomi.persistence.elasticsearch9.querybuilders.core;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.GeoDistanceType;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.util.ObjectBuilder;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch9.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch9.ConditionESQueryBuilderDispatcher;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.time.OffsetDateTime;
import java.util.*;

import static org.apache.unomi.persistence.spi.conditions.DateUtils.getDate;

/**
 * Builder to create Elasticsearch 9 queries from property conditions.
 * This class handles different types of comparison operators to create the corresponding queries.
 */
public class PropertyConditionESQueryBuilder implements ConditionESQueryBuilder {

    private final DateTimeFormatter dateTimeFormatter;

    // Operator groups for better organization
    private static final Set<String> EQUALITY_OPERATORS = Set.of("equals", "notEquals");
    private static final Set<String> COMPARISON_OPERATORS = Set.of("greaterThan", "lessThanOrEqualTo", "lessThan", "greaterThanOrEqualTo");
    private static final Set<String> EXISTENCE_OPERATORS = Set.of("exists", "missing");
    private static final Set<String> CONTENT_OPERATORS = Set.of("contains", "notContains", "startsWith", "endsWith", "matchesRegex");
    private static final Set<String> COLLECTION_OPERATORS = Set.of("in", "notIn", "all", "inContains", "hasSomeOf", "hasNoneOf");
    private static final Set<String> DATE_OPERATORS = Set.of("isDay", "isNotDay");

    public PropertyConditionESQueryBuilder() {
        this.dateTimeFormatter = ISODateTimeFormat.dateTime();
    }

    @Override
    public Query buildQuery(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        String comparisonOperator = (String) condition.getParameter("comparisonOperator");
        String propertyName = (String) condition.getParameter("propertyName");

        validateRequiredParameters(comparisonOperator, propertyName);

        // Extract and normalize condition values
        PropertyValues propertyValues = extractPropertyValues(condition);

        if (EQUALITY_OPERATORS.contains(comparisonOperator)) {
            return buildEqualityQuery(propertyName, propertyValues.singleValue, comparisonOperator);
        } else if (COMPARISON_OPERATORS.contains(comparisonOperator)) {
            return buildComparisonQuery(propertyName, propertyValues.singleValue, comparisonOperator);
        } else if (comparisonOperator.equals("between")) {
            return buildBetweenQuery(propertyName, propertyValues.multipleValues);
        } else if (EXISTENCE_OPERATORS.contains(comparisonOperator)) {
            return buildExistenceQuery(propertyName, comparisonOperator);
        } else if (CONTENT_OPERATORS.contains(comparisonOperator)) {
            return buildContentQuery(propertyName, (String)propertyValues.singleValue, comparisonOperator);
        } else if (COLLECTION_OPERATORS.contains(comparisonOperator)) {
            return buildCollectionQuery(propertyName, propertyValues.multipleValues, comparisonOperator);
        } else if (DATE_OPERATORS.contains(comparisonOperator)) {
            return buildDateQuery(propertyName, propertyValues.singleValue, comparisonOperator);
        } else if (comparisonOperator.equals("distance")) {
            return buildDistanceQuery(condition, propertyName);
        }

        return null;
    }

    /**
     * Class to group different types of property values
     */
    private static class PropertyValues {
        final Object singleValue;
        final Collection<?> multipleValues;

        PropertyValues(Object singleValue, Collection<?> multipleValues) {
            this.singleValue = singleValue;
            this.multipleValues = multipleValues;
        }
    }

    /**
     * Extracts and normalizes property values from the condition
     */
    private PropertyValues extractPropertyValues(Condition condition) {
        String stringValue = ConditionContextHelper.forceFoldToASCII(condition.getParameter("propertyValue"));
        Object integerValue = condition.getParameter("propertyValueInteger");
        Object doubleValue = condition.getParameter("propertyValueDouble");
        Object dateValue = convertDateToISO(condition.getParameter("propertyValueDate"));
        Object dateExprValue = condition.getParameter("propertyValueDateExpr");

        Collection<?> stringValues = ConditionContextHelper.forceFoldToASCII((Collection<?>) condition.getParameter("propertyValues"));
        Collection<?> integerValues = (Collection<?>) condition.getParameter("propertyValuesInteger");
        Collection<?> doubleValues = (Collection<?>) condition.getParameter("propertyValuesDouble");
        Collection<?> dateValues = convertDatesToISO((Collection<?>) condition.getParameter("propertyValuesDate"));
        Collection<?> dateExprValues = (Collection<?>) condition.getParameter("propertyValuesDateExpr");

        Object singleValue = ObjectUtils.firstNonNull(stringValue, integerValue, doubleValue, dateValue, dateExprValue);
        Collection<?> multipleValues = ObjectUtils.firstNonNull(stringValues, integerValues, doubleValues, dateValues, dateExprValues);

        return new PropertyValues(singleValue, multipleValues);
    }

    /**
     * Validates that required parameters are present
     */
    private void validateRequiredParameters(String comparisonOperator, String propertyName) {
        if (comparisonOperator == null || propertyName == null) {
            throw new IllegalArgumentException(
                    "Cannot build ES query, the condition is not valid: comparisonOperator and propertyName properties are required");
        }
    }

    /**
     * Checks that the required value is present
     */
    private void checkRequiredValue(Object value, String name, String operator, boolean multiple) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Cannot build ES query, missing value" + (multiple ? "s" : "") +
                            " for condition using operator: " + operator +
                            " and property: " + name);
        }
    }

    /**
     * Checks that the collection contains exactly the expected number of elements
     */
    private void checkRequiredValuesSize(Collection<?> values, String name, String operator, int expectedSize) {
        if (values == null || values.size() != expectedSize) {
            throw new IllegalArgumentException(
                    "Cannot build ES query, missing " + expectedSize +
                            " values for a condition using operator: " + operator +
                            " and property: " + name);
        }
    }

    /**
     * Builds an equality query (equals, notEquals)
     */
    private Query buildEqualityQuery(String propertyName, Object value, String operator) {
        checkRequiredValue(value, propertyName, operator, false);
        if (operator.equals("equals")) {
            return Query.of(q -> q.term(t -> t.field(propertyName).value(v -> getValue(value))));
        } else { // notEquals
            return Query.of(q -> q.bool(b -> b.mustNot(m -> m.term(t -> t.field(propertyName).value(v -> getValue(value))))));
        }
    }

    /**
     * Builds a comparison query (greaterThan, lessThan, etc.)
     */
    private Query buildComparisonQuery(String propertyName, Object value, String operator) {
        checkRequiredValue(value, propertyName, operator, false);
        return Query.of(q -> q.range(getRangeQuery(propertyName, value, operator)));
    }

    /**
     * Builds a between query
     */
    private Query buildBetweenQuery(String propertyName, Collection<?> values) {
        checkRequiredValuesSize(values, propertyName, "between", 2);
        return Query.of(q -> q.range(getRangeQuery(propertyName, values, "between")));
    }

    /**
     * Builds an existence query (exists, missing)
     */
    private Query buildExistenceQuery(String propertyName, String operator) {
        if (operator.equals("exists")) {
            return Query.of(q -> q.exists(e -> e.field(propertyName)));
        } else { // missing
            return Query.of(q -> q.bool(b -> b.mustNot(m -> m.exists(e -> e.field(propertyName)))));
        }
    }

    /**
     * Builds a content-based query (contains, startsWith, etc.)
     */
    private Query buildContentQuery(String propertyName, String value, String operator) {
        checkRequiredValue(value, propertyName, operator, false);

        return switch (operator) {
            case "contains" -> Query.of(q -> q.regexp(r -> r.field(propertyName).value(".*" + value + ".*")));
            case "notContains" -> Query.of(q -> q.bool(b -> b.mustNot(m ->
                    m.regexp(r -> r.field(propertyName).value(".*" + value + ".*")))));
            case "startsWith" -> Query.of(q -> q.prefix(p -> p.field(propertyName).value(value)));
            case "endsWith" -> Query.of(q -> q.regexp(r -> r.field(propertyName).value(".*" + value)));
            case "matchesRegex" -> Query.of(q -> q.regexp(r -> r.field(propertyName).value(value)));
            default -> throw new IllegalArgumentException("Unsupported content operator: " + operator);
        };
    }

    /**
     * Builds a collection-based query (in, notIn, all, etc.)
     */
    private Query buildCollectionQuery(String propertyName, Collection<?> values, String operator) {
        checkRequiredValue(values, propertyName, operator, true);

        return switch (operator) {
            case "in" -> Query.of(q -> q.terms(t -> t.field(propertyName).terms(t2 -> t2.value(getValues(values)))));
            case "notIn" -> Query.of(q -> q.bool(b -> b.mustNot(m ->
                    m.terms(t -> t.field(propertyName).terms(t2 -> t2.value(getValues(values)))))));
            case "all" -> {
                BoolQuery.Builder all = new BoolQuery.Builder();
                for (Object curValue : values) {
                    all.must(Query.of(q -> q.term(t -> t.field(propertyName).value(getValue(curValue).build()))));
                }
                yield Query.of(q -> q.bool(all.build()));
            }
            case "inContains" -> {
                BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
                for (Object curValue : values) {
                    boolQueryBuilder.must(Query.of(q -> q.regexp(r -> r.field(propertyName).value(".*" + curValue + ".*"))));
                }
                yield Query.of(q -> q.bool(boolQueryBuilder.build()));
            }
            case "hasSomeOf" -> {
                BoolQuery.Builder hasSomeOf = new BoolQuery.Builder();
                for (Object curValue : values) {
                    hasSomeOf.should(Query.of(q -> q.term(t -> t.field(propertyName).value(getValue(curValue).build()))));
                }
                yield Query.of(q -> q.bool(hasSomeOf.build()));
            }
            case "hasNoneOf" -> {
                BoolQuery.Builder hasNoneOf = new BoolQuery.Builder();
                for (Object curValue : values) {
                    hasNoneOf.mustNot(Query.of(q -> q.term(t -> t.field(propertyName).value(getValue(curValue).build()))));
                }
                yield Query.of(q -> q.bool(hasNoneOf.build()));
            }
            default -> throw new IllegalArgumentException("Unsupported collection operator: " + operator);
        };
    }

    /**
     * Builds a date-based query (isDay, isNotDay)
     */
    private Query buildDateQuery(String propertyName, Object value, String operator) {
        checkRequiredValue(value, propertyName, operator, false);
        if (operator.equals("isDay")) {
            return getIsSameDayRange(getDate(value), propertyName);
        } else { // isNotDay
            return Query.of(q -> q.bool(b -> b.mustNot(getIsSameDayRange(getDate(value), propertyName))));
        }
    }

    /**
     * Builds a geographical distance query
     */
    private Query buildDistanceQuery(Condition condition, String propertyName) {
        final String unitString = (String) condition.getParameter("unit");
        final Object centerObj = condition.getParameter("center");
        final Double distance = (Double) condition.getParameter("distance");

        if (centerObj == null || distance == null) {
            throw new IllegalArgumentException("The 'center' and 'distance' parameters are required for the distance operator");
        }

        String centerString;
        if (centerObj instanceof org.apache.unomi.api.GeoPoint) {
            centerString = ((org.apache.unomi.api.GeoPoint) centerObj).asString();
        } else if (centerObj instanceof String) {
            centerString = (String) centerObj;
        } else {
            centerString = centerObj.toString();
        }

        GeoDistanceType unit = unitString != null ? GeoDistanceType.valueOf(unitString) : GeoDistanceType.Plane;

        return Query.of(q -> q.geoDistance(g -> g.field(propertyName)
                .distance(distance + "")
                .distanceType(unit)
                .location(l -> l.text(centerString))));
    }

    /**
     * Creates a query to check if a date is on the same day
     */
    private Query getIsSameDayRange(Date value, String name) {
        DateTime date = new DateTime(value);
        DateTime dayStart = date.withTimeAtStartOfDay();
        DateTime dayAfterStart = date.plusDays(1).withTimeAtStartOfDay();

        return DateRangeQuery.of(d -> d.field(name)
                        .gte(dayStart.toString())
                        .lt(dayAfterStart.toString()))
                ._toRangeQuery()
                ._toQuery();
    }

    /**
     * Converts a date to ISO format
     */
    private Object convertDateToISO(Object dateValue) {
        if (dateValue == null) {
            return null;
        }

        if (dateValue instanceof Date) {
            return dateTimeFormatter.print(new DateTime(dateValue));
        } else if (dateValue instanceof OffsetDateTime) {
            return dateTimeFormatter.print(new DateTime(Date.from(((OffsetDateTime) dateValue).toInstant())));
        } else {
            return dateValue;
        }
    }

    /**
     * Converts a collection of dates to ISO format
     */
    private Collection<?> convertDatesToISO(Collection<?> datesValues) {
        if (datesValues == null) {
            return null;
        }

        List<Object> results = new ArrayList<>(datesValues.size());
        for (Object dateValue : datesValues) {
            if (dateValue != null) {
                results.add(convertDateToISO(dateValue));
            }
        }
        return results;
    }

    /**
     * Creates a range query based on the value type
     */
    private RangeQuery getRangeQuery(String fieldName, Object value, String comparisonOperator) {
        if (value instanceof String) {
            return new RangeQuery.Builder()
                    .term(t -> withComparison(t.field(fieldName), (String) value, comparisonOperator))
                    .build();
        } else if (value instanceof Date) {
            return new RangeQuery.Builder()
                    .date(t -> withComparison(t.field(fieldName), convertDateToISO(value).toString(), comparisonOperator))
                    .build();
        } else if (value instanceof Number) {
            return new RangeQuery.Builder()
                    .number(t -> withComparison(t.field(fieldName), ((Number) value).doubleValue(), comparisonOperator))
                    .build();
        } else if (value instanceof Collection<?>) {
            Iterator<?> iterator = ((Collection<?>) value).iterator();
            Object val1 = iterator.next();
            Object val2 = iterator.next();

            if (val1 instanceof String) {
                return new RangeQuery.Builder()
                        .term(t -> t.field(fieldName).gte((String) val1).lte((String) val2))
                        .build();
            } else if (val1 instanceof Date) {
                return new RangeQuery.Builder()
                        .date(t -> t.field(fieldName)
                                .gte(convertDateToISO(val1).toString())
                                .lte(convertDateToISO(val2).toString()))
                        .build();
            } else if (val1 instanceof Number) {
                return new RangeQuery.Builder()
                        .number(t -> t.field(fieldName)
                                .gte(((Number) val1).doubleValue())
                                .lte(((Number) val2).doubleValue()))
                        .build();
            }
        }

        throw new IllegalArgumentException("Unsupported value type for range query: " +
                (value != null ? value.getClass().getName() : "null"));
    }

    /**
     * Applies the appropriate comparison operator to the range query builder
     */
    private <K, T extends RangeQueryBase.AbstractBuilder<K, T>> T withComparison(RangeQueryBase.AbstractBuilder<K, T> range, K value, String comparisonOperator) {
        return switch (comparisonOperator) {
            case "greaterThan" -> range.gt(value);
            case "greaterThanOrEqualTo" -> range.gte(value);
            case "lessThan" -> range.lt(value);
            case "lessThanOrEqualTo" -> range.lte(value);
            default -> throw new IllegalArgumentException("Unsupported comparison operator for range query: " + comparisonOperator);
        };
    }

    /**
     * Converts a value to Elasticsearch FieldValue
     */
    private ObjectBuilder<FieldValue> getValue(Object fieldValue) {
        FieldValue.Builder fieldValueBuilder = new FieldValue.Builder();

        if (fieldValue instanceof String) {
            return fieldValueBuilder.stringValue((String) fieldValue);
        } else if (fieldValue instanceof Integer) {
            return fieldValueBuilder.longValue((Integer) fieldValue);
        } else if (fieldValue instanceof Long) {
            return fieldValueBuilder.longValue((Long) fieldValue);
        } else if (fieldValue instanceof Double) {
            return fieldValueBuilder.doubleValue((Double) fieldValue);
        } else if (fieldValue instanceof Float) {
            return fieldValueBuilder.doubleValue((Float) fieldValue);
        } else if (fieldValue instanceof Boolean) {
            return fieldValueBuilder.booleanValue((Boolean) fieldValue);
        } else if (fieldValue instanceof Date || fieldValue instanceof OffsetDateTime) {
            return fieldValueBuilder.stringValue(convertDateToISO(fieldValue).toString());
        }

        throw new IllegalArgumentException("Unsupported value type: " +
                (fieldValue != null ? fieldValue.getClass().getName() : "null"));
    }

    /**
     * Converts a collection of values to a list of Elasticsearch FieldValues
     */
    private List<FieldValue> getValues(Collection<?> fieldValues) {
        List<FieldValue> values = new ArrayList<>(fieldValues.size());
        for (Object fieldValue : fieldValues) {
            values.add(getValue(fieldValue).build());
        }
        return values;
    }
}