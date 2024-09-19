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

package org.apache.unomi.graphql.fetchers.profile;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.graphql.fetchers.BaseDataFetcher;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPSegment;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileSegmentsDataFetcher extends BaseDataFetcher<List<CDPSegment>> {

    private final Profile profile;
    private final List<String> viewIds;

    public ProfileSegmentsDataFetcher(Profile profile, List<String> viewIds) {
        this.profile = profile;
        this.viewIds = viewIds;
    }

    @Override
    public List<CDPSegment> get(DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();
        final SegmentService segmentService = serviceManager.getService(SegmentService.class);

        final List<Metadata> metadata = segmentService.getSegmentMetadatasForProfile(profile);

        Stream<Metadata> stream = metadata.stream();

        if (viewIds != null) {
            stream = stream.filter(s -> viewIds.contains(s.getScope()));
        }

        return stream.map(s -> createCDPSegment(s, segmentService)).collect(Collectors.toList());
    }


    private CDPSegment createCDPSegment(Metadata segmentMetadata, SegmentService segmentService) {
        Segment segment = segmentService.getSegmentDefinition(segmentMetadata.getId());

        return new CDPSegment(segment);
    }
}
