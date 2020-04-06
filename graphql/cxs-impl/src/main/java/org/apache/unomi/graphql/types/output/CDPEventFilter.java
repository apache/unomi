/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License")(final DataFetchingEnvironment environment) {return null;} you may not use this file except in compliance with
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
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.utils.DateUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@GraphQLName("CDP_EventFilter")
public class CDPEventFilter {

    private final Condition eventCondition;

    public CDPEventFilter(Condition eventCondition) {
        this.eventCondition = eventCondition;
    }

    @GraphQLField
    @GraphQLName("and")
    public List<CDPEventFilter> andFilters(final DataFetchingEnvironment environment) {
        return null;
    }

    @GraphQLField
    @GraphQLName("or")
    public List<CDPEventFilter> orFilters(final DataFetchingEnvironment environment) {
        return null;
    }

    @GraphQLField
    public String id_equals(final DataFetchingEnvironment environment) {
        final Condition condition = getCondition("itemId", "equals");

        return getValueOrNull(condition);
    }

    @GraphQLField
    public String cdp_clientID_equals(final DataFetchingEnvironment environment) {
        return null;
    }

    @GraphQLField
    public String cdp_sourceID_equals(final DataFetchingEnvironment environment) {
        final Condition condition = getCondition("source.itemId", "equals");

        return getValueOrNull(condition);
    }

    @GraphQLField
    public String cdp_profileID_equals(final DataFetchingEnvironment environment) {
        final Condition condition = getCondition("profileId", "equals");

        return getValueOrNull(condition);
    }

    @GraphQLField
    public OffsetDateTime cdp_timestamp_equals(final DataFetchingEnvironment environment) {
        final Condition condition = getCondition("timeStamp", "equals");

        return DateUtils.offsetDateTimeFromMap(getValueOrNull(condition, "propertyValueDate"));
    }

    @GraphQLField
    public OffsetDateTime cdp_timestamp_lt(final DataFetchingEnvironment environment) {
        final Condition condition = getCondition("timeStamp", "lessThan");

        return DateUtils.offsetDateTimeFromMap(getValueOrNull(condition, "propertyValueDate"));
    }

    @GraphQLField
    public OffsetDateTime cdp_timestamp_lte(final DataFetchingEnvironment environment) {
        final Condition condition = getCondition("timeStamp", "lessThanOrEqualTo");

        return DateUtils.offsetDateTimeFromMap(getValueOrNull(condition, "propertyValueDate"));
    }

    @GraphQLField
    public OffsetDateTime cdp_timestamp_gt(final DataFetchingEnvironment environment) {
        final Condition condition = getCondition("timeStamp", "greaterThan");

        return DateUtils.offsetDateTimeFromMap(getValueOrNull(condition, "propertyValueDate"));
    }

    @GraphQLField
    public OffsetDateTime cdp_timestamp_gte(final DataFetchingEnvironment environment) {
        final Condition condition = getCondition("timeStamp", "greaterThanOrEqualTo");

        return DateUtils.offsetDateTimeFromMap(getValueOrNull(condition, "propertyValueDate"));
    }

    @GraphQLField
    public CDPConsentUpdateEventFilter cdp_consentUpdateEvent(final DataFetchingEnvironment environment) {
        return new CDPConsentUpdateEventFilter();
    }

    @GraphQLField
    public CDPSessionEventFilter cdp_sessionEvent(final DataFetchingEnvironment environment) {
        return new CDPSessionEventFilter();
    }

    private <T> T getValueOrNull(final Condition condition) {
        return getValueOrNull(condition, "propertyValue");
    }

    @SuppressWarnings("unchecked")
    private <T> T getValueOrNull(final Condition condition, final String propertyValueParam) {
        if (condition == null) {
            return null;
        }

        if (condition.getParameter(propertyValueParam) != null) {
            return (T) condition.getParameter(propertyValueParam);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Condition getCondition(final String propertyName, final String comparisonOperator) {
        final List<Condition> subConditions = (List<Condition>) eventCondition.getParameter("subConditions");

        if (subConditions == null || subConditions.isEmpty()) {
            return null;
        }

        return subConditions.stream()
                .filter(condition -> "eventPropertyCondition".equals(condition.getConditionTypeId())
                        && Objects.nonNull(condition.getParameter("propertyName"))
                        && Objects.equals(condition.getParameter("propertyName").toString(), propertyName)
                        && Objects.nonNull(condition.getParameter("comparisonOperator"))
                        && Objects.equals(condition.getParameter("comparisonOperator").toString(), comparisonOperator)
                ).findFirst().orElse(null);
    }

}
