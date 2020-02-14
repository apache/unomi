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
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.commands.GetSegmentsByProfileCommand;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPNamedFilterInput;
import org.apache.unomi.graphql.types.input.CDPOptimizationInput;
import org.apache.unomi.graphql.types.input.CDPRecommendationInput;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@GraphQLName("CDP_Profile")
public class CDPProfile implements CDPProfileInterface {

    private Profile profile;

    public CDPProfile(Profile profile) {
        this.profile = profile;
    }

    @Override
    @GraphQLField
    public List<CDPProfileID> cdp_profileIDs(DataFetchingEnvironment environment) {
        return Collections.singletonList(createProfileId(profile));
    }

    private CDPProfileID createProfileId(Profile profile) {
        final CDPProfileID profileID = new CDPProfileID();

        profileID.setId(profile.getItemId());
        profileID.setClient(getDefaultClient());

        return profileID;
    }

    private CDPClient getDefaultClient() {
        final CDPClient client = new CDPClient();

        client.setId("defaultClientId");
        client.setTitle("Default ClientName");

        return client;
    }

    @Override
    @GraphQLField
    public List<CDPSegment> cdp_segments(final @GraphQLName("views") List<String> viewIds, DataFetchingEnvironment environment) {
        return GetSegmentsByProfileCommand.create(profile).setServiceManager(environment.getContext()).build().execute();
    }

    @Override
    @GraphQLField
    public List<CDPInterest> cdp_interests(final @GraphQLName("views") List<String> viewIds, DataFetchingEnvironment environment) {
        return Collections.emptyList();
    }

    @Override
    @GraphQLField
    public List<CDPConsent> cdp_consents(DataFetchingEnvironment environment) {
        return Collections.emptyList();
    }

    @Override
    @GraphQLField
    public List<CDPList> cdp_lists(final @GraphQLName("views") List<String> viewIds, DataFetchingEnvironment environment) {
        return Collections.emptyList();
    }

    @GraphQLField
    public CDPEventConnection cdp_events(
            @GraphQLName("filter") CDPEventFilterInput filterInput,
            @GraphQLName("first") Integer first,
            @GraphQLName("last") Integer last,
            @GraphQLName("before") String before,
            @GraphQLName("after") String after
    ) {
        return new CDPEventConnection();
    }

    @GraphQLField
    public CDPEventConnection cdp_lastEvents(
            @GraphQLName("profileID") CDPProfileIDInput profileID,
            @GraphQLName("count") Integer count
    ) {
        return new CDPEventConnection();
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

}
