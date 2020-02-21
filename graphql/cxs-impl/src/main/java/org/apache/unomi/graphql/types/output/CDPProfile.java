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

import graphql.annotations.annotationTypes.GraphQLDataFetcher;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.fetchers.profile.ProfileAllEventsConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.ProfileConsentsDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.ProfileIdsDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.ProfileInterestsDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.ProfileLastEventsConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.ProfileListsDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.ProfileSegmentsDataFetcher;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPNamedFilterInput;
import org.apache.unomi.graphql.types.input.CDPOptimizationInput;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.graphql.types.input.CDPRecommendationInput;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.apache.unomi.graphql.types.output.CDPProfile.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPProfile implements CDPProfileInterface {

    public static final String TYPE_NAME = "CDP_Profile";

    private Profile profile;

    public CDPProfile(Profile profile) {
        this.profile = profile;
    }

    @Override
    @GraphQLField
    @GraphQLDataFetcher(ProfileIdsDataFetcher.class)
    public List<CDPProfileID> cdp_profileIDs(DataFetchingEnvironment environment) {
        return null;
    }

    @Override
    @GraphQLField
    @GraphQLDataFetcher(ProfileSegmentsDataFetcher.class)
    public List<CDPSegment> cdp_segments(final @GraphQLName("views") List<String> viewIds, DataFetchingEnvironment environment) {
        return null;
    }

    @Override
    @GraphQLField
    @GraphQLDataFetcher(ProfileInterestsDataFetcher.class)
    public List<CDPInterest> cdp_interests(final @GraphQLName("views") List<String> viewIds, DataFetchingEnvironment environment) {
        return null;
    }

    @Override
    @GraphQLField
    @GraphQLDataFetcher(ProfileConsentsDataFetcher.class)
    public List<CDPConsent> cdp_consents(DataFetchingEnvironment environment) {
        return null;
    }

    @Override
    @GraphQLField
    @GraphQLDataFetcher(ProfileListsDataFetcher.class)
    public List<CDPList> cdp_lists(final @GraphQLName("views") List<String> viewIds, DataFetchingEnvironment environment) {
        return null;
    }

    @GraphQLField
    @GraphQLDataFetcher(ProfileAllEventsConnectionDataFetcher.class)
    public CDPEventConnection cdp_events(
            @GraphQLName("filter") CDPEventFilterInput filterInput,
            @GraphQLName("first") Integer first,
            @GraphQLName("last") Integer last,
            @GraphQLName("before") String before,
            @GraphQLName("after") String after
    ) {
        return null;
    }

    @GraphQLField
    @GraphQLDataFetcher(ProfileLastEventsConnectionDataFetcher.class)
    public CDPEventConnection cdp_lastEvents(
            @GraphQLName("profileID") CDPProfileIDInput profileID,
            @GraphQLName("count") Integer count
    ) {
        return null;
    }

    @GraphQLField
    public List<CDPFilterMatch> cdp_matches(@GraphQLName("namedFilters") Collection<CDPNamedFilterInput> namedFilters) {
        return Collections.emptyList();
    }

    @GraphQLField
    public List<CDPOptimizationResult> cdp_optimize(@GraphQLName("parameters") Collection<CDPOptimizationInput> parameters) {
        return Collections.emptyList();
    }

    @GraphQLField
    public List<CDPRecommendationResult> cdp_recommend(@GraphQLName("parameters") Collection<CDPRecommendationInput> parameters) {
        return Collections.emptyList();
    }

    public Profile getProfile() {
        return profile;
    }
}
