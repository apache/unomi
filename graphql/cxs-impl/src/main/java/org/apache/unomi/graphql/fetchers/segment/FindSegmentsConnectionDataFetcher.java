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

package org.apache.unomi.graphql.fetchers.segment;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.graphql.condition.ProfileConditionFactory;
import org.apache.unomi.graphql.fetchers.ConnectionParams;
import org.apache.unomi.graphql.fetchers.SegmentConnectionDataFetcher;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;
import org.apache.unomi.graphql.types.input.CDPSegmentFilterInput;
import org.apache.unomi.graphql.types.output.CDPSegmentConnection;

import java.util.List;
import java.util.stream.Collectors;

public class FindSegmentsConnectionDataFetcher extends SegmentConnectionDataFetcher {

    private final CDPSegmentFilterInput filterInput;

    private final List<CDPOrderByInput> orderByInput;

    public FindSegmentsConnectionDataFetcher(CDPSegmentFilterInput filterInput, List<CDPOrderByInput> orderByInput) {
        this.filterInput = filterInput;
        this.orderByInput = orderByInput;
    }

    @Override
    public CDPSegmentConnection get(DataFetchingEnvironment environment) {
        final ServiceManager serviceManager = environment.getContext();
        final ConnectionParams params = parseConnectionParams(environment);

        final Condition condition = ProfileConditionFactory.get(environment)
                .segmentFilterInputCondition(filterInput, params.getAfter(), params.getBefore());
        final Query query = buildQuery(condition, orderByInput, params);
        final PartialList<Metadata> metas = serviceManager.getSegmentService().getSegmentMetadatas(query);

        final List<Segment> segmentList = metas.getList().stream()
                .map(meta -> serviceManager.getSegmentService().getSegmentDefinition(meta.getId()))
                .collect(Collectors.toList());

        PartialList<Segment> segments = new PartialList<>(segmentList, metas.getOffset(), metas.getPageSize(), metas.getTotalSize(), metas.getTotalSizeRelation());

        return createSegmentConnection(segments);
    }
}
