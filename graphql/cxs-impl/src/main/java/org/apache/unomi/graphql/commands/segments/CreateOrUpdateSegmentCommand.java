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
package org.apache.unomi.graphql.commands.segments;

import com.google.common.base.Strings;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLInputObjectType;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.graphql.commands.BaseCommand;
import org.apache.unomi.graphql.schema.ComparisonConditionTranslator;
import org.apache.unomi.graphql.schema.PropertyNameTranslator;
import org.apache.unomi.graphql.schema.PropertyValueTypeHelper;
import org.apache.unomi.graphql.types.input.CDPConsentUpdateEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPInterestFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileEventsFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfilePropertiesFilterInput;
import org.apache.unomi.graphql.types.input.CDPSegmentInput;
import org.apache.unomi.graphql.types.input.CDPSessionEventFilterInput;
import org.apache.unomi.graphql.types.output.CDPSegment;
import org.apache.unomi.graphql.utils.ConditionBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.unomi.graphql.CDPGraphQLConstants.SEGMENT_ARGUMENT_NAME;

public class CreateOrUpdateSegmentCommand extends BaseCommand<CDPSegment> {

    private final CDPSegmentInput segmentInput;

    private final DataFetchingEnvironment environment;

    private final ConditionType profileSegmentConditionType;

    private final ConditionType profilePropertyConditionType;

    private final ConditionType booleanConditionType;

    private final ConditionType profileUserListConditionType;

    private final ConditionType eventPropertyConditionType;

    private final ConditionType pastEventConditionType;

    private final ConditionType notConditionType;

    private CreateOrUpdateSegmentCommand(Builder builder) {
        super(builder);

        this.segmentInput = builder.segmentInput;
        this.environment = builder.environment;

        final DefinitionsService definitionsService = serviceManager.getDefinitionsService();

        this.profileSegmentConditionType = definitionsService.getConditionType("profileSegmentCondition");
        this.profilePropertyConditionType = definitionsService.getConditionType("profilePropertyCondition");
        this.booleanConditionType = definitionsService.getConditionType("booleanCondition");
        this.profileUserListConditionType = definitionsService.getConditionType("profileUserListCondition");
        this.eventPropertyConditionType = definitionsService.getConditionType("eventPropertyCondition");
        this.pastEventConditionType = definitionsService.getConditionType("pastEventCondition");
        this.notConditionType = definitionsService.getConditionType("notCondition");
    }

    @Override
    public CDPSegment execute() {
        final SegmentService segmentService = serviceManager.getSegmentService();

        final String segmentId = Strings.isNullOrEmpty(segmentInput.getId())
                ? segmentInput.getName()
                : segmentInput.getId();

        Segment segment = segmentService.getSegmentDefinition(segmentId);

        if (segment == null) {
            segment = new Segment();

            segment.setItemType(Segment.ITEM_TYPE);
            segment.setMetadata(createMedata(segmentId));
        } else {
            if (segment.getMetadata() == null) {
                segment.setMetadata(new Metadata());
            }

            segment.setItemId(segmentId);
            segment.getMetadata().setId(segmentId);
            segment.getMetadata().setName(segmentInput.getName());
            segment.getMetadata().setScope(segmentInput.getView());
        }

        final Condition condition = createSegmentCondition();
        segment.setCondition(condition);

        segmentService.setSegmentDefinition(segment);

        return new CDPSegment(segment);
    }

    private Metadata createMedata(final String segmentId) {
        final Metadata metadata = new Metadata();

        metadata.setId(segmentId);
        metadata.setName(segmentInput.getName());
        metadata.setScope(segmentInput.getView());

        return metadata;
    }

    private List<Condition> createSubCondition() {
        final CDPProfileFilterInput filterInput = segmentInput.getProfiles();

        if (filterInput == null) {
            return Collections.emptyList();
        }

        final List<Condition> conditions = new ArrayList<>();

        final Condition segmentsContainsCondition = createSegmentsContainsCondition(filterInput.getSegments_contains());
        if (segmentsContainsCondition != null) {
            conditions.add(segmentsContainsCondition);
        }

        final Condition profileIDsContainsCondition = createProfileIDsContainsCondition(filterInput.getProfileIDs_contains());
        if (profileIDsContainsCondition != null) {
            conditions.add(profileIDsContainsCondition);
        }

        final Condition listsContainsCondition = createListsContains(filterInput.getLists_contains());
        if (listsContainsCondition != null) {
            conditions.add(listsContainsCondition);
        }

        final Condition consentsContainsCondition = createConsentsContains(filterInput.getConsents_contains());
        if (consentsContainsCondition != null) {
            conditions.add(consentsContainsCondition);
        }

        final Condition profilePropertiesCondition = createProfilePropertiesCondition();
        if (profilePropertiesCondition != null) {
            conditions.add(profilePropertiesCondition);
        }

        final Condition profileInterestCondition = createProfileInterestCondition(filterInput.getInterests());
        if (profileInterestCondition != null) {
            conditions.add(profileInterestCondition);
        }

        final Condition profileEventsCondition = createProfileEventsCondition(filterInput.getEvents());
        if (profileEventsCondition != null) {
            conditions.add(profileEventsCondition);
        }

        return conditions;
    }

    private Condition createSegmentsContainsCondition(final List<String> segmentsContains) {
        if (segmentsContains == null || segmentsContains.isEmpty()) {
            return null;
        }

        return ConditionBuilder.builder(profileSegmentConditionType)
                .setParameter("segments", segmentsContains)
                .setParameter("matchType", "in").build();
    }

    private Condition createProfileIDsContainsCondition(final List<String> profileIDsContains) {
        if (profileIDsContains == null || profileIDsContains.isEmpty()) {
            return null;
        }

        final List<Condition> subConditions = profileIDsContains.stream()
                .map(profileID -> ConditionBuilder.builder(profilePropertyConditionType)
                        .setPropertyName("itemId")
                        .setComparisonOperator("contains")
                        .setPropertyValue(profileID)
                        .build()
                ).collect(Collectors.toList());

        return ConditionBuilder.builder(booleanConditionType).buildBooleanCondition("or", subConditions);
    }

    private Condition createListsContains(final List<String> listsContains) {
        if (listsContains == null || listsContains.isEmpty()) {
            return null;
        }

        return ConditionBuilder.builder(profileUserListConditionType)
                .setParameter("lists", listsContains)
                .setParameter("matchType", "in").build();
    }

    private Condition createConsentsContains(final List<String> consentsContains) {
        if (consentsContains == null || consentsContains.isEmpty()) {
            return null;
        }

        final List<Condition> subConditions = new ArrayList<>();

        for (final String value : consentsContains) {
            final String[] splittedValue = value.split("/", -1);

            final Condition scopeCondition = ConditionBuilder.builder(profilePropertyConditionType)
                    .setPropertyName("consents." + value + ".scope")
                    .setComparisonOperator("equals")
                    .setPropertyValue(splittedValue[0]).build();

            final Condition typeIdentifierCondition = ConditionBuilder.builder(profilePropertyConditionType)
                    .setPropertyName("consents." + value + ".typeIdentifier")
                    .setComparisonOperator("equals")
                    .setPropertyValue(splittedValue[1]).build();

            final Condition statusCondition = ConditionBuilder.builder(profilePropertyConditionType)
                    .setPropertyName("consents." + value + ".status")
                    .setComparisonOperator("equals")
                    .setPropertyValue("GRANTED").build();

            subConditions.add(ConditionBuilder.builder(booleanConditionType)
                    .buildBooleanCondition("and", Arrays.asList(scopeCondition, typeIdentifierCondition, statusCondition)));
        }

        return ConditionBuilder.builder(booleanConditionType).buildBooleanCondition("or", subConditions);
    }

    @SuppressWarnings("unchecked")
    private Condition createProfilePropertiesCondition() {
        final Map<String, Object> segmentArgumentAsMap = environment.getArgument(SEGMENT_ARGUMENT_NAME);

        if (!segmentArgumentAsMap.containsKey("profiles")) {
            return null;
        }

        final Map<String, Object> profilesAsMap = (Map<String, Object>) segmentArgumentAsMap.get("profiles");

        if (!profilesAsMap.containsKey("properties")) {
            return null;
        }

        final Map<String, Object> profileFilterPropertiesAsMap = (Map<String, Object>) profilesAsMap.get("properties");

        return createProfilePropertiesCondition(profileFilterPropertiesAsMap);
    }

    @SuppressWarnings("unchecked")
    private Condition createProfilePropertiesCondition(final Map<String, Object> profilePropertiesFilterInput) {
        if (profilePropertiesFilterInput == null || profilePropertiesFilterInput.isEmpty()) {
            return null;
        }

        final List<Condition> subConditions = new ArrayList<>();

        profilePropertiesFilterInput.forEach((propertyName, value) -> {
            if ("and".equals(propertyName) || "or".equals(propertyName)
                    && profilePropertiesFilterInput.get(propertyName) != null) {
                final List<Map<String, Object>> inputFilters = (List<Map<String, Object>>) profilePropertiesFilterInput.get(propertyName);
                createConditionBasedOnFilterWithSubFilters(
                        subConditions, inputFilters, this::createProfilePropertiesCondition, propertyName);
            } else {
                final String[] propertyFilter = propertyName.split("_", -1);

                final String propertyValueType =
                        PropertyValueTypeHelper.getPropertyValueParameterForInputType(
                                CDPProfilePropertiesFilterInput.TYPE_NAME, propertyName, environment);

                subConditions.add(ConditionBuilder.builder(profilePropertyConditionType)
                        .setPropertyName("properties." + PropertyNameTranslator.translateFromGraphQLToUnomi(propertyFilter[0]))
                        .setComparisonOperator(ComparisonConditionTranslator.translateFromGraphQLToUnomi(propertyFilter[1]))
                        .setParameter(propertyValueType, value).build());
            }
        });

        return ConditionBuilder.builder(booleanConditionType).buildBooleanCondition("and", subConditions);
    }

    private Condition createProfileInterestCondition(final CDPInterestFilterInput interestFilterInput) {
        if (interestFilterInput == null) {
            return null;
        }

        final String propertyName = "properties.interests." + interestFilterInput.getTopic_equals();

        final List<Condition> subConditions = new ArrayList<>();

        if (interestFilterInput.getScore_equals() != null) {
            subConditions.add(ConditionBuilder.builder(profilePropertyConditionType)
                    .setPropertyName(propertyName)
                    .setComparisonOperator("equals")
                    .setPropertyValueInteger(interestFilterInput.getScore_equals()).build());
        }

        if (interestFilterInput.getScore_gte() != null) {
            subConditions.add(ConditionBuilder.builder(profilePropertyConditionType)
                    .setPropertyName(propertyName)
                    .setComparisonOperator("greaterThanOrEqualTo")
                    .setPropertyValueInteger(interestFilterInput.getScore_gte()).build());
        }

        if (interestFilterInput.getScore_gt() != null) {
            subConditions.add(ConditionBuilder.builder(profilePropertyConditionType)
                    .setPropertyName(propertyName)
                    .setComparisonOperator("greaterThan")
                    .setPropertyValueInteger(interestFilterInput.getScore_gt()).build());
        }

        if (interestFilterInput.getScore_lte() != null) {
            subConditions.add(ConditionBuilder.builder(profilePropertyConditionType)
                    .setPropertyName(propertyName)
                    .setComparisonOperator("lessThanOrEqualTo")
                    .setPropertyValueInteger(interestFilterInput.getScore_lte()).build());
        }

        if (interestFilterInput.getScore_lt() != null) {
            subConditions.add(ConditionBuilder.builder(profilePropertyConditionType)
                    .setPropertyName(propertyName)
                    .setComparisonOperator("lessThan")
                    .setPropertyValueInteger(interestFilterInput.getScore_lt()).build());
        }

        createConditionBasedOnFilterWithSubFilters(
                subConditions, interestFilterInput.getAnd(), this::createProfileInterestCondition, "and");

        createConditionBasedOnFilterWithSubFilters(
                subConditions, interestFilterInput.getOr(), this::createProfileInterestCondition, "or");

        return ConditionBuilder.builder(booleanConditionType).buildBooleanCondition("and", subConditions);
    }


    private Condition createEventPropertyCondition(final CDPEventFilterInput eventFilterInput) {
        final List<Condition> subConditions = new ArrayList<>();

        if (!Strings.isNullOrEmpty(eventFilterInput.getCdp_sourceID_equals())) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("source.itemId")
                    .setComparisonOperator("equals")
                    .setPropertyValue(eventFilterInput.getCdp_sourceID_equals()).build());
        }

        if (!Strings.isNullOrEmpty(eventFilterInput.getCdp_profileID_equals())) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("profileId")
                    .setComparisonOperator("equals")
                    .setPropertyValue(eventFilterInput.getCdp_profileID_equals()).build());
        }

        if (!Strings.isNullOrEmpty(eventFilterInput.getId_equals())) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("itemId")
                    .setComparisonOperator("equals")
                    .setPropertyValue(eventFilterInput.getId_equals())
                    .build());
        }

        if (eventFilterInput.getCdp_timestamp_equals() != null) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("timeStamp")
                    .setComparisonOperator("equals")
                    .setPropertyValueDate(eventFilterInput.getCdp_timestamp_equals())
                    .build());
        }

        if (eventFilterInput.getCdp_timestamp_gt() != null) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("timeStamp")
                    .setComparisonOperator("greaterThan")
                    .setPropertyValueDate(eventFilterInput.getCdp_timestamp_gt())
                    .build());
        }

        if (eventFilterInput.getCdp_timestamp_gte() != null) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("timeStamp")
                    .setComparisonOperator("greaterThanOrEqualTo")
                    .setPropertyValueDate(eventFilterInput.getCdp_timestamp_gte())
                    .build());
        }

        if (eventFilterInput.getCdp_timestamp_lt() != null) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("timeStamp")
                    .setComparisonOperator("lessThan")
                    .setPropertyValueDate(eventFilterInput.getCdp_timestamp_lt())
                    .build());
        }

        if (eventFilterInput.getCdp_timestamp_lte() != null) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("timeStamp")
                    .setComparisonOperator("lessThanOrEqualTo")
                    .setPropertyValueDate(eventFilterInput.getCdp_timestamp_lte()).build());
        }

        if (eventFilterInput.getCdp_consentUpdateEvent() != null) {
            subConditions.add(createCdpConsentUpdateEventCondition(eventFilterInput.getCdp_consentUpdateEvent()));
        }

        if (eventFilterInput.getCdp_sessionEvent() != null) {
            subConditions.add(createCdpSessionEventCondition(eventFilterInput.getCdp_sessionEvent()));
        }

        createConditionBasedOnFilterWithSubFilters(
                subConditions, eventFilterInput.getAnd(), this::createEventPropertyCondition, "and");

        createConditionBasedOnFilterWithSubFilters(
                subConditions, eventFilterInput.getOr(), this::createEventPropertyCondition, "or");

        return ConditionBuilder.builder(booleanConditionType).buildBooleanCondition("and", subConditions);
    }

    public Condition createCdpConsentUpdateEventCondition(final CDPConsentUpdateEventFilterInput eventFilterInput) {
        final GraphQLInputObjectType inputObjectType =
                (GraphQLInputObjectType) environment.getGraphQLSchema().getType(CDPConsentUpdateEventFilterInput.TYPE_NAME);

        final List<Condition> subConditions = new ArrayList<>();

        if (eventFilterInput.getStatus_equals() != null) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("status")
                    .setComparisonOperator("equals")
                    .setPropertyValue(eventFilterInput.getStatus_equals()).build());
        }

        return ConditionBuilder.builder(booleanConditionType).buildBooleanCondition("and", subConditions);
    }

    public Condition createCdpSessionEventCondition(final CDPSessionEventFilterInput eventFilterInput) {
        final List<Condition> subConditions = new ArrayList<>();

        if (eventFilterInput.getState_equals() != null) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("properties.state")
                    .setComparisonOperator("equals")
                    .setPropertyValue(eventFilterInput.getState_equals().name()).build());
        }

        if (eventFilterInput.getUnomi_scope_equals() != null) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("properties.scope")
                    .setComparisonOperator("equals")
                    .setPropertyValue(eventFilterInput.getUnomi_scope_equals()).build());
        }

        if (eventFilterInput.getUnomi_sessionId_equals() != null) {
            subConditions.add(ConditionBuilder.builder(eventPropertyConditionType)
                    .setPropertyName("properties.sessionId")
                    .setComparisonOperator("equals")
                    .setPropertyValue(eventFilterInput.getUnomi_sessionId_equals()).build());
        }

        return ConditionBuilder.builder(booleanConditionType).buildBooleanCondition("and", subConditions);
    }

    final List<Condition> subConditions = new ArrayList<>();

    private Condition createProfileEventsCondition(final CDPProfileEventsFilterInput eventsFilterInput) {
        if (eventsFilterInput == null || eventsFilterInput.getEventFilter() == null) {
            return null;
        }

        final List<Condition> subConditions = new ArrayList<>();

        createConditionBasedOnFilterWithSubFilters(
                subConditions, eventsFilterInput.getAnd(), this::createProfileEventsCondition, "and");

        createConditionBasedOnFilterWithSubFilters(
                subConditions, eventsFilterInput.getOr(), this::createProfileEventsCondition, "or");

        if (eventsFilterInput.getNot() != null) {
            subConditions.add(ConditionBuilder.builder(notConditionType)
                    .setParameter("subCondition", createProfileEventsCondition(eventsFilterInput.getNot()))
                    .build());
        }

        final Condition eventCondition = createEventPropertyCondition(eventsFilterInput.getEventFilter());

        final Condition pastEventCondition = ConditionBuilder.builder(pastEventConditionType)
                .setParameter("minimumEventCount", eventsFilterInput.getMinimalCount())
                .setParameter("maximumEventCount", eventsFilterInput.getMaximalCount())
                .setParameter("eventCondition", eventCondition).build();

        subConditions.add(pastEventCondition);

        return ConditionBuilder.builder(booleanConditionType).buildBooleanCondition("and", subConditions);
    }

    private <INPUT> void createConditionBasedOnFilterWithSubFilters(
            final List<Condition> container, final List<INPUT> inputFilters, final Function<INPUT, Condition> function, final String operator) {
        if (inputFilters == null || inputFilters.isEmpty()) {
            return;
        }

        final List<Condition> subConditions = inputFilters.stream()
                .map(function)
                .collect(Collectors.toList());

        container.add(ConditionBuilder.builder(booleanConditionType).buildBooleanCondition(operator, subConditions));
    }

    private Condition createSegmentCondition() {
        final List<Condition> subConditions = createSubCondition();

        return ConditionBuilder.builder(booleanConditionType).buildBooleanCondition("and", subConditions);
    }

    public static Builder create(final CDPSegmentInput segmentInput, final DataFetchingEnvironment environment) {
        return new Builder(segmentInput, environment);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        private final CDPSegmentInput segmentInput;

        private final DataFetchingEnvironment environment;

        public Builder(CDPSegmentInput segmentInput, DataFetchingEnvironment environment) {
            this.segmentInput = segmentInput;
            this.environment = environment;
        }

        private void validate() {
            if (segmentInput == null) {
                throw new IllegalArgumentException();
            }
            if (Strings.isNullOrEmpty(segmentInput.getName())) {
                throw new IllegalArgumentException();
            }
            if (Strings.isNullOrEmpty(segmentInput.getView())) {
                throw new IllegalArgumentException();
            }
        }

        public CreateOrUpdateSegmentCommand build() {
            validate();

            return new CreateOrUpdateSegmentCommand(this);
        }

    }

}
