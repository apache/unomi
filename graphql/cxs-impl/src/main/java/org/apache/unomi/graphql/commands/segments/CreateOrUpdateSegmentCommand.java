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

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.graphql.condition.factories.ProfileConditionFactory;
import org.apache.unomi.graphql.types.input.CDPSegmentInput;
import org.apache.unomi.graphql.types.output.CDPSegment;

import java.util.Map;
import java.util.Objects;

import static org.apache.unomi.graphql.CDPGraphQLConstants.SEGMENT_ARGUMENT_NAME;

public class CreateOrUpdateSegmentCommand extends BaseCreateOrUpdateSegmentCommand<CDPSegmentInput, CDPSegment> {

    private final CDPSegmentInput segmentInput;

    private CreateOrUpdateSegmentCommand(Builder builder) {
        super(builder);

        this.segmentInput = builder.getSegmentInput();
    }

    @Override
    public CDPSegment execute() {
        Segment segment = preparedSegmentWithoutCondition(segmentInput);

        Map<String, Object> profileFilterAsMap = null;
        final Map<String, Object> segmentArgumentAsMap = environment.getArgument(SEGMENT_ARGUMENT_NAME);
        if (segmentArgumentAsMap != null) {
            profileFilterAsMap = (Map<String, Object>) segmentArgumentAsMap.get("profiles");
        }

        final Condition condition = ProfileConditionFactory.get(environment).profileFilterInputCondition(segmentInput.getProfiles(), profileFilterAsMap);

        segment.setCondition(condition);

        serviceManager.getSegmentService().setSegmentDefinition(segment);

        return new CDPSegment(segment);
    }

    public static Builder create(final CDPSegmentInput segmentInput) {
        return new Builder(segmentInput);
    }

    public static class Builder extends BaseCreateOrUpdateSegmentCommand.Builder<CDPSegmentInput, Builder> {

        public Builder(CDPSegmentInput segmentInput) {
            super(segmentInput);
        }

        @Override
        public void validate() {
            super.validate();

            Objects.requireNonNull(getSegmentInput().getProfiles(), "The profiles field can not be null");
        }

        public CreateOrUpdateSegmentCommand build() {
            validate();

            return new CreateOrUpdateSegmentCommand(this);
        }

    }

}
