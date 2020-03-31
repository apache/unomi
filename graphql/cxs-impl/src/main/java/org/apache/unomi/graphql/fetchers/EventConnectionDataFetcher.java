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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.graphql.types.output.CDPEvent;
import org.apache.unomi.graphql.types.output.CDPEventConnection;
import org.apache.unomi.graphql.types.output.CDPEventEdge;
import org.apache.unomi.graphql.types.output.CDPPageInfo;

import java.util.List;
import java.util.stream.Collectors;

public abstract class EventConnectionDataFetcher extends BaseConnectionDataFetcher<CDPEventConnection> {

    protected CDPEventConnection createEventConnection(PartialList<Event> events) {
        final List<CDPEventEdge> eventEdges = events.getList().stream().map(event -> new CDPEventEdge(new CDPEvent(event), event.getItemId())).collect(Collectors.toList());
        final CDPPageInfo cdpPageInfo = new CDPPageInfo(events.getOffset() > 0, events.getTotalSize() > events.getList().size());

        return new CDPEventConnection(eventEdges, cdpPageInfo);
    }
}
