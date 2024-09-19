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

package org.apache.unomi.graphql.fetchers.event;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.graphql.condition.factories.EventConditionFactory;
import org.apache.unomi.graphql.fetchers.ConnectionParams;
import org.apache.unomi.graphql.fetchers.EventConnectionDataFetcher;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;
import org.apache.unomi.graphql.types.output.CDPEventConnection;

import java.util.List;
import java.util.Map;

public class FindEventsConnectionDataFetcher extends EventConnectionDataFetcher {

    private final CDPEventFilterInput filterInput;

    private final List<CDPOrderByInput> orderByInput;

    public FindEventsConnectionDataFetcher(CDPEventFilterInput filterInput, List<CDPOrderByInput> orderByInput) {
        this.filterInput = filterInput;
        this.orderByInput = orderByInput;
    }

    @Override
    public CDPEventConnection get(DataFetchingEnvironment environment) {
        final ServiceManager serviceManager = environment.getContext();
        final ConnectionParams params = parseConnectionParams(environment);

        final Map<String, Object> filterInputAsMap = environment.getArgument("filter");

        final Condition condition = EventConditionFactory.get(environment)
                .eventFilterInputCondition(filterInput, filterInputAsMap);

        final Query query = buildQuery(condition, orderByInput, params);

        PartialList<Event> events = serviceManager.getService(EventService.class).search(query);

        return createEventConnection(events);
    }
}
