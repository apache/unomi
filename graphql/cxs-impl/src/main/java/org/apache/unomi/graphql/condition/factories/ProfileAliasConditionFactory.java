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
import org.apache.unomi.graphql.types.input.CDPProfileAliasFilterInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProfileAliasConditionFactory extends ConditionFactory {

    private static ProfileAliasConditionFactory instance;

    public static synchronized ProfileAliasConditionFactory get(final DataFetchingEnvironment environment) {
        if (instance == null) {
            instance = new ProfileAliasConditionFactory(environment);
        }

        return instance;
    }

    private ProfileAliasConditionFactory(final DataFetchingEnvironment environment) {
        super("profileAliasesPropertyCondition", environment);
    }

    @SuppressWarnings("unchecked")
    public Condition filterInputCondition(final CDPProfileAliasFilterInput filterInput, final Map<String, Object> filterInputAsMap) {
        if (filterInput == null) {
            return matchAllCondition();
        }

        final List<Condition> rootSubConditions = new ArrayList<>();

        if (filterInput.getAlias_equals() != null) {
            rootSubConditions.add(propertyCondition("itemId", filterInput.getAlias_equals()));
        }

        if (filterInput.getProfileID_equals() != null) {
            rootSubConditions.add(propertyCondition("profileID.keyword", filterInput.getProfileID_equals()));
        }

        if (filterInput.getClientID_equals() != null) {
            rootSubConditions.add(propertyCondition("clientID.keyword", filterInput.getClientID_equals()));
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
