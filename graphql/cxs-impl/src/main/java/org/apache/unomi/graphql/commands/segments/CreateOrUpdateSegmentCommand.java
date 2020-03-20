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
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.graphql.commands.BaseCommand;
import org.apache.unomi.graphql.schema.ComparisonConditionTranslator;
import org.apache.unomi.graphql.schema.PropertyNameTranslator;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPInterestFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileEventsFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileFilterInput;
import org.apache.unomi.graphql.types.input.CDPSegmentInput;
import org.apache.unomi.graphql.types.output.CDPSegment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.unomi.graphql.CDPGraphQLConstants.SEGMENT_ARGUMENT_NAME;

public class CreateOrUpdateSegmentCommand extends BaseCommand<CDPSegment> {

    private final CDPSegmentInput segmentInput;

    private final DataFetchingEnvironment environment;

    private CreateOrUpdateSegmentCommand(Builder builder) {
        super(builder);

        this.segmentInput = builder.segmentInput;
        this.environment = builder.environment;
    }

    @Override
    public CDPSegment execute() {
        final ServiceManager serviceManager = environment.getContext();

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
        if (profileIDsContainsCondition != null) {
            conditions.add(profileEventsCondition);
        }

        return conditions;
    }

    private Condition createSegmentsContainsCondition(final List<String> segmentsContains) {
        if (segmentsContains == null || segmentsContains.isEmpty()) {
            return null;
        }

        final Condition condition = new Condition();

        condition.setConditionType(serviceManager.getDefinitionsService().getConditionType("profileSegmentCondition"));
        condition.setParameter("segments", segmentsContains);
        condition.setParameter("matchType", "in");

        return condition;
    }

    private Condition createProfileIDsContainsCondition(final List<String> profileIDsContains) {
        if (profileIDsContains == null || profileIDsContains.isEmpty()) {
            return null;
        }

        final List<Condition> subConditions = profileIDsContains.stream()
                .map(profileID -> {
                    final Condition condition = new Condition();

                    condition.setConditionType(serviceManager.getDefinitionsService().getConditionType("profilePropertyCondition"));
                    condition.setParameter("propertyName", "itemId");
                    condition.setParameter("comparisonOperator", "contains");
                    condition.setParameter("propertyValue", profileID);

                    return condition;
                }).collect(Collectors.toList());

        final Condition condition = new Condition();

        condition.setConditionType(serviceManager.getDefinitionsService().getConditionType("booleanCondition"));
        condition.setParameter("operator", "or");
        condition.setParameter("subConditions", subConditions);

        return condition;
    }

    private Condition createListsContains(final List<String> listsContains) {
        if (listsContains == null || listsContains.isEmpty()) {
            return null;
        }

        final Condition condition = new Condition();

        condition.setConditionType(serviceManager.getDefinitionsService().getConditionType("profileUserListCondition"));
        condition.setParameter("lists", listsContains);
        condition.setParameter("matchType", "in");

        return condition;
    }

    private Condition createConsentsContains(final List<String> consentsContains) {
        if (consentsContains == null || consentsContains.isEmpty()) {
            return null;
        }

        final List<Condition> rootSubConditions = new ArrayList<>();

        final ConditionType profilePropertyConditionType =
                serviceManager.getDefinitionsService().getConditionType("profilePropertyCondition");

        for (String value : consentsContains) {
            final String[] splittedValue = value.split("/", -1);

            final Condition scopeCondition = new Condition();

            scopeCondition.setConditionType(profilePropertyConditionType);
            scopeCondition.setParameter("propertyName", "consents." + value + ".scope");
            scopeCondition.setParameter("comparisonOperator", "equals");
            scopeCondition.setParameter("propertyValue", splittedValue[0]);

            final Condition typeIdentifierCondition = new Condition();

            typeIdentifierCondition.setConditionType(profilePropertyConditionType);
            typeIdentifierCondition.setParameter("propertyName", "consents." + value + ".typeIdentifier");
            typeIdentifierCondition.setParameter("comparisonOperator", "equals");
            typeIdentifierCondition.setParameter("propertyValue", splittedValue[1]);

            final Condition statusCondition = new Condition();

            statusCondition.setConditionType(profilePropertyConditionType);
            statusCondition.setParameter("propertyName", "consents." + value + ".status");
            statusCondition.setParameter("comparisonOperator", "equals");
            statusCondition.setParameter("propertyValue", "GRANTED");

            final Condition consentSubCondition = new Condition();

            consentSubCondition.setConditionType(profilePropertyConditionType);
            consentSubCondition.setParameter("operator", "and");
            consentSubCondition.setParameter("subConditions", Arrays.asList(scopeCondition, typeIdentifierCondition, statusCondition));

            rootSubConditions.add(consentSubCondition);
        }

        final Condition condition = new Condition();

        condition.setConditionType(serviceManager.getDefinitionsService().getConditionType("booleanCondition"));
        condition.setParameter("operator", "or");
        condition.setParameter("subConditions", rootSubConditions);

        return condition;
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

        if (profileFilterPropertiesAsMap == null || profileFilterPropertiesAsMap.isEmpty()) {
            return null;
        }

        final ConditionType conditionType = serviceManager.getDefinitionsService().getConditionType("profilePropertyCondition");

        final List<Condition> subConditions = profileFilterPropertiesAsMap.entrySet().stream().map(entry -> {
            final String[] propertyFilter = entry.getKey().split("_", -1);

            final Condition propertyFilterCondition = new Condition();

            propertyFilterCondition.setConditionType(conditionType);
            propertyFilterCondition.setParameter("propertyName",
                    "properties." + PropertyNameTranslator.translateFromGraphQLToUnomi(propertyFilter[0]));
            propertyFilterCondition.setParameter("comparisonOperator",
                    ComparisonConditionTranslator.translateComparisonCondition(propertyFilter[1]));
            propertyFilterCondition.setParameter("propertyValue", entry.getValue());

            return propertyFilterCondition;
        }).collect(Collectors.toList());


        final Condition profilePropertiesCondition = new Condition();

        profilePropertiesCondition.setConditionType(serviceManager.getDefinitionsService().getConditionType("booleanCondition"));
        profilePropertiesCondition.setParameter("operator", "and");
        profilePropertiesCondition.setParameter("subConditions", subConditions);

        return profilePropertiesCondition;
    }

    private Condition createProfileInterestCondition(final CDPInterestFilterInput interestFilterInput) {
        if (interestFilterInput == null) {
            return null;
        }

        final ConditionType conditionType = serviceManager.getDefinitionsService().getConditionType("profilePropertyCondition");

        final String propertyName = "properties.interests." + interestFilterInput.getTopic_equals();

        final List<Condition> subConditions = new ArrayList<>();

        if (interestFilterInput.getScore_equals() != null) {
            final Condition condition = new Condition();

            condition.setConditionType(conditionType);

            condition.setParameter("propertyName", propertyName);
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValueInteger", interestFilterInput.getScore_equals());

            subConditions.add(condition);
        }

        if (interestFilterInput.getScore_gte() != null) {
            final Condition condition = new Condition();

            condition.setConditionType(conditionType);
            condition.setParameter("propertyName", propertyName);
            condition.setParameter("comparisonOperator", "greaterThanOrEqualTo");
            condition.setParameter("propertyValueInteger", interestFilterInput.getScore_gte());

            subConditions.add(condition);
        }

        if (interestFilterInput.getScore_gt() != null) {
            final Condition condition = new Condition();

            condition.setConditionType(conditionType);
            condition.setParameter("propertyName", propertyName);
            condition.setParameter("comparisonOperator", "greaterThan");
            condition.setParameter("propertyValueInteger", interestFilterInput.getScore_gt());

            subConditions.add(condition);
        }

        if (interestFilterInput.getScore_lte() != null) {
            final Condition condition = new Condition();

            condition.setConditionType(conditionType);
            condition.setParameter("propertyName", propertyName);
            condition.setParameter("comparisonOperator", "lessThanOrEqualTo");
            condition.setParameter("propertyValueInteger", interestFilterInput.getScore_lte());

            subConditions.add(condition);
        }

        if (interestFilterInput.getScore_lt() != null) {
            final Condition condition = new Condition();

            condition.setConditionType(conditionType);
            condition.setParameter("propertyName", propertyName);
            condition.setParameter("comparisonOperator", "lessThan");
            condition.setParameter("propertyValueInteger", interestFilterInput.getScore_lt());

            subConditions.add(condition);
        }

        final Condition condition = new Condition();

        condition.setConditionType(serviceManager.getDefinitionsService().getConditionType("booleanCondition"));
        condition.setParameter("operator", "and");
        condition.setParameter("subConditions", subConditions);

        return condition;
    }

    private Condition createProfileEventsCondition(final CDPProfileEventsFilterInput eventsFilterInput) {
        return null;
    }

    private Condition createSegmentCondition() {
        final Condition condition = new Condition();

        condition.setConditionType(serviceManager.getDefinitionsService().getConditionType("booleanCondition"));
        condition.setParameter("operator", "and");

        final List<Condition> subConditions = createSubCondition();
        condition.setParameter("subConditions", subConditions);

        return condition;
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
