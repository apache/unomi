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
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.fetchers.EventConnectionDataFetcher;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.graphql.types.output.CDPEventConnection;
import org.apache.unomi.graphql.types.output.CDPProfile;

public class ProfileLastEventsConnectionDataFetcher extends EventConnectionDataFetcher {

    public static final Integer DEFAULT_SIZE = 10;

    @Override
    public CDPEventConnection get(DataFetchingEnvironment environment) throws Exception {
        Integer count = environment.getArgument("count");
        CDPProfileIDInput cdpProfileIDInput = environment.getArgument("profileID");

        if (count == null) {
            count = DEFAULT_SIZE;
        }

        final String profileId = cdpProfileIDInput != null
                ? cdpProfileIDInput.getId()
                : ((CDPProfile) environment.getSource()).getProfile().getItemId();

        final ServiceManager serviceManager = environment.getContext();

        final Condition condition = createEventPropertyCondition("profileId", profileId, serviceManager.getDefinitionsService());
        final PartialList<Event> events = serviceManager.getEventService().searchEvents(condition, 0, count);

        return createEventConnection(events);
    }
}
