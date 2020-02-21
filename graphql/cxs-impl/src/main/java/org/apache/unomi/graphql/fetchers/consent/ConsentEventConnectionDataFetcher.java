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

package org.apache.unomi.graphql.fetchers.consent;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.fetchers.EventConnectionDataFetcher;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPConsent;
import org.apache.unomi.graphql.types.output.CDPEventConnection;

import java.util.Arrays;

public class ConsentEventConnectionDataFetcher extends EventConnectionDataFetcher {


    @Override
    public CDPEventConnection get(DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();
        final CDPConsent consent = environment.getSource();

        final Condition andCondition = createBoolCondition("and", serviceManager.getDefinitionsService());
        final Condition eventCondition = createEventPropertyCondition("eventType", "modifyConsent", serviceManager.getDefinitionsService());
        final Condition consentCondition = createEventPropertyCondition("target.token", consent.getToken(), serviceManager.getDefinitionsService());

        andCondition.setParameter("subConditions", Arrays.asList(eventCondition, consentCondition));
        final PartialList<Event> events = serviceManager.getEventService().searchEvents(andCondition, 0, DEFAULT_PAGE_SIZE);

        return createEventConnection(events);
    }
}
