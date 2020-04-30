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

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.schema.ComparisonConditionTranslator;
import org.apache.unomi.graphql.utils.DateUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SegmentProfilePropertiesConditionParser {

    private static final Predicate<Condition> IS_BOOLEAN_CONDITION_TYPE =
            condition -> "booleanCondition".equals(condition.getConditionTypeId());

    private final Condition condition;

    public SegmentProfilePropertiesConditionParser(Condition condition) {
        this.condition = condition;
    }

    public Map<String, Object> parse() {
        return processProfileProperties(condition);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processProfileProperties(final Condition condition) {
        final Map<String, Object> fieldsMap = new LinkedHashMap<>();

        ((List<Condition>) condition.getParameter("subConditions")).forEach(subCondition -> {
            if (IS_BOOLEAN_CONDITION_TYPE.test(subCondition)) {
                final List<Map<String, Object>> conditionList = ((List<Condition>) subCondition.getParameter("subConditions"))
                        .stream()
                        .map(this::processProfileProperties)
                        .collect(Collectors.toList());

                fieldsMap.put(subCondition.getParameter("operator").toString(), conditionList);
            } else {
                final Map<String, Object> fieldAsTuple = createProfilePropertiesField(subCondition);
                fieldsMap.putAll(fieldAsTuple);
            }
        });

        return fieldsMap;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createProfilePropertiesField(final Condition condition) {
        final String propertyName = condition.getParameter("propertyName").toString().replaceAll("properties.", "");

        final String comparisonOperator = ComparisonConditionTranslator.translateFromUnomiToGraphQL(condition.getParameter("comparisonOperator").toString());

        final String fieldName = propertyName + "_" + comparisonOperator;

        Object value;

        if (condition.getParameter("propertyValueDate") != null) {
            value = DateUtils.offsetDateTimeFromMap((Map<String, Object>) condition.getParameter("propertyValueDate"));
        } else if (condition.getParameter("propertyValueInteger") != null) {
            value = condition.getParameter("propertyValueInteger");
        } else {
            value = condition.getParameter("propertyValue");
        }

        return Collections.singletonMap(fieldName, value);
    }

}
