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
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.graphql.fetchers.segments.SegmentProfileConsentsDataFetcher;
import org.apache.unomi.graphql.fetchers.segments.SegmentProfileIDsDataFetcher;
import org.apache.unomi.graphql.fetchers.segments.SegmentProfileListDataFetcher;
import org.apache.unomi.graphql.fetchers.segments.SegmentProfileSegmentsDataFetcher;

import java.util.List;

import static org.apache.unomi.graphql.types.output.CDPProfileFilter.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPProfileFilter {

    public static final String TYPE_NAME = "CDP_ProfileFilter";

    private final Segment segment;

    public CDPProfileFilter(final Segment segment) {
        this.segment = segment;
    }

    @GraphQLField
    public List<String> profileIDs(final DataFetchingEnvironment environment) throws Exception {
        return new SegmentProfileIDsDataFetcher().get(environment);
    }

    @GraphQLField
    public List<String> segments_contains(final DataFetchingEnvironment environment) throws Exception {
        return new SegmentProfileSegmentsDataFetcher().get(environment);
    }

    @GraphQLField
    public List<String> consents_contains(final DataFetchingEnvironment environment) throws Exception {
        return new SegmentProfileConsentsDataFetcher().get(environment);
    }

    @GraphQLField
    public List<String> lists_contains(final DataFetchingEnvironment environment) throws Exception {
        return new SegmentProfileListDataFetcher().get(environment);
    }

    @GraphQLField
    public CDPProfilePropertiesFilter properties(final DataFetchingEnvironment environment) throws Exception {
        return new CDPProfilePropertiesFilter(segment.getCondition());
    }

    @GraphQLField
    public CDPInterestFilter interests(final DataFetchingEnvironment environment) throws Exception {
        return new CDPInterestFilter(segment.getCondition());
    }


    public Segment getSegment() {
        return segment;
    }

}
