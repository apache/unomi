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
import org.apache.unomi.api.Event;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.graphql.condition.factories.EventConditionFactory;
import org.apache.unomi.graphql.fetchers.ConnectionParams;
import org.apache.unomi.graphql.fetchers.EventConnectionDataFetcher;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.output.CDPEventConnection;

import java.util.Map;

public class ProfileAllEventsConnectionDataFetcher extends EventConnectionDataFetcher {

    private final Profile profile;
    private CDPEventFilterInput filterInput;

    public ProfileAllEventsConnectionDataFetcher(Profile profile, CDPEventFilterInput filterInput) {
        this.profile = profile;
        this.filterInput = filterInput;
    }

    @Override
    public CDPEventConnection get(DataFetchingEnvironment environment) throws Exception {
        final ConnectionParams params = parseConnectionParams(environment);
        final ServiceManager serviceManager = environment.getContext();

        final EventConditionFactory eventConditionFactory = EventConditionFactory.get(environment);

        Condition condition;

        if (filterInput == null) {
            condition = eventConditionFactory.propertyCondition("profileId", profile.getItemId());
        } else {
            final Map<String, Object> filterInputAsMap = environment.getArgument("filter");

            condition = eventConditionFactory.eventFilterInputCondition(filterInput, filterInputAsMap);
        }

        final PartialList<Event> events = serviceManager.getService(EventService.class).searchEvents(condition, params.getOffset(), params.getSize());

        return createEventConnection(events);
    }

}
