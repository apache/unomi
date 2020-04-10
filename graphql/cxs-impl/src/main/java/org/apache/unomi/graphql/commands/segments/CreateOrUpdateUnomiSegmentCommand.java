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
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.graphql.types.output.UnomiSegment;
import org.apache.unomi.graphql.types.input.UnomiSegmentInput;
import org.apache.unomi.graphql.utils.GraphQLObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CreateOrUpdateUnomiSegmentCommand extends BaseCreateOrUpdateSegmentCommand<UnomiSegmentInput, UnomiSegment> {

    private final UnomiSegmentInput segmentInput;

    public CreateOrUpdateUnomiSegmentCommand(final Builder builder) {
        super(builder);

        this.segmentInput = builder.getSegmentInput();
    }

    @Override
    public UnomiSegment execute() {
        final SegmentService segmentService = serviceManager.getSegmentService();

        Segment segment = preparedSegmentWithoutCondition(segmentInput);

        final Condition condition = GraphQLObjectMapper.getInstance().convertValue(segmentInput.getCondition(), Condition.class);

        decorateCondition(condition);

        segment.setCondition(condition);

        segmentService.setSegmentDefinition(segment);

        return new UnomiSegment(segment);
    }

    @SuppressWarnings("unchecked")
    private Condition decorateCondition(final Condition condition) {
        condition.setConditionType(serviceManager.getDefinitionsService().getConditionType(condition.getConditionTypeId()));

        if (condition.containsParameter("subConditions")) {
            final List<LinkedHashMap<String, Object>> subConditions = (List<LinkedHashMap<String, Object>>) condition.getParameter("subConditions");

            final List<Condition> subConditionDecorators = subConditions.stream()
                    .map(subConditionAsMap -> {
                        final Condition subCondition = GraphQLObjectMapper.getInstance().convertValue((Object) subConditionAsMap, Condition.class);

                        return decorateCondition(subCondition);
                    }).collect(Collectors.toList());

            condition.setParameter("subConditions", subConditionDecorators);
        }

        return condition;
    }

    public static Builder create(final UnomiSegmentInput segmentInput) {
        return new Builder(segmentInput);
    }

    public static class Builder extends BaseCreateOrUpdateSegmentCommand.Builder<UnomiSegmentInput, Builder> {

        public Builder(UnomiSegmentInput segmentInput) {
            super(segmentInput);
        }

        @Override
        public void validate() {
            super.validate();

            Objects.requireNonNull(getSegmentInput().getCondition(), "The condition field can not be null");
        }

        public CreateOrUpdateUnomiSegmentCommand build() {
            validate();

            return new CreateOrUpdateUnomiSegmentCommand(this);
        }

    }

}
