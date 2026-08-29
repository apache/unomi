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
package org.apache.unomi.graphql.condition.parsers;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLInputObjectType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.schema.PropertyNameTranslator;
import org.apache.unomi.graphql.schema.PropertyValueTypeHelper;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.utils.DateUtils;
import org.apache.unomi.graphql.utils.ReflectionUtil;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SegmentProfileEventsConditionParser {

    private static final Predicate<Condition> IS_BOOLEAN_CONDITION_TYPE =
            condition -> "booleanCondition".equals(condition.getConditionTypeId());

    private static final Predicate<Condition> IS_NOT_CONDITION_TYPE =
            condition -> "notCondition".equals(condition.getConditionTypeId());

    private final Condition condition;

    private final DataFetchingEnvironment environment;

    public SegmentProfileEventsConditionParser(final Condition condition, final DataFetchingEnvironment environment) {
        this.condition = condition;
        this.environment = environment;
    }

    public Map<String, Object> parse() {
        return processProfileEvent(condition);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processProfileEvent(final Condition condition) {
        final Map<String, Object> fieldsMap = new LinkedHashMap<>();

        ((List<Condition>) condition.getParameter("subConditions")).forEach(subCondition -> {
            if (IS_BOOLEAN_CONDITION_TYPE.test(subCondition)) {
                final List<Map<String, Object>> conditionList = ((List<Condition>) subCondition.getParameter("subConditions"))
                        .stream()
                        .map(this::processProfileEvent)
                        .collect(Collectors.toList());

                fieldsMap.put(subCondition.getParameter("operator").toString(), conditionList);
            } else {
                final Map<String, Object> fieldAsTuple = createProfileEventField(subCondition);
                fieldsMap.putAll(fieldAsTuple);
            }
        });

        return fieldsMap;
    }

    private Map<String, Object> createProfileEventField(final Condition condition) {
        final Map<String, Object> result = new LinkedHashMap<>();

        result.put("maximalCount", condition.getParameter("maximumEventCount"));
        result.put("minimalCount", condition.getParameter("minimumEventCount"));
        if (IS_NOT_CONDITION_TYPE.test(condition)) {
            result.put("not", processProfileEvent((Condition) condition.getParameter("subCondition")));
        } else {
            result.put("eventFilter", processProfileEventProperties((Condition) condition.getParameter("eventCondition")));
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processProfileEventProperties(final Condition condition) {
        final Map<String, Object> fieldsMap = new LinkedHashMap<>();

        if (IS_BOOLEAN_CONDITION_TYPE.test(condition)) {
            ((List<Condition>) condition.getParameter("subConditions")).forEach(subCondition -> {
                if (IS_BOOLEAN_CONDITION_TYPE.test(subCondition)) {
                    final List<Condition> subConditions = (List<Condition>) subCondition.getParameter("subConditions");

                    if ("and".equals(subCondition.getParameter("operator").toString())
                            && subConditions.stream().anyMatch(c -> c.getParameter("propertyName") != null
                            && "eventType".equals(c.getParameter("propertyName").toString()))) {
                        processDynamicEventField(subCondition, fieldsMap);
                    } else {
                        final List<Map<String, Object>> conditionList = subConditions.stream()
                                .map(this::processProfileEventProperties)
                                .collect(Collectors.toList());

                        fieldsMap.put(subCondition.getParameter("operator").toString(), conditionList);
                    }
                } else {
                    processEventPropertyCondition(subCondition, fieldsMap);
                }
            });
        } else {
            processEventPropertyCondition(condition, fieldsMap);
        }

        return fieldsMap;
    }

    private void processEventPropertyCondition(final Condition condition, final Map<String, Object> fieldsMap) {
        final Map<String, Object> fieldAsTuple = createProfileEventPropertyField(condition);
        if (fieldAsTuple.size() == 2) {
            fieldsMap.put(fieldAsTuple.get("fieldName").toString(), fieldAsTuple.get("fieldValue"));
        }
    }

    @SuppressWarnings("unchecked")
    private void processDynamicEventField(final Condition condition, final Map<String, Object> container) {
        final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");

        final String dynamicFieldName = subConditions.stream()
                .filter(subCondition -> "eventType".equals(subCondition.getParameter("propertyName").toString()))
                .map(subCondition -> subCondition.getParameter("propertyValue").toString())
                .findFirst().orElse(null);

        if (dynamicFieldName != null) {
            final GraphQLInputObjectType inputObjectType =
                    (GraphQLInputObjectType) environment.getGraphQLSchema().getType(ReflectionUtil.resolveTypeName(CDPEventFilterInput.class));

            final String typeName = ((GraphQLInputObjectType) inputObjectType.getFieldDefinition(dynamicFieldName).getType()).getName();

            final Map<String, Object> dynamicFieldAsMap = new HashMap<>();

            for (final Condition subCondition : subConditions) {
                if ("eventType".equals(subCondition.getParameter("propertyName").toString())) {
                    continue;
                }

                final String propertyName = subCondition.getParameter("propertyName").toString().replace("properties.", "");

                final String comparisonOperator = subCondition.getParameter("comparisonOperator").toString();

                final String fieldName = PropertyNameTranslator.translateFromUnomiToGraphQL(propertyName) + "_" + comparisonOperator;

                final String propertyValueType = PropertyValueTypeHelper.getPropertyValueParameterForInputType(typeName, fieldName, environment);

                dynamicFieldAsMap.put(fieldName, subCondition.getParameter(propertyValueType));
            }

            container.put(dynamicFieldName, dynamicFieldAsMap);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createProfileEventPropertyField(final Condition condition) {
        final Map<String, Object> tuple = new HashMap<>();

        final String propertyName = condition.getParameter("propertyName").toString();

        if ("timeStamp".equals(propertyName)) {
            final String comparisonOperator = condition.getParameter("comparisonOperator").toString();

            if ("equals".equals(comparisonOperator)) {
                tuple.put("fieldName", "cdp_timestamp_equals");
            } else if ("lessThan".equals(comparisonOperator)) {
                tuple.put("fieldName", "cdp_timestamp_lt");
            } else if ("lessThanOrEqualTo".equals(comparisonOperator)) {
                tuple.put("fieldName", "cdp_timestamp_lte");
            } else if ("greaterThan".equals(comparisonOperator)) {
                tuple.put("fieldName", "cdp_timestamp_gt");
            } else {
                tuple.put("fieldName", "cdp_timestamp_gte");
            }

            Object propertyValueDate = condition.getParameter("propertyValueDate");
            if (propertyValueDate == null) {
                tuple.put("fieldValue", null);
            } else if (propertyValueDate instanceof Map){
                // This shouldn't be needed since Jackson was upgraded to > 2.13, but we keep it for backwards compatibility with older data sets
                final OffsetDateTime fieldValue = DateUtils.offsetDateTimeFromMap((Map<String, Object>) propertyValueDate);
                tuple.put("fieldValue", fieldValue != null ? fieldValue.toString() : null);
            } else {
                tuple.put("fieldValue", propertyValueDate.toString());
            }
        } else {
            if ("source.itemId".equals(propertyName)) {
                tuple.put("fieldName", "cdp_sourceID_equals");
            } else if ("profileId".equals(propertyName)) {
                tuple.put("fieldName", "cdp_profileID_equals");
            } else if ("itemId".equals(propertyName)) {
                tuple.put("fieldName", "id_equals");
            } else if ("properties.clientId".equals(propertyName)) {
                tuple.put("fieldName", "cdp_clientID_equals");
            }

            tuple.put("fieldValue", condition.getParameter("propertyValue"));
        }

        return tuple;
    }
}
