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
package org.apache.unomi.graphql.types;

import graphql.annotations.annotationTypes.GraphQLDataFetcher;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.apache.unomi.graphql.fetchers.EventConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.event.FindEventsConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.FindProfilesConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.ProfileDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.PropertiesConnectionDataFetcher;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;
import org.apache.unomi.graphql.types.input.CDPProfileFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.graphql.types.output.CDPEventConnection;
import org.apache.unomi.graphql.types.output.CDPProfile;
import org.apache.unomi.graphql.types.output.CDPProfileConnection;
import org.apache.unomi.graphql.types.output.CDPPropertyConnection;

import java.util.List;

import static org.apache.unomi.graphql.types.CDPQuery.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPQuery {

    public static final String TYPE_NAME = "CDP_Query";

    @GraphQLField
    @GraphQLDataFetcher(ProfileDataFetcher.class)
    public CDPProfile getProfile(
            final @GraphQLName("profileID") @GraphQLNonNull CDPProfileIDInput profileID,
            final @GraphQLName("createIfMissing") Boolean createIfMissing) {

        return null;
    }

    @GraphQLField
    @GraphQLDataFetcher(FindProfilesConnectionDataFetcher.class)
    public CDPProfileConnection findProfiles(final @GraphQLName("filter") CDPProfileFilterInput filter,
                                             final @GraphQLName("orderBy") List<CDPOrderByInput> orderBy,
                                             final @GraphQLName("first") Integer first,
                                             final @GraphQLName("after") String after,
                                             final @GraphQLName("last") Integer last,
                                             final @GraphQLName("before") String before) {
        return null;
    }

    @GraphQLField
    @GraphQLDataFetcher(PropertiesConnectionDataFetcher.class)
    public CDPPropertyConnection getProfileProperties(final @GraphQLName("first") Integer first,
                                                      final @GraphQLName("last") Integer last) {
        return null;
    }

    @GraphQLField
    @GraphQLDataFetcher(FindEventsConnectionDataFetcher.class)
    public CDPEventConnection findEvents(final @GraphQLName("filter") CDPEventFilterInput filter,
                                         final @GraphQLName("orderBy") List<CDPOrderByInput> orderBy,
                                         final @GraphQLName("first") Integer first,
                                         final @GraphQLName("after") String after,
                                         final @GraphQLName("last") Integer last,
                                         final @GraphQLName("before") String before) {
        return null;
    }
}
