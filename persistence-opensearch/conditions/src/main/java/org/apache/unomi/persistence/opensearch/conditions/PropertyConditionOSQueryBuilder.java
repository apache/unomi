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

package org.apache.unomi.persistence.opensearch.conditions;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.opensearch.ConditionOSQueryBuilder;
import org.apache.unomi.persistence.opensearch.ConditionOSQueryBuilderDispatcher;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.GeoDistanceType;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.util.ObjectBuilder;

import java.time.OffsetDateTime;
import java.util.*;

import static org.apache.unomi.persistence.spi.conditions.DateUtils.getDate;

public class PropertyConditionOSQueryBuilder implements ConditionOSQueryBuilder {

    DateTimeFormatter dateTimeFormatter;

    public PropertyConditionOSQueryBuilder() {
        dateTimeFormatter = ISODateTimeFormat.dateTime();
    }

    @Override
    public Query buildQuery(Condition condition, Map<String, Object> context, ConditionOSQueryBuilderDispatcher dispatcher) {
        String comparisonOperator = (String) condition.getParameter("comparisonOperator");
        String name = (String) condition.getParameter("propertyName");

        if (comparisonOperator == null || name == null) {
            throw new IllegalArgumentException("Impossible to build ES filter, condition is not valid, comparisonOperator and propertyName properties should be provided");
        }

        String expectedValue = ConditionContextHelper.foldToASCII((String) condition.getParameter("propertyValue"));
        Object expectedValueInteger = condition.getParameter("propertyValueInteger");
        Object expectedValueDouble = condition.getParameter("propertyValueDouble");
        Object expectedValueDate = convertDateToISO(condition.getParameter("propertyValueDate"));
        Object expectedValueDateExpr = condition.getParameter("propertyValueDateExpr");

        Collection<?> expectedValues = ConditionContextHelper.foldToASCII((Collection<?>) condition.getParameter("propertyValues"));
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
                return Query.of(q->q.term(t->t.field(name).value(v->getValue(value))));
            case "notEquals":
                checkRequiredValue(value, name, comparisonOperator, false);
                return Query.of(q->q.bool(b->b.mustNot(m->m.term(t->t.field(name).value(v->getValue(value))))));
            case "greaterThan":
                checkRequiredValue(value, name, comparisonOperator, false);
                return Query.of(q->q.range(r->r.field(name).gt(JsonData.of(value))));
            case "greaterThanOrEqualTo":
                checkRequiredValue(value, name, comparisonOperator, false);
                return Query.of(q->q.range(r->r.field(name).gte(JsonData.of(value))));
            case "lessThan":
                checkRequiredValue(value, name, comparisonOperator, false);
                return Query.of(q->q.range(r->r.field(name).lt(JsonData.of(value))));
            case "lessThanOrEqualTo":
                checkRequiredValue(value, name, comparisonOperator, false);
                return Query.of(q->q.range(r->r.field(name).lte(JsonData.of(value))));
            case "between":
                checkRequiredValuesSize(values, name, comparisonOperator, 2);
                Iterator<?> iterator = values.iterator();
                return Query.of(q->q.range(r->r.field(name).gte(JsonData.of(iterator.next())).lte(JsonData.of(iterator.next()))));
            case "exists":
                return Query.of(q->q.exists(e->e.field(name)));
            case "missing":
                return Query.of(q->q.bool(b->b.mustNot(m->m.exists(e->e.field(name)))));
            case "contains":
                checkRequiredValue(expectedValue, name, comparisonOperator, false);
                return Query.of(q->q.regexp(r->r.field(name).value(".*" + expectedValue + ".*")));
            case "notContains":
                checkRequiredValue(expectedValue, name, comparisonOperator, false);
                return Query.of(q->q.bool(b->b.mustNot(m->m.regexp(r->r.field(name).value(".*" + expectedValue + ".*")))));
            case "startsWith":
                checkRequiredValue(expectedValue, name, comparisonOperator, false);
                return Query.of(q->q.prefix(p->p.field(name).value(expectedValue)));
            case "endsWith":
                checkRequiredValue(expectedValue, name, comparisonOperator, false);
                return Query.of(q->q.regexp(r->r.field(name).value(".*" + expectedValue)));
            case "matchesRegex":
                checkRequiredValue(expectedValue, name, comparisonOperator, false);
                return Query.of(q->q.regexp(r->r.field(name).value(expectedValue)));
            case "in":
                checkRequiredValue(values, name, comparisonOperator, true);
                return Query.of(q->q.terms(t->t.field(name).terms(t2->t2.value(getValues(values)))));
            case "notIn":
                checkRequiredValue(values, name, comparisonOperator, true);
                return Query.of(q->q.bool(b->b.mustNot(m->m.terms(t->t.field(name).terms(t2->t2.value(getValues(values)))))));
            case "all":
                checkRequiredValue(values, name, comparisonOperator, true);
                BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
                for (Object curValue : values) {
                    boolQueryBuilder.must(Query.of(q->q.term(t->t.field(name).value(getValue(curValue).build()))));
                }
                return Query.of(q->q.bool(boolQueryBuilder.build()));
            case "inContains":
                checkRequiredValue(values, name, comparisonOperator, true);
                BoolQuery.Builder boolQueryBuilderInContains = new BoolQuery.Builder();
                for (Object curValue : values) {
                    boolQueryBuilderInContains.must(Query.of(q->q.regexp(r->r.field(name).value(".*" + curValue + ".*"))));
                }
                return Query.of(q->q.bool(boolQueryBuilderInContains.build()));
            case "hasSomeOf":
                checkRequiredValue(values, name, comparisonOperator, true);
                boolQueryBuilder = new BoolQuery.Builder();
                for (Object curValue : values) {
                    boolQueryBuilder.should(Query.of(q->q.term(t->t.field(name).value(getValue(curValue).build()))));
                }
                return Query.of(q->q.bool(boolQueryBuilder.build()));
            case "hasNoneOf":
                checkRequiredValue(values, name, comparisonOperator, true);
                boolQueryBuilder = new BoolQuery.Builder();
                for (Object curValue : values) {
                    boolQueryBuilder.mustNot(Query.of(q->q.term(t->t.field(name).value(getValue(curValue).build()))));
                }
                return Query.of(q->q.bool(boolQueryBuilder.build()));
            case "isDay":
                checkRequiredValue(value, name, comparisonOperator, false);
                return getIsSameDayRange(getDate(value), name);
            case "isNotDay":
                checkRequiredValue(value, name, comparisonOperator, false);
                return Query.of(q->q.bool(b->b.mustNot(getIsSameDayRange(getDate(value), name))));
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
                    GeoDistanceType unit = unitString != null ? GeoDistanceType.valueOf(unitString) : GeoDistanceType.Plane;

                    return Query.of(q->q.geoDistance(g->g.field(name).distance(distance + "").distanceType(unit).location(l->l.text(centerString))));
                }
        }
        return null;
    }

    private void checkRequiredValuesSize(Collection<?> values, String name, String operator, int expectedSize) {
        if (values == null || values.size() != expectedSize) {
            throw new IllegalArgumentException("Impossible to build OS filter, missing " + expectedSize + " values for a condition using comparisonOperator: " + operator + ", and propertyName: " + name);
        }
    }

    private void checkRequiredValue(Object value, String name, String operator, boolean multiple) {
        if (value == null) {
            throw new IllegalArgumentException("Impossible to build ES filter, missing value" + (multiple ? "s" : "") + " for condition using comparisonOperator: " + operator + ", and propertyName: " + name);
        }
    }

    private Query getIsSameDayRange(Object value, String name) {
        DateTime date = new DateTime(value);
        DateTime dayStart = date.withTimeAtStartOfDay();
        DateTime dayAfterStart = date.plusDays(1).withTimeAtStartOfDay();
        return Query.of(q->q.range(r->r
                .field(name)
                .gte(JsonData.of(convertDateToISO(dayStart.toDate())))
                .lte(JsonData.of(convertDateToISO(dayAfterStart.toDate())))));
    }

    private Object convertDateToISO(Object dateValue) {
        if (dateValue == null) {
            return dateValue;
        }
        if (dateValue instanceof Date) {
            return dateTimeFormatter.print(new DateTime(dateValue));
        } else if (dateValue instanceof OffsetDateTime) {
            return dateTimeFormatter.print(new DateTime(Date.from(((OffsetDateTime)dateValue).toInstant())));
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
        } else if (fieldValue instanceof Date) {
            return fieldValueBuilder.stringValue(convertDateToISO((Date) fieldValue).toString());
        } else if (fieldValue instanceof OffsetDateTime) {
            return fieldValueBuilder.stringValue(convertDateToISO((OffsetDateTime) fieldValue).toString());
        } else {
            throw new IllegalArgumentException("Impossible to build ES filter, unsupported value type: " + fieldValue.getClass().getName());
        }
    }

    private List<FieldValue> getValues(Collection<?> fieldValues) {
        List<FieldValue> values = new ArrayList<>();
        for (Object fieldValue : fieldValues) {
            values.add(getValue(fieldValue).build());
        }
        return values;
    }
}
