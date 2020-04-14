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

package org.apache.unomi.graphql.condition;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.schema.ComparisonConditionTranslator;
import org.apache.unomi.graphql.schema.PropertyNameTranslator;
import org.apache.unomi.graphql.schema.PropertyValueTypeHelper;
import org.apache.unomi.graphql.types.input.CDPInterestFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileEventsFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfilePropertiesFilterInput;
import org.apache.unomi.graphql.types.input.CDPSegmentFilterInput;
import org.apache.unomi.graphql.utils.ConditionBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProfileConditionFactory extends ConditionFactory {

    private static ProfileConditionFactory instance;

    public static synchronized ProfileConditionFactory get(final DataFetchingEnvironment environment) {
        if (instance == null) {
            instance = new ProfileConditionFactory(environment);
        }
        return instance;
    }

    private ProfileConditionFactory(final DataFetchingEnvironment environment) {
        super("profilePropertyCondition", environment);
    }

    public Condition segmentFilterInputCondition(final CDPSegmentFilterInput filterInput, Date after, Date before) {
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (after != null) {
            rootSubConditions.add(datePropertyCondition("timeStamp", "greaterThan", after));
        }

        if (before != null) {
            rootSubConditions.add(datePropertyCondition("timeStamp", "lessThanOrEqual", before));
        }

        if (filterInput != null) {
            if (filterInput.getNameEquals() != null) {
                rootSubConditions.add(propertyCondition("metadata.name", filterInput.getNameEquals(), definitionsService));
            }

            if (filterInput.getViewEquals() != null) {
                rootSubConditions.add(propertyCondition("metadata.scope", filterInput.getViewEquals(), definitionsService));
            }

            if (filterInput.getAndFilters() != null && !filterInput.getAndFilters().isEmpty()) {
                final List<Condition> filterAndSubConditions = filterInput.getAndFilters().stream()
                        .map(andInput -> segmentFilterInputCondition(andInput, null, null))
                        .collect(Collectors.toList());
                rootSubConditions.add(booleanCondition("and", filterAndSubConditions));
            }

            if (filterInput.getOrFilters() != null && !filterInput.getOrFilters().isEmpty()) {
                final List<Condition> filterOrSubConditions = filterInput.getOrFilters().stream()
                        .map(orInput -> segmentFilterInputCondition(orInput, null, null))
                        .collect(Collectors.toList());
                rootSubConditions.add(booleanCondition("or", filterOrSubConditions));
            }
        }

        return booleanCondition("and", rootSubConditions);
    }

    public Condition profileFilterInputCondition(final CDPProfileFilterInput filterInput, final Map<String, Object> filterAsMap) {
        return profileFilterInputCondition(filterInput, filterAsMap, null, null);
    }

    public Condition profileFilterInputCondition(final CDPProfileFilterInput filterInput, final Map<String, Object> filterAsMap, Date after, Date before) {
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (after != null) {
            rootSubConditions.add(datePropertyCondition("timeStamp", "greaterThan", after));
        }

        if (before != null) {
            rootSubConditions.add(datePropertyCondition("timeStamp", "lessThanOrEqual", before));
        }

        if (filterInput != null) {
            if (filterInput.getProfileIDs_contains() != null && !filterInput.getProfileIDs_contains().isEmpty()) {
                rootSubConditions.add(propertiesCondition("itemId", "inContains", filterInput.getProfileIDs_contains()));
            }

            if (filterInput.getSegments_contains() != null && filterInput.getSegments_contains().isEmpty()) {
                rootSubConditions.add(ConditionBuilder.create(getConditionType("profileSegmentCondition"))
                        .parameter("segments", filterInput.getSegments_contains())
                        .parameter("matchType", "in")
                        .build());
            }

            if (filterInput.getConsents_contains() != null && !filterInput.getConsents_contains().isEmpty()) {
                rootSubConditions.add(consentContainsCondition(filterInput.getConsents_contains()));

            }

            if (filterInput.getLists_contains() != null && filterInput.getLists_contains().isEmpty()) {
                rootSubConditions.add(ConditionBuilder.create(getConditionType("profileUserListCondition"))
                        .parameter("lists", filterInput.getLists_contains())
                        .parameter("matchType", "in")
                        .build());
            }

            if (filterInput.getProperties() != null) {
                Map<String, Object> propertiesFilterAsMap = null;
                if (filterAsMap != null) {
                    propertiesFilterAsMap = (Map<String, Object>) filterAsMap.get("properties");
                }
                rootSubConditions.add(profilePropertiesFilterInputCondition(filterInput.getProperties(), propertiesFilterAsMap));
            }

            if (filterInput.getEvents() != null) {
                rootSubConditions.add(profileEventsFilterInputCondition(filterInput.getEvents()));
            }

            if (filterInput.getInterests() != null) {
                rootSubConditions.add(interestFilterInputCondition(filterInput.getInterests()));
            }
        }

        return booleanCondition("and", rootSubConditions);
    }

    private Condition consentContainsCondition(final List<String> consentsContains) {
        final List<Condition> subConditions = new ArrayList<>();

        for (final String value : consentsContains) {
            final String[] splittedValue = value.split("/", -1);

            final Condition scopeCondition = propertyCondition("consents." + value + ".scope", splittedValue[0]);

            final Condition typeIdentifierCondition = propertyCondition("consents." + value + ".typeIdentifier", splittedValue[1]);

            final Condition statusCondition = propertyCondition("consents." + value + ".status", "GRANTED");

            subConditions.add(booleanCondition("and", Arrays.asList(scopeCondition, typeIdentifierCondition, statusCondition)));
        }
        return booleanCondition("or", subConditions);
    }

    private Condition interestFilterInputCondition(final CDPInterestFilterInput filterInput) {
        final List<Condition> rootSubConditions = new ArrayList<>();

        final String propertyName = "properties.interests." + filterInput.getTopic_equals();

        if (filterInput.getTopic_equals() != null) {
            rootSubConditions.add(propertyCondition(propertyName, "exists", filterInput.getTopic_equals()));
        }

        if (filterInput.getScore_equals() != null) {
            rootSubConditions.add(integerPropertyCondition(propertyName, filterInput.getScore_equals()));
        }

        if (filterInput.getScore_gt() != null) {
            rootSubConditions.add(integerPropertyCondition(propertyName, "greaterThan", filterInput.getScore_gt()));
        }

        if (filterInput.getScore_gte() != null) {
            rootSubConditions.add(integerPropertyCondition(propertyName, "greaterThanOrEqualTo", filterInput.getScore_gte()));
        }

        if (filterInput.getScore_lt() != null) {
            rootSubConditions.add(integerPropertyCondition(propertyName, "lessThan", filterInput.getScore_lt()));
        }

        if (filterInput.getScore_lte() != null) {
            rootSubConditions.add(integerPropertyCondition(propertyName, "lessThanOrEqualTo", filterInput.getScore_lte()));
        }

        if (filterInput.getAnd() != null && !filterInput.getAnd().isEmpty()) {
            rootSubConditions.add(filtersToCondition(filterInput.getOr(), this::interestFilterInputCondition, "and"));
        }

        if (filterInput.getOr() != null && !filterInput.getOr().isEmpty()) {
            rootSubConditions.add(filtersToCondition(filterInput.getOr(), this::interestFilterInputCondition, "or"));
        }

        return booleanCondition("and", rootSubConditions);
    }

    private Condition profileEventsFilterInputCondition(final CDPProfileEventsFilterInput filterInput) {
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (filterInput.getNot() != null) {
            rootSubConditions.add(ConditionBuilder.create(getConditionType("notCondition"))
                    .parameter("subCondition", profileEventsFilterInputCondition(filterInput.getNot()))
                    .build());
        }

        final Condition pastCondition = pastEventsCondition(filterInput);
        if (pastCondition != null) {
            rootSubConditions.add(pastCondition);
        }

        if (filterInput.getAnd() != null && !filterInput.getAnd().isEmpty()) {
            rootSubConditions.add(filtersToCondition(filterInput.getAnd(), this::profileEventsFilterInputCondition, "and"));
        }

        if (filterInput.getOr() != null && !filterInput.getOr().isEmpty()) {
            rootSubConditions.add(filtersToCondition(filterInput.getOr(), this::profileEventsFilterInputCondition, "or"));
        }

        return booleanCondition("and", rootSubConditions);
    }

    private Condition profilePropertiesFilterInputCondition(final CDPProfilePropertiesFilterInput filterInput, final Map<String, Object> filterAsMap) {
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (filterInput.getAnd() != null && filterInput.getAnd().isEmpty()) {
            rootSubConditions.add(filtersToCondition(filterInput.getAnd(), andFilter -> profilePropertiesFilterInputCondition(andFilter, filterAsMap), "and"));
        }

        if (filterInput.getOr() != null && !filterInput.getOr().isEmpty()) {
            rootSubConditions.add(filtersToCondition(filterInput.getOr(), orFilter -> profilePropertiesFilterInputCondition(orFilter, filterAsMap), "or"));
        }

        final Condition dynamicCondition = dynamicProfilePropertiesCondition(filterAsMap);
        if (dynamicCondition != null) {
            rootSubConditions.add(dynamicCondition);
        }

        return booleanCondition("and", rootSubConditions);
    }

    private Condition dynamicProfilePropertiesCondition(Map<String, Object> filterAsMap) {
        if (filterAsMap == null || filterAsMap.isEmpty()) {
            return null;
        }

        final List<Condition> subConditions = new ArrayList<>();

        filterAsMap.forEach((propertyName, value) -> {
            if (!"and".equals(propertyName) && !"or".equals(propertyName)) {

                final String[] propertyFilter = propertyName.split("_", -1);

                final String propertyValueType =
                        PropertyValueTypeHelper.getPropertyValueParameterForInputType(
                                CDPProfilePropertiesFilterInput.TYPE_NAME, propertyName, environment);

                subConditions.add(propertyCondition(
                        "properties." + PropertyNameTranslator.translateFromGraphQLToUnomi(propertyFilter[0]),
                        ComparisonConditionTranslator.translateFromGraphQLToUnomi(propertyFilter[1]),
                        propertyValueType,
                        value));
            }
        });

        return booleanCondition("and", subConditions);
    }

    private Condition pastEventsCondition(final CDPProfileEventsFilterInput filterInput) {
        boolean notEmpty = false;
        final ConditionBuilder pastEventConditionBuilder = ConditionBuilder.create(getConditionType("pastEventCondition"));

        if (filterInput.getEventFilter() != null) {
            final Condition eventFilterCondition = EventConditionFactory.get(environment).eventFilterInputCondition(filterInput.getEventFilter(), null, null);

            pastEventConditionBuilder.parameter("eventCondition", eventFilterCondition);
            notEmpty = true;
        }

        if (filterInput.getMinimalCount() != null) {
            pastEventConditionBuilder.parameter("minimumEventCount", filterInput.getMinimalCount());
            notEmpty = true;
        }

        if (filterInput.getMaximalCount() != null) {
            pastEventConditionBuilder.parameter("maximumEventCount", filterInput.getMaximalCount());
            notEmpty = true;
        }

        return notEmpty ? pastEventConditionBuilder.build() : null;
    }
}
