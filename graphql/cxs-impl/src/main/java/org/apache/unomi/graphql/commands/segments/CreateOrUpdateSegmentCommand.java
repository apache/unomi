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
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.graphql.commands.BaseCommand;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPProfileFilterInput;
import org.apache.unomi.graphql.types.input.CDPSegmentInput;
import org.apache.unomi.graphql.types.output.CDPSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        if (filterInput.getSegments_contains() != null && !filterInput.getSegments_contains().isEmpty()) {
            final Condition segmentsContainsCondition = new Condition();

            segmentsContainsCondition.setConditionType(serviceManager.getDefinitionsService().getConditionType("profileSegmentCondition"));
            segmentsContainsCondition.setParameter("segments", filterInput.getSegments_contains());
            segmentsContainsCondition.setParameter("matchType", "in");

            conditions.add(segmentsContainsCondition);
        }

        if (filterInput.getProfileIDs_contains() != null && !filterInput.getProfileIDs_contains().isEmpty()) {
            final Condition profileIDsContainsCondition =
                    createContainsCondition(filterInput.getProfileIDs_contains(), "profilePropertyCondition", "itemId");

            conditions.add(profileIDsContainsCondition);
        }

        if (filterInput.getLists_contains() != null && !filterInput.getLists_contains().isEmpty()) {
            final Condition listsContainsCondition = new Condition();

            listsContainsCondition.setConditionType(serviceManager.getDefinitionsService().getConditionType("profileUserListCondition"));
            listsContainsCondition.setParameter("lists", filterInput.getLists_contains());
            listsContainsCondition.setParameter("matchType", "in");

            conditions.add(listsContainsCondition);
        }

        return conditions;
    }

    private Condition createContainsCondition(List<String> values, String conditionId, String propertyName) {
        final Condition condition = new Condition();

        condition.setConditionType(serviceManager.getDefinitionsService().getConditionType("booleanCondition"));
        condition.setParameter("operator", "or");

        final List<Condition> subConditions = new ArrayList<>();

        for (String value : values) {
            final Condition subCondition = new Condition();

            subCondition.setConditionType(serviceManager.getDefinitionsService().getConditionType(conditionId));
            subCondition.setParameter("propertyName", propertyName);
            subCondition.setParameter("comparisonOperator", "contains");
            subCondition.setParameter("propertyValue", value);

            subConditions.add(subCondition);
        }
        condition.setParameter("subConditions", subConditions);

        return condition;
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
