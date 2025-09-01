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
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.schema.ComparisonConditionTranslator;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.utils.DateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SegmentProfilePropertiesConditionParser {

    private static final Predicate<Condition> IS_BOOLEAN_CONDITION_TYPE =
            condition -> "booleanCondition".equals(condition.getConditionTypeId());

    private final Condition condition;

    private final Map<String, PropertyType> profilePropertiesAsMap;

    public SegmentProfilePropertiesConditionParser(final Condition condition, final DataFetchingEnvironment environment) {
        this.condition = condition;

        final ServiceManager serviceManager = environment.getContext();

        profilePropertiesAsMap = serviceManager.getService(ProfileService.class).getTargetPropertyTypes("profiles").stream()
                .collect(Collectors.toMap(PropertyType::getItemId, Function.identity()));
    }

    public Map<String, Object> parse() {
        return processProfileProperties(condition);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processProfileProperties(final Condition condition) {
        final Map<String, Object> fieldsMap = new LinkedHashMap<>();

        final List<Condition> setConditions = new ArrayList<>();

        ((List<Condition>) condition.getParameter("subConditions")).forEach(subCondition -> {
            if (IS_BOOLEAN_CONDITION_TYPE.test(subCondition)) {
                final List<Map<String, Object>> conditionList = ((List<Condition>) subCondition.getParameter("subConditions"))
                        .stream()
                        .map(this::processProfileProperties)
                        .collect(Collectors.toList());

                fieldsMap.put(subCondition.getParameter("operator").toString(), conditionList);
            } else {
                if (isSimpleCondition(subCondition)) {
                    fieldsMap.putAll(createProfilePropertiesField(subCondition));
                } else {
                    setConditions.add(subCondition);
                }
            }
        });

        if (!setConditions.isEmpty()) {
            fieldsMap.putAll(processSetConditions(setConditions));
        }

        return fieldsMap;
    }

    private boolean isSimpleCondition(final Condition condition) {
        final String propertyName = condition.getParameter("propertyName").toString().replaceAll("properties.", "");

        return profilePropertiesAsMap.containsKey(propertyName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processSetConditions(final List<Condition> conditions) {
        final Map<Integer, List<Condition>> groupedConditionsByDeepLevels = new TreeMap<>();

        conditions.forEach(condition -> {
            final String propertyName = condition.getParameter("propertyName").toString().replaceAll("properties.", "");

            final String[] propertiesPath = propertyName.split("\\.", -1);

            if (!groupedConditionsByDeepLevels.containsKey(propertiesPath.length)) {
                groupedConditionsByDeepLevels.put(propertiesPath.length, new ArrayList<>());
            }
            groupedConditionsByDeepLevels.get(propertiesPath.length).add(condition);
        });

        final Map<String, Object> fieldsMap = new LinkedHashMap<>();

        groupedConditionsByDeepLevels.values().forEach(setConditions -> setConditions.forEach(condition -> {
            final String propertyName = condition.getParameter("propertyName").toString().replaceAll("properties.", "");

            final String[] propertiesPath = propertyName.split("\\.", -1);

            Map<String, Object> tempFieldsMap = fieldsMap;

            for (int i = 0; i < propertiesPath.length; i++) {
                if (i == propertiesPath.length - 1) {
                    tempFieldsMap.putAll(createProfilePropertiesField(propertiesPath[i], condition));
                } else {
                    if (!tempFieldsMap.containsKey(propertiesPath[i])) {
                        tempFieldsMap.put(propertiesPath[i], new HashMap<>());
                    }
                    tempFieldsMap = (Map<String, Object>) tempFieldsMap.get(propertiesPath[i]);
                }
            }
        }));

        return fieldsMap;
    }

    private Map<String, Object> createProfilePropertiesField(final Condition condition) {
        final String propertyName = condition.getParameter("propertyName").toString().replaceAll("properties.", "");

        return createProfilePropertiesField(propertyName, condition);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createProfilePropertiesField(final String propertyName, final Condition condition) {
        final String comparisonOperator = ComparisonConditionTranslator.translateFromUnomiToGraphQL(condition.getParameter("comparisonOperator").toString());

        final String fieldName = propertyName + "_" + comparisonOperator;

        Object value;

        if (condition.getParameter("propertyValueDate") != null) {
            value = condition.getParameter("propertyValueDate");
        } else if (condition.getParameter("propertyValueInteger") != null) {
            value = condition.getParameter("propertyValueInteger");
        } else {
            value = condition.getParameter("propertyValue");
        }

        return Collections.singletonMap(fieldName, value);
    }

}
