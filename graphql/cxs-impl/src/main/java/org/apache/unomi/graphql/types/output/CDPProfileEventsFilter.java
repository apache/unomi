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
package org.apache.unomi.graphql.types.output;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CDPProfileEventsFilter {

    private final Condition segmentCondition;

    public CDPProfileEventsFilter(final Condition segmentCondition) {
        this.segmentCondition = segmentCondition;
    }

    @GraphQLField
    public List<CDPProfileEventsFilter> and(final DataFetchingEnvironment environment) {
        return Collections.emptyList();
    }

    @GraphQLField
    public List<CDPProfileEventsFilter> or(final DataFetchingEnvironment environment) {
        return Collections.emptyList();
    }

    @GraphQLField
    public CDPProfileEventsFilter not(final DataFetchingEnvironment environment) {
        return null;
    }

    @GraphQLField
    public Integer minimalCount(final DataFetchingEnvironment environment) {
        final Optional<Condition> conditionOp = getSubConditions().stream()
                .filter(condition -> "pastEventCondition".equals(condition.getConditionTypeId())
                        && Objects.nonNull(condition.getParameter("minimumEventCount"))
                ).findFirst();

        return conditionOp.map(condition -> (Integer) condition.getParameter("minimumEventCount")).orElse(null);
    }

    @GraphQLField
    public Integer maximalCount(final DataFetchingEnvironment environment) {
        final Optional<Condition> conditionOp = getSubConditions().stream()
                .filter(condition -> "pastEventCondition".equals(condition.getConditionTypeId())
                        && Objects.nonNull(condition.getParameter("maximumEventCount"))
                ).findFirst();

        return conditionOp.map(condition -> (Integer) condition.getParameter("maximumEventCount")).orElse(null);
    }

    @GraphQLField
    public CDPEventFilter eventFilter() {
        final Optional<Condition> conditionOp = getSubConditions().stream()
                .filter(condition -> "pastEventCondition".equals(condition.getConditionTypeId())
                        && Objects.nonNull(condition.getParameter("eventCondition"))
                ).findFirst();

        return conditionOp.map(
                c -> new CDPEventFilter((Condition) conditionOp.get().getParameter("eventCondition"))
        ).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Condition> getSubConditions() {
        final List<Condition> subConditions = (List<Condition>) segmentCondition.getParameter("subConditions");

        if (subConditions == null || subConditions.isEmpty()) {
            return Collections.emptyList();
        }

        return subConditions;
    }

}
