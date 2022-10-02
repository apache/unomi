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

import graphql.annotations.annotationTypes.GraphQLDescription;
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
import org.apache.unomi.graphql.fetchers.profile.ProfileMatchesDataFetcher;
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
@GraphQLDescription(" The Customer Data Platform dynamically creates and build profiles from events that occur over time. A Profile can be created from an anonymous visitor on a webpage, populated from an identity system, a CRM, or the combination of all of them.")
public class CDPProfile implements CDPProfileInterface {

    public static final String TYPE_NAME = "CDP_Profile";

    private final Profile profile;

    public CDPProfile(Profile profile) {
        this.profile = profile;
    }

    @Override
    public Object getProperty(final String propertyName) {
        return profile != null ? profile.getProperty(propertyName) : null;
    }

    @Override
    @GraphQLField
    public List<CDPProfileID> cdp_profileIDs(final DataFetchingEnvironment environment) throws Exception {
        return new ProfileIdsDataFetcher(profile).get(environment);
    }

    @Override
    @GraphQLField
    public List<CDPSegment> cdp_segments(final @GraphQLName("views") List<String> viewIds, final DataFetchingEnvironment environment) throws Exception {
        return new ProfileSegmentsDataFetcher(profile, viewIds).get(environment);
    }

    @Override
    @GraphQLField
    public List<CDPInterest> cdp_interests(final @GraphQLName("views") List<String> viewIds, final DataFetchingEnvironment environment) throws Exception {
        return new ProfileInterestsDataFetcher(profile, viewIds).get(environment);
    }

    @Override
    @GraphQLField
    public List<CDPConsent> cdp_consents(final DataFetchingEnvironment environment) throws Exception {
        return new ProfileConsentsDataFetcher(profile).get(environment);
    }

    @Override
    @GraphQLField
    public List<CDPList> cdp_lists(final @GraphQLName("views") List<String> viewIds, final DataFetchingEnvironment environment) throws Exception {
        return new ProfileListsDataFetcher(profile, viewIds).get(environment);
    }

    @GraphQLField
    public CDPEventConnection cdp_events(
            final @GraphQLName("filter") CDPEventFilterInput filterInput,
            final @GraphQLName("first") Integer first,
            final @GraphQLName("last") Integer last,
            final @GraphQLName("before") String before,
            final @GraphQLName("after") String after,
            final DataFetchingEnvironment environment
    ) throws Exception {
        return new ProfileAllEventsConnectionDataFetcher(profile, filterInput).get(environment);
    }

    @GraphQLField
    public CDPEventConnection cdp_lastEvents(
            final @GraphQLName("profileID") CDPProfileIDInput profileID,
            final @GraphQLName("count") Integer count,
            final DataFetchingEnvironment environment
    ) throws Exception {
        return new ProfileLastEventsConnectionDataFetcher(profile, count).get(environment);
    }

    @GraphQLField
    public List<CDPFilterMatch> cdp_matches(@GraphQLName("namedFilters") List<CDPNamedFilterInput> namedFilters,
                                            final DataFetchingEnvironment environment) throws Exception {
        return new ProfileMatchesDataFetcher(profile, namedFilters).get(environment);
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
