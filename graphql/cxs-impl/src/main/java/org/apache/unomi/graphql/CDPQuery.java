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
package org.apache.unomi.graphql;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.graphql.types.input.CDPEventFilter;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;
import org.apache.unomi.graphql.types.input.CDPSegmentFilterInput;
import org.apache.unomi.graphql.types.output.*;

import java.util.ArrayList;
import java.util.List;

@GraphQLName("CDP_Query")
public class CDPQuery {

    CDPGraphQLProvider cdpGraphQLProvider;

    public CDPQuery(CDPGraphQLProvider cdpGraphQLProvider) {
        this.cdpGraphQLProvider = cdpGraphQLProvider;
    }

    @GraphQLField
    public List<CDPEventType> getEventTypes() {
        return new ArrayList<>();
    }

    @GraphQLField
    public CDPEvent getEvent(@GraphQLName("id") String id) {
        return new CDPEvent();
    }

    @GraphQLField
    public CDPEventConnection findEvents(@GraphQLName("filter") CDPEventFilter filter,
                                         @GraphQLName("orderBy") CDPOrderByInput orderBy,
                                         DataFetchingEnvironment env) {
        env.getArgument("first");
        env.getArgument("after");
        return new CDPEventConnection();
    }

    @GraphQLField
    public CDPSegmentConnection findSegments(@GraphQLName("filter") CDPSegmentFilterInput filter,
                                             @GraphQLName("orderBy") CDPOrderByInput orderBy,
                                             DataFetchingEnvironment env) {
        SegmentService segmentService = cdpGraphQLProvider.getCdpProviderManager().getSegmentService();
        Query query = new Query();
        segmentService.getSegmentMetadatas(query);
        return new CDPSegmentConnection();
    }

    @GraphQLField
    public CDPSegment getSegment(@GraphQLName("segmentId") String segmentId) {
        SegmentService segmentService = cdpGraphQLProvider.getCdpProviderManager().getSegmentService();
        Segment segment = segmentService.getSegmentDefinition(segmentId);
        if (segment == null) {
            return null;
        }
        CDPSegment cdpSegment = new CDPSegment();
        cdpSegment.id = segment.getItemId();
        cdpSegment.name = segment.getMetadata().getName();
        CDPView cdpView = new CDPView();
        cdpView.name = segment.getScope();
        cdpSegment.view = cdpView;
        cdpSegment.condition = getSegmentCondition(segment.getCondition());
        return cdpSegment;
    }

    private CDPSegmentCondition getSegmentCondition(Condition segmentRootCondition) {
        if (segmentRootCondition == null) {
            return null;
        }
        // @todo translate the conditions into something that the CDP spec can work with.

        // we probably have to scan the tree to find any event conditions and seperate them
        // from the profile property conditions (what about session conditions ?)
        return null;
    }
}
