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
import org.apache.unomi.graphql.types.input.CXSEventFilter;
import org.apache.unomi.graphql.types.input.CXSOrderByInput;
import org.apache.unomi.graphql.types.input.CXSSegmentFilterInput;
import org.apache.unomi.graphql.types.output.*;

import java.util.ArrayList;
import java.util.List;

@GraphQLName("CXS_Query")
public class CXSQuery {

    CXSGraphQLProvider cxsGraphQLProvider;

    public CXSQuery(CXSGraphQLProvider cxsGraphQLProvider) {
        this.cxsGraphQLProvider = cxsGraphQLProvider;
    }

    @GraphQLField
    public List<CXSEventType> getEventTypes() {
        return new ArrayList<>();
    }

    @GraphQLField
    public CXSEvent getEvent(@GraphQLName("id") String id) {
        return new CXSEvent();
    }

    @GraphQLField
    public CXSEventConnection findEvents(@GraphQLName("filter") CXSEventFilter filter,
                                         @GraphQLName("orderBy") CXSOrderByInput orderBy,
                                         DataFetchingEnvironment env) {
        env.getArgument("first");
        env.getArgument("after");
        return new CXSEventConnection();
    }

    @GraphQLField
    public CXSSegmentConnection findSegments(@GraphQLName("filter") CXSSegmentFilterInput filter,
                                             @GraphQLName("orderBy") CXSOrderByInput orderBy,
                                             DataFetchingEnvironment env) {
        SegmentService segmentService = cxsGraphQLProvider.getCxsProviderManager().getSegmentService();
        Query query = new Query();
        segmentService.getSegmentMetadatas(query);
        return new CXSSegmentConnection();
    }

    @GraphQLField
    public CXSSegment getSegment(@GraphQLName("segmentId") String segmentId) {
        SegmentService segmentService = cxsGraphQLProvider.getCxsProviderManager().getSegmentService();
        Segment segment = segmentService.getSegmentDefinition(segmentId);
        if (segment == null) {
            return null;
        }
        CXSSegment cxsSegment = new CXSSegment();
        cxsSegment.id = segment.getItemId();
        cxsSegment.name = segment.getMetadata().getName();
        CXSView cxsView = new CXSView();
        cxsView.name = segment.getScope();
        cxsSegment.view = cxsView;
        cxsSegment.condition = getSegmentCondition(segment.getCondition());
        return cxsSegment;
    }

    private CXSSegmentCondition getSegmentCondition(Condition segmentRootCondition) {
        if (segmentRootCondition == null) {
            return null;
        }
        // @todo translate the conditions into something that the CXS spec can work with.

        // we probably have to scan the tree to find any event conditions and seperate them
        // from the profile property conditions (what about session conditions ?)
        return null;
    }
}
