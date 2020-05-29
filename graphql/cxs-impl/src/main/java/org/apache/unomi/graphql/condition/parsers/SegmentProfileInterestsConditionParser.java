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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SegmentProfileInterestsConditionParser {

    private static final Predicate<Condition> IS_BOOLEAN_CONDITION_TYPE =
            condition -> "booleanCondition".equals(condition.getConditionTypeId());

    private final Condition condition;

    public SegmentProfileInterestsConditionParser(final Condition condition) {
        this.condition = condition;
    }

    public Map<String, Object> parse() {
        return processInterests(condition);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processInterests(final Condition condition) {
        final Map<String, Object> fieldsMap = new LinkedHashMap<>();

        ((List<Condition>) condition.getParameter("subConditions")).forEach(subCondition -> {
            if (IS_BOOLEAN_CONDITION_TYPE.test(subCondition)) {
                final List<Map<String, Object>> conditionList = ((List<Condition>) subCondition.getParameter("subConditions"))
                        .stream()
                        .map(this::processInterests)
                        .collect(Collectors.toList());

                fieldsMap.put(subCondition.getParameter("operator").toString(), conditionList);
            } else {
                final Map<String, Object> fieldAsTuple = createInterestField(subCondition);

                fieldsMap.putAll(fieldAsTuple);
            }
        });

        return fieldsMap;
    }

    private Map<String, Object> createInterestField(final Condition condition) {
        final String comparisonOperator =
                ComparisonConditionTranslator.translateFromUnomiToGraphQL(condition.getParameter("comparisonOperator").toString());

        final String fieldName = "score_" + comparisonOperator;

        final Map<String, Object> tuple = new HashMap<>();

        tuple.put(fieldName, condition.getParameter("propertyValueInteger"));
        tuple.put("topic_equals", condition.getParameter("propertyName").toString().replaceAll("properties.interests.", ""));

        return tuple;
    }

}
