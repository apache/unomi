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
package org.apache.unomi.graphql.condition.factories;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.types.input.CDPTopicFilterInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TopicConditionFactory extends ConditionFactory {

    private static TopicConditionFactory instance;

    public static synchronized TopicConditionFactory get(final DataFetchingEnvironment environment) {
        if (instance == null) {
            instance = new TopicConditionFactory(environment);
        }

        return instance;
    }

    private TopicConditionFactory(final DataFetchingEnvironment environment) {
        super("topicPropertyCondition", environment);
    }

    @SuppressWarnings("unchecked")
    public Condition filterInputCondition(final CDPTopicFilterInput filterInput, final Map<String, Object> filterInputAsMap) {
        if (filterInput == null) {
            return matchAllCondition();
        }

        final List<Condition> rootSubConditions = new ArrayList<>();

        if (filterInput.getId_equals() != null) {
            rootSubConditions.add(propertyCondition("itemId", filterInput.getId_equals()));
        }

        if (filterInput.getName_equals() != null) {
            rootSubConditions.add(propertyCondition("name", filterInput.getName_equals()));
        }

        if (filterInput.getView_equals() != null) {
            rootSubConditions.add(propertyCondition("scope", filterInput.getView_equals()));
        }

        if (filterInputAsMap.get("and") != null) {
            final List<Map<String, Object>> andFilterInputAsMap = (List<Map<String, Object>>) filterInputAsMap.get("and");

            rootSubConditions.add(filtersToCondition(filterInput.getAnd(), andFilterInputAsMap, this::filterInputCondition, "and"));
        }

        if (filterInputAsMap.get("or") != null) {
            final List<Map<String, Object>> orFilterInputAsMap = (List<Map<String, Object>>) filterInputAsMap.get("or");

            rootSubConditions.add(filtersToCondition(filterInput.getOr(), orFilterInputAsMap, this::filterInputCondition, "or"));
        }

        return booleanCondition("and", rootSubConditions);
    }

}
