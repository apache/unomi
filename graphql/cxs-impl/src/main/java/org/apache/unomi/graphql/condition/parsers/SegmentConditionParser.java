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
import org.apache.unomi.api.conditions.Condition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SegmentConditionParser {

    private static final Predicate<Condition> IS_BOOLEAN_CONDITION_TYPE =
            condition -> "booleanCondition".equals(condition.getConditionTypeId());

    private static final Predicate<Condition> IS_PROFILE_PROPERTY_CONDITION_TYPE =
            condition -> "profilePropertyCondition".equals(condition.getConditionTypeId());

    private static final Predicate<Condition> IS_PROFILE_SEGMENT_CONDITION_TYPE =
            condition -> "profileSegmentCondition".equals(condition.getConditionTypeId());

    private static final Predicate<Condition> IS_PAST_EVENT_CONDITION_TYPE =
            condition -> "pastEventCondition".equals(condition.getConditionTypeId());

    private static final Predicate<Condition> IS_PROFILE_USER_LIST_CONDITION_TYPE =
            condition -> "profileUserListCondition".equals(condition.getConditionTypeId());

    private final Condition segmentCondition;

    private final DataFetchingEnvironment environment;

    private Map<String, ConditionDecorator> conditionsContext = new LinkedHashMap<>();

    private Map<FilterType, List<ConditionDecorator>> groupedConditionsByFilterType = new LinkedHashMap<>();

    private Map<FilterType, String> rootConditionIdPerFilterType = new LinkedHashMap<>();

    public SegmentConditionParser(final Condition segmentCondition, final DataFetchingEnvironment environment) {
        this.segmentCondition = segmentCondition;
        this.environment = environment;
    }

    public Map<String, Object> parse() {
        populateConditionsContext(segmentCondition);

        groupConditionsByFilterType();

        findRootConditionsByFilterType();

        return doParse();
    }

    private Map<String, Object> doParse() {
        final Map<String, Object> dataHolder = new LinkedHashMap<>();

        rootConditionIdPerFilterType.forEach((filterType, conditionId) -> {
            final ConditionDecorator conditionDecorator = conditionsContext.get(conditionId);

            final List<Condition> conditionDecorators = groupedConditionsByFilterType.get(filterType).stream()
                    .map(ConditionDecorator::getCondition)
                    .collect(Collectors.toList());

            switch (filterType) {
                case CONSENTS_CONTAINS:
                    dataHolder.put(filterType.getValue(),
                            new SegmentProfileConsentsConditionParser(conditionDecorators).parse());
                    break;
                case SEGMENTS_CONTAINS:
                    dataHolder.put(filterType.getValue(),
                            new SegmentProfileSegmentsConditionParser(conditionDecorators).parse());
                    break;
                case PROFILE_IDS_CONTAINS:
                    dataHolder.put(filterType.getValue(),
                            new SegmentProfileIDsConditionParser(conditionDecorators).parse());
                    break;
                case LISTS_CONTAINS:
                    dataHolder.put(filterType.getValue(),
                            new SegmentProfileListConditionParser(conditionDecorators).parse());
                    break;
                case EVENTS:
                    dataHolder.put(filterType.getValue(),
                            new SegmentProfileEventsConditionParser(conditionDecorator.getCondition(), environment).parse());
                    break;
                case PROPERTIES:
                    dataHolder.put(filterType.getValue(),
                            new SegmentProfilePropertiesConditionParser(conditionDecorator.getCondition(), environment).parse());
                    break;
                case INTERESTS:
                    dataHolder.put(filterType.getValue(),
                            new SegmentProfileInterestsConditionParser(conditionDecorator.getCondition()).parse());
                    break;
                default: {
                    // do nothing
                }
            }
        });

        return dataHolder;
    }

    private void groupConditionsByFilterType() {
        conditionsContext.entrySet().stream()
                .filter(entry -> !IS_BOOLEAN_CONDITION_TYPE.test(entry.getValue().getCondition()))
                .forEach(entry -> {
                    final FilterType filterType = entry.getValue().getFilterType();

                    if (!groupedConditionsByFilterType.containsKey(filterType)) {
                        groupedConditionsByFilterType.put(filterType, new ArrayList<>());
                    }

                    groupedConditionsByFilterType.get(filterType).add(entry.getValue());
                });
    }

    private void findRootConditionsByFilterType() {
        groupedConditionsByFilterType.forEach((filterType, conditionDecorators) -> {
            if (conditionDecorators != null && !conditionDecorators.isEmpty()) {
                ConditionDecorator member = conditionDecorators.get(0);

                do {
                    if (member.getParentId() != null && conditionsContext.get(member.getParentId()).getParentId() != null) {
                        member = conditionsContext.get(member.getParentId());
                    }
                }
                while (conditionsContext.get(member.getParentId()).getParentId() != null);

                rootConditionIdPerFilterType.put(filterType, member.getId());
            }
        });
    }


    private void populateConditionsContext(final Condition condition) {
        populateConditionsContext(null, condition);
    }

    @SuppressWarnings("unchecked")
    private void populateConditionsContext(final ConditionDecorator parentDecorator, final Condition condition) {
        final ConditionDecorator conditionDecorator = createConditionDecorator(parentDecorator, condition);
        conditionsContext.put(conditionDecorator.getId(), conditionDecorator);

        if (IS_BOOLEAN_CONDITION_TYPE.test(condition)) {
            final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");

            conditionDecorator.setSubConditions(subConditions);

            subConditions.forEach(subCondition -> populateConditionsContext(conditionDecorator, subCondition));
        }
    }

    private ConditionDecorator createConditionDecorator(final ConditionDecorator parentDecorator, final Condition condition) {
        final ConditionDecorator decorator = new ConditionDecorator();

        decorator.setId(UUID.randomUUID().toString());
        decorator.setCondition(condition);

        if (parentDecorator != null) {
            decorator.setParentId(parentDecorator.getId());
        }

        if (IS_BOOLEAN_CONDITION_TYPE.test(condition)) {
            decorator.setOperator(condition.getParameter("operator").toString());

            return decorator;
        }

        if (IS_PROFILE_PROPERTY_CONDITION_TYPE.test(condition)) {
            final Object propertyName = condition.getParameter("propertyName");

            if (propertyName != null) {
                if (propertyName.toString().startsWith("properties.interests.")) {
                    decorator.setFilterType(FilterType.INTERESTS);
                } else if (propertyName.toString().equals("itemId")) {
                    decorator.setFilterType(FilterType.PROFILE_IDS_CONTAINS);
                } else if (propertyName.toString().startsWith("consents.")) {
                    decorator.setFilterType(FilterType.CONSENTS_CONTAINS);
                } else {
                    decorator.setFilterType(FilterType.PROPERTIES);
                }
            }
        } else if (IS_PROFILE_SEGMENT_CONDITION_TYPE.test(condition)) {
            decorator.setFilterType(FilterType.SEGMENTS_CONTAINS);
        } else if (IS_PAST_EVENT_CONDITION_TYPE.test(condition)) {
            decorator.setFilterType(FilterType.EVENTS);
        } else if (IS_PROFILE_USER_LIST_CONDITION_TYPE.test(condition)) {
            decorator.setFilterType(FilterType.LISTS_CONTAINS);
        } else {
            decorator.setFilterType(FilterType.UNKNOWN);
        }

        return decorator;
    }

    private class ConditionDecorator {

        private String id;

        private String parentId;

        private String operator;

        private FilterType filterType;

        private Condition condition;

        private List<Condition> subConditions;

        String getId() {
            return id;
        }

        ConditionDecorator setId(String id) {
            this.id = id;
            return this;
        }

        String getParentId() {
            return parentId;
        }

        ConditionDecorator setParentId(String parentId) {
            this.parentId = parentId;
            return this;
        }

        String getOperator() {
            return operator;
        }

        ConditionDecorator setOperator(String operator) {
            this.operator = operator;
            return this;
        }

        FilterType getFilterType() {
            return filterType;
        }

        ConditionDecorator setFilterType(FilterType filterType) {
            this.filterType = filterType;
            return this;
        }

        Condition getCondition() {
            return condition;
        }

        ConditionDecorator setCondition(Condition condition) {
            this.condition = condition;
            return this;
        }

        List<Condition> getSubConditions() {
            return subConditions;
        }

        ConditionDecorator setSubConditions(List<Condition> subConditions) {
            this.subConditions = subConditions;
            return this;
        }

    }

    private enum FilterType {

        CONSENTS_CONTAINS("consents_contains"),

        LISTS_CONTAINS("lists_contains"),

        PROFILE_IDS_CONTAINS("profileIDs_contains"),

        SEGMENTS_CONTAINS("segments_contains"),

        PROPERTIES("properties"),

        EVENTS("events"),

        INTERESTS("interests"),

        UNKNOWN("unknown");

        private String value;

        FilterType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

    }

}
