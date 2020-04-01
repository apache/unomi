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
package org.apache.unomi.graphql.fetchers.segment;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.types.output.CDPInterestFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class SegmentProfileInterestsDataFetcher implements DataFetcher<Object> {

    private final Boolean interestName;

    private final String comparisonOperator;

    private SegmentProfileInterestsDataFetcher(final Builder builder) {
        this.interestName = builder.interestName;
        this.comparisonOperator = builder.comparisonOperator;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object get(final DataFetchingEnvironment environment) throws Exception {
        final Stream<Condition> conditionStream = getSubConditions(environment).stream()
                .filter(condition -> "booleanCondition".equals(condition.getConditionTypeId())
                        && "and".equals(condition.getParameter("operator"))
                        && Objects.nonNull(condition.getParameter("subConditions")))
                .flatMap(condition -> ((ArrayList<Condition>) condition.getParameter("subConditions")).stream());

        if (interestName != null && interestName) {
            final Optional<Condition> interestCondition = conditionStream
                    .filter(condition -> "profilePropertyCondition".equals(condition.getConditionTypeId())
                            && Objects.nonNull(condition.getParameter("propertyName"))
                            && condition.getParameter("propertyName").toString().startsWith("properties.interests."))
                    .findFirst();

            if (interestCondition.isPresent()) {
                return interestCondition.get()
                        .getParameter("propertyName").toString().replaceAll("properties.interests.", "");
            }
        } else {
            final Optional<Condition> interestCondition = conditionStream
                    .filter(condition -> "profilePropertyCondition".equals(condition.getConditionTypeId())
                            && condition.getParameter("propertyName").toString().startsWith("properties.interests.")
                            && condition.getParameter("comparisonOperator").toString().equals(comparisonOperator))
                    .findFirst();

            if (interestCondition.isPresent()) {
                return interestCondition.get().getParameter("propertyValueInteger");
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Condition> getSubConditions(final DataFetchingEnvironment environment) {
        final CDPInterestFilter source = environment.getSource();

        final List<Condition> subConditions = (List<Condition>) source.getSegmentCondition().getParameter("subConditions");

        if (subConditions == null || subConditions.isEmpty()) {
            return Collections.emptyList();
        }

        return subConditions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Boolean interestName;

        private String comparisonOperator;

        private Builder() {
        }

        public Builder setInterestName(Boolean interestName) {
            this.interestName = interestName;
            return this;
        }

        public Builder setComparisonOperator(String comparisonOperator) {
            this.comparisonOperator = comparisonOperator;
            return this;
        }

        public SegmentProfileInterestsDataFetcher build() {
            return new SegmentProfileInterestsDataFetcher(this);
        }

    }

}
