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
import org.apache.unomi.api.GeoPoint;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.schema.ComparisonConditionTranslator;
import org.apache.unomi.graphql.schema.PropertyNameTranslator;
import org.apache.unomi.graphql.schema.PropertyValueTypeHelper;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.*;
import org.apache.unomi.graphql.utils.ConditionBuilder;
import org.apache.unomi.graphql.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    public Condition segmentFilterInputCondition(final CDPSegmentFilterInput filterInput) {
        if (filterInput == null) {
            return matchAllCondition();
        }

        final List<Condition> rootSubConditions = new ArrayList<>();

        if (filterInput.getNameEquals() != null) {
            rootSubConditions.add(propertyCondition("metadata.name", filterInput.getNameEquals(), definitionsService));
        }

        if (filterInput.getViewEquals() != null) {
            rootSubConditions.add(propertyCondition("metadata.scope", filterInput.getViewEquals(), definitionsService));
        }

        if (filterInput.getAndFilters() != null && !filterInput.getAndFilters().isEmpty()) {
            final List<Condition> filterAndSubConditions = filterInput.getAndFilters().stream()
                    .map(this::segmentFilterInputCondition)
                    .collect(Collectors.toList());
            rootSubConditions.add(booleanCondition("and", filterAndSubConditions));
        }

        if (filterInput.getOrFilters() != null && !filterInput.getOrFilters().isEmpty()) {
            final List<Condition> filterOrSubConditions = filterInput.getOrFilters().stream()
                    .map(this::segmentFilterInputCondition)
                    .collect(Collectors.toList());
            rootSubConditions.add(booleanCondition("or", filterOrSubConditions));
        }

        return booleanCondition("and", rootSubConditions);
    }

    @SuppressWarnings("unchecked")
    public Condition profileFilterInputCondition(final CDPProfileFilterInput filterInput, final Map<String, Object> filterInputAsMap) {
        if (filterInput == null) {
            return matchAllCondition();
        }

        final List<Condition> rootSubConditions = new ArrayList<>();

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
            final Map<String, Object> propertiesFilterAsMap = (Map<String, Object>) filterInputAsMap.get("properties");
            rootSubConditions.add(profilePropertiesFilterInputCondition(filterInput.getProperties(), propertiesFilterAsMap));
        }

        if (filterInput.getEvents() != null) {
            final Map<String, Object> eventsFilterAsMap = (Map<String, Object>) filterInputAsMap.get("events");
            rootSubConditions.add(profileEventsFilterInputCondition(filterInput.getEvents(), eventsFilterAsMap));
        }

        if (filterInput.getInterests() != null) {
            rootSubConditions.add(interestFilterInputCondition(filterInput.getInterests()));
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

    private Condition buildConditionInterestValue(Double interestValue, String operator) {
        return numberPropertyCondition("properties.interests.value", operator, interestValue);
    }

    private Condition interestFilterInputCondition(final CDPInterestFilterInput filterInput) {
        final List<Condition> subConditions = new ArrayList<>();

        if (filterInput.getTopic_equals() != null) {
            subConditions.add(propertyCondition("properties.interests.key", filterInput.getTopic_equals()));
        }

        if (filterInput.getScore_equals() != null) {
            subConditions.add(buildConditionInterestValue(filterInput.getScore_equals(), "equals"));
        }

        if (filterInput.getScore_gt() != null) {
            subConditions.add(buildConditionInterestValue(filterInput.getScore_gt(), "greaterThan"));
        }

        if (filterInput.getScore_gte() != null) {
            subConditions.add(buildConditionInterestValue(filterInput.getScore_gte(), "greaterThanOrEqualTo"));
        }

        if (filterInput.getScore_lt() != null) {
            subConditions.add(buildConditionInterestValue(filterInput.getScore_lt(), "lessThan"));
        }

        if (filterInput.getScore_lte() != null) {
            subConditions.add(buildConditionInterestValue(filterInput.getScore_lte(), "lessThanOrEqualTo"));
        }

        if (filterInput.getAnd() != null && !filterInput.getAnd().isEmpty()) {
            subConditions.add(filtersToCondition(filterInput.getOr(), this::interestFilterInputCondition, "and"));
        }

        if (filterInput.getOr() != null && !filterInput.getOr().isEmpty()) {
            subConditions.add(filtersToCondition(filterInput.getOr(), this::interestFilterInputCondition, "or"));
        }

        final Condition nestedCondition = new Condition(definitionsService.getConditionType("nestedCondition"));
        nestedCondition.setParameter("path", "properties.interests");
        nestedCondition.setParameter("subCondition", booleanCondition("and", subConditions));
        return nestedCondition;
    }

    @SuppressWarnings("unchecked")
    private Condition profileEventsFilterInputCondition(final CDPProfileEventsFilterInput filterInput, final Map<String, Object> eventsFilterAsMap) {
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (filterInput.getNot() != null) {
            final Map<String, Object> notEventsFilterAsMap = (Map<String, Object>) eventsFilterAsMap.get("not");

            rootSubConditions.add(ConditionBuilder.create(getConditionType("notCondition"))
                    .parameter("subCondition", profileEventsFilterInputCondition(filterInput.getNot(), notEventsFilterAsMap))
                    .build());
        }

        final Condition pastCondition = pastEventsCondition(filterInput, eventsFilterAsMap);
        if (pastCondition != null) {
            rootSubConditions.add(pastCondition);
        }

        if (filterInput.getAnd() != null && !filterInput.getAnd().isEmpty()) {
            final List<Map<String, Object>> listEventsFilterAsMap = (List<Map<String, Object>>) eventsFilterAsMap.get("and");

            rootSubConditions.add(filtersToCondition(filterInput.getAnd(), listEventsFilterAsMap, this::profileEventsFilterInputCondition, "and"));
        }

        if (filterInput.getOr() != null && !filterInput.getOr().isEmpty()) {
            final List<Map<String, Object>> listEventsFilterAsMap = (List<Map<String, Object>>) eventsFilterAsMap.get("or");

            rootSubConditions.add(filtersToCondition(filterInput.getOr(), listEventsFilterAsMap, this::profileEventsFilterInputCondition, "or"));
        }

        return booleanCondition("and", rootSubConditions);
    }

    @SuppressWarnings("unchecked")
    private Condition profilePropertiesFilterInputCondition(final CDPProfilePropertiesFilterInput filterInput, final Map<String, Object> filterAsMap) {
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (filterInput.getAnd() != null && !filterInput.getAnd().isEmpty()) {
            final List<Map<String, Object>> listFilterAsMap = (List<Map<String, Object>>) filterAsMap.get("and");

            rootSubConditions.add(filtersToCondition(filterInput.getAnd(), listFilterAsMap, this::profilePropertiesFilterInputCondition, "and"));
        }

        if (filterInput.getOr() != null && !filterInput.getOr().isEmpty()) {
            final List<Map<String, Object>> listFilterAsMap = (List<Map<String, Object>>) filterAsMap.get("or");

            rootSubConditions.add(filtersToCondition(filterInput.getOr(), listFilterAsMap, this::profilePropertiesFilterInputCondition, "or"));
        }

        addDynamicProfilePropertiesCondition(filterAsMap, rootSubConditions);

        return booleanCondition("and", rootSubConditions);
    }

    private void addDynamicProfilePropertiesCondition(final Map<String, Object> filterAsMap, final List<Condition> subConditions) {
        final ServiceManager serviceManager = environment.getContext();

        final Map<String, PropertyType> propertyTypeMap = serviceManager.getService(ProfileService.class).getTargetPropertyTypes("profiles")
                .stream().collect(Collectors.toMap(PropertyType::getItemId, Function.identity()));

        filterAsMap.forEach((propertyName, propertyValue) -> {
            if (!"and".equals(propertyName) && !"or".equals(propertyName)) {
                doAddDynamicProfilePropertiesCondition(null, propertyName, propertyValue, CDPProfilePropertiesFilterInput.TYPE_NAME, propertyTypeMap, filterAsMap, subConditions);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void doAddDynamicProfilePropertiesCondition(
            final String path,
            final String propertyName,
            final Object propertyValue,
            final String typeName,
            final Map<String, PropertyType> propertyTypeMap,
            final Map<String, Object> filterAsMap,
            final List<Condition> subConditions) {
        if (propertyTypeMap.containsKey(propertyName) && "set".equals(propertyTypeMap.get(propertyName).getValueTypeId())) {
            final Map<String, Object> setFilterAsMap = (Map<String, Object>) filterAsMap.get(propertyName);

            final String setTypeName = StringUtils.capitalize(propertyName) + "FilterInput";

            setFilterAsMap.forEach((setPropertyName, setPropertyValue) -> {
                if ("set".equals(propertyTypeMap.get(propertyName).getValueTypeId())) {
                    final Map<String, PropertyType> childPropertyTypeMap = propertyTypeMap.get(propertyName).getChildPropertyTypes()
                            .stream()
                            .collect(Collectors.toMap(PropertyType::getItemId, Function.identity()));

                    final String setPropertyPath = path != null ? path + "." + propertyName : propertyName;

                    doAddDynamicProfilePropertiesCondition(setPropertyPath, setPropertyName, setPropertyValue, setTypeName, childPropertyTypeMap, setFilterAsMap, subConditions);
                } else {
                    subConditions.add(createDynamicProfilePropertyCondition(setPropertyName, setPropertyValue, setTypeName, path));
                }
            });
        } else {
            subConditions.add(createDynamicProfilePropertyCondition(propertyName, propertyValue, typeName, path));
        }
    }

    private Condition createDynamicProfilePropertyCondition(final String propertyName, final Object value, final String typeName, final String propertyPath) {
        final int index = propertyName.lastIndexOf("_");

        final String property = propertyName.substring(0, index);

        final String comparisonOperator = propertyName.substring(index + 1);

        if ("distance".equals(comparisonOperator) && value instanceof Map) {
            Map<String, Object> distanceFilter = (Map<String, Object>) value;

            ConditionBuilder builder = ConditionBuilder.create(getConditionType(conditionTypeId))
                    .property("properties" + (propertyPath != null ? "." + propertyPath : "") + "." + PropertyNameTranslator.translateFromGraphQLToUnomi(property))
                    .operator(ComparisonConditionTranslator.translateFromGraphQLToUnomi(comparisonOperator));

            final Object centerObj = distanceFilter.get("center");
            if (centerObj != null) {
                builder.parameter("center", ((GeoPoint) centerObj).asString());
            }

            final Object distanceObj = distanceFilter.get("distance");
            if (distanceObj != null) {
                builder.parameter("distance", distanceObj);
            }

            final Object unitObj = distanceFilter.get("unit");
            if (unitObj != null) {
                builder.parameter("unit", unitObj.toString().toLowerCase());
            }

            return builder.build();
        } else {

            final String propertyValueType = PropertyValueTypeHelper.getPropertyValueParameterForInputType(typeName, propertyName, environment);

            return propertyCondition(
                    "properties" + (propertyPath != null ? "." + propertyPath : "") + "." + PropertyNameTranslator.translateFromGraphQLToUnomi(property),
                    ComparisonConditionTranslator.translateFromGraphQLToUnomi(comparisonOperator),
                    propertyValueType,
                    value);
        }
    }

    @SuppressWarnings("unchecked")
    private Condition pastEventsCondition(final CDPProfileEventsFilterInput filterInput, final Map<String, Object> filterInputAsMap) {
        boolean notEmpty = false;
        final ConditionBuilder pastEventConditionBuilder = ConditionBuilder.create(getConditionType("pastEventCondition"));

        if (filterInput.getEventFilter() != null) {
            final Map<String, Object> eventFilterInputAsMap = (Map<String, Object>) filterInputAsMap.get("eventFilter");

            final Condition eventFilterCondition = EventConditionFactory.get(environment).eventFilterInputCondition(filterInput.getEventFilter(), eventFilterInputAsMap);

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
