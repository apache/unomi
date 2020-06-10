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

import graphql.annotations.annotationTypes.*;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.graphql.fetchers.FindTopicsConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.SourceDataFetcher;
import org.apache.unomi.graphql.fetchers.TopicDataFetcher;
import org.apache.unomi.graphql.fetchers.ViewDataFetcher;
import org.apache.unomi.graphql.fetchers.event.EventDataFetcher;
import org.apache.unomi.graphql.fetchers.event.FindEventsConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.list.GetListDataFetcher;
import org.apache.unomi.graphql.fetchers.list.ListConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.FindProfilesConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.ProfileDataFetcher;
import org.apache.unomi.graphql.fetchers.profile.PropertiesConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.segment.FindSegmentsConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.segment.SegmentDataFetcher;
import org.apache.unomi.graphql.fetchers.segment.UnomiSegmentDataFetcher;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPListFilterInput;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;
import org.apache.unomi.graphql.types.input.CDPProfileFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.graphql.types.input.CDPSegmentFilterInput;
import org.apache.unomi.graphql.types.input.CDPTopicFilterInput;

import java.util.List;

import static org.apache.unomi.graphql.types.output.CDPQuery.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPQuery {

    public static final String TYPE_NAME = "CDP_Query";

    @GraphQLField
    public CDPProfile getProfile(
            final @GraphQLName("profileID") @GraphQLNonNull CDPProfileIDInput profileID,
            final @GraphQLName("createIfMissing") Boolean createIfMissing,
            final DataFetchingEnvironment environment) throws Exception {

        return new ProfileDataFetcher(profileID, createIfMissing).get(environment);
    }

    @GraphQLField
    public CDPProfileConnection findProfiles(
            final @GraphQLName("filter") CDPProfileFilterInput filter,
            final @GraphQLName("orderBy") List<CDPOrderByInput> orderBy,
            final @GraphQLName("first") Integer first,
            final @GraphQLName("after") String after,
            final @GraphQLName("last") Integer last,
            final @GraphQLName("before") String before,
            final
            @GraphQLName("unomi_text")
            @GraphQLDescription("The text that the item must have in one of its fields to be considered a match")
                    String text,
            final DataFetchingEnvironment environment) throws Exception {
        return new FindProfilesConnectionDataFetcher(filter, orderBy).get(environment);
    }

    @GraphQLField
    public CDPPropertyConnection getProfileProperties(final @GraphQLName("first") Integer first,
                                                      final @GraphQLName("after") String after,
                                                      final @GraphQLName("last") Integer last,
                                                      final @GraphQLName("before") String before,
                                                      final DataFetchingEnvironment environment) throws Exception {
        return new PropertiesConnectionDataFetcher().get(environment);
    }

    @GraphQLField
    public CDPEventConnection findEvents(
            final @GraphQLName("filter") CDPEventFilterInput filter,
            final @GraphQLName("orderBy") List<CDPOrderByInput> orderBy,
            final @GraphQLName("first") Integer first,
            final @GraphQLName("after") String after,
            final @GraphQLName("last") Integer last,
            final @GraphQLName("before") String before,
            final
            @GraphQLName("unomi_text")
            @GraphQLDescription("The text that the item must have in one of its fields to be considered a match")
                    String text,
            final DataFetchingEnvironment environment
    ) {
        return new FindEventsConnectionDataFetcher(filter, orderBy).get(environment);
    }

    @GraphQLField
    public CDPEventInterface getEvent(final @GraphQLNonNull @GraphQLName("id") String id, final DataFetchingEnvironment environment) throws Exception {
        return new EventDataFetcher(id).get(environment);
    }

    @GraphQLField
    public CDPSegmentConnection findSegments(final @GraphQLName("filter") CDPSegmentFilterInput filter,
                                             final @GraphQLName("orderBy") List<CDPOrderByInput> orderBy,
                                             final @GraphQLName("first") Integer first,
                                             final @GraphQLName("after") String after,
                                             final @GraphQLName("last") Integer last,
                                             final @GraphQLName("before") String before,
                                             final DataFetchingEnvironment environment) {
        return new FindSegmentsConnectionDataFetcher(filter, orderBy).get(environment);
    }

    @GraphQLField
    public CDPSegment getSegment(final @GraphQLID @GraphQLName("segmentID") String segmentId,
                                 final DataFetchingEnvironment environment) throws Exception {
        return new SegmentDataFetcher(segmentId).get(environment);
    }

    @GraphQLField
    public UnomiSegment getUnomiSegment(final @GraphQLID @GraphQLName("segmentID") String segmentId,
                                        final DataFetchingEnvironment environment) throws Exception {
        return new UnomiSegmentDataFetcher(segmentId).get(environment);
    }

    @GraphQLField
    public List<CDPView> getViews(final DataFetchingEnvironment environment) throws Exception {
        return new ViewDataFetcher().get(environment);
    }

    @GraphQLField
    public CDPTopic getTopic(final @GraphQLID @GraphQLName("topicID") String topicId,
                             final DataFetchingEnvironment environment) throws Exception {
        return new TopicDataFetcher(topicId).get(environment);
    }

    @GraphQLField
    public CDPTopicConnection findTopics(final @GraphQLName("filter") CDPTopicFilterInput filterInput,
                                         final @GraphQLName("orderBy") List<CDPOrderByInput> orderByInput,
                                         final @GraphQLName("first") Integer first,
                                         final @GraphQLName("after") String after,
                                         final @GraphQLName("last") Integer last,
                                         final @GraphQLName("before") String before,
                                         final DataFetchingEnvironment environment) throws Exception {
        return new FindTopicsConnectionDataFetcher(filterInput, orderByInput).get(environment);
    }

    @GraphQLField
    public List<CDPSource> getSources(final DataFetchingEnvironment environment) throws Exception {
        return new SourceDataFetcher().get(environment);
    }

    @GraphQLField
    public CDPList getList(
            final @GraphQLID @GraphQLName("listID") String listId,
            final DataFetchingEnvironment environment) throws Exception {
        return new GetListDataFetcher(listId).get(environment);
    }

    @GraphQLField
    public CDPListConnection findLists(
            final @GraphQLName("filter") CDPListFilterInput filterInput,
            final @GraphQLName("orderBy") List<CDPOrderByInput> orderByInput,
            final @GraphQLName("first") Integer first,
            final @GraphQLName("after") String after,
            final @GraphQLName("last") Integer last,
            final @GraphQLName("before") String before,
            final DataFetchingEnvironment environment) throws Exception {
        return new ListConnectionDataFetcher(filterInput, orderByInput).get(environment);
    }

}
