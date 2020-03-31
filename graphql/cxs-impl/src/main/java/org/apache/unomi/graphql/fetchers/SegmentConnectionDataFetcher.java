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

package org.apache.unomi.graphql.fetchers;

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.graphql.types.output.CDPPageInfo;
import org.apache.unomi.graphql.types.output.CDPSegment;
import org.apache.unomi.graphql.types.output.CDPSegmentConnection;
import org.apache.unomi.graphql.types.output.CDPSegmentEdge;

import java.util.List;
import java.util.stream.Collectors;

public abstract class SegmentConnectionDataFetcher extends BaseConnectionDataFetcher<CDPSegmentConnection> {

    protected CDPSegmentConnection createSegmentConnection(PartialList<Segment> segments) {
        final List<CDPSegmentEdge> segmentEdges = segments.getList().stream().map(segment -> new CDPSegmentEdge(new CDPSegment(segment), segment.getItemId())).collect(Collectors.toList());
        final CDPPageInfo cdpPageInfo = new CDPPageInfo(segments.getOffset() > 0, segments.getTotalSize() > segments.getList().size());

        return new CDPSegmentConnection(segmentEdges, cdpPageInfo);
    }

}
