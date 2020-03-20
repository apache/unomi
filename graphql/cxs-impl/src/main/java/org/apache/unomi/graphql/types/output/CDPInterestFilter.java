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
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.fetchers.segments.SegmentProfileInterestsDataFetcher;

import static org.apache.unomi.graphql.types.output.CDPInterestFilter.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPInterestFilter {

    public static final String TYPE_NAME = "CDP_InterestFilter";

    private final Condition segmentCondition;

    public CDPInterestFilter(final Condition segmentCondition) {
        this.segmentCondition = segmentCondition;
    }

    @GraphQLID
    @GraphQLField
    public String topic_equals(final DataFetchingEnvironment environment) throws Exception {
        final Object result = SegmentProfileInterestsDataFetcher.builder()
                .setInterestName(true)
                .build()
                .get(environment);

        if (result != null) {
            return result.toString();
        }

        return null;
    }

    @GraphQLField
    public Double score_equals(final DataFetchingEnvironment environment) throws Exception {
        return getScore("equals", environment);
    }

    @GraphQLField
    public Double score_lt(final DataFetchingEnvironment environment) throws Exception {
        return getScore("lessThan", environment);
    }

    @GraphQLField
    public Double score_lte(final DataFetchingEnvironment environment) throws Exception {
        return getScore("lessThanOrEqualTo", environment);
    }

    @GraphQLField
    public Double score_gt(final DataFetchingEnvironment environment) throws Exception {
        return getScore("greaterThan", environment);
    }

    @GraphQLField
    public Double score_gte(final DataFetchingEnvironment environment) throws Exception {
        return getScore("greaterThanOrEqualTo", environment);
    }

    private Double getScore(
            final String comparisonOperator, final DataFetchingEnvironment environment) throws Exception {
        final Object result = SegmentProfileInterestsDataFetcher.builder()
                .setComparisonOperator(comparisonOperator)
                .build()
                .get(environment);

        if (result != null) {
            return (Double) result;
        }

        return null;
    }

    public Condition getSegmentCondition() {
        return segmentCondition;
    }
}
