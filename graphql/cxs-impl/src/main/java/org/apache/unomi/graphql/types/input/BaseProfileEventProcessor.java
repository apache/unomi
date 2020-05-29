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
package org.apache.unomi.graphql.types.input;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.utils.EventBuilder;

import java.util.Map;

public abstract class BaseProfileEventProcessor implements CDPEventProcessor {

    @SuppressWarnings("unchecked")
    protected Profile loadProfile(final Map<String, Object> eventInputAsMap, final DataFetchingEnvironment environment) {
        final Map<String, Object> cdpProfileId = (Map<String, Object>) eventInputAsMap.get("cdp_profileID");
        final ServiceManager serviceManager = environment.getContext();
        return serviceManager.getProfileService().load((String) cdpProfileId.get("id"));
    }

    protected final EventBuilder eventBuilder(final Profile profile) {
        return EventBuilder.create("updateProperties", profile);
    }

    protected final EventBuilder eventBuilder(final String eventType, final Profile profile) {
        return EventBuilder.create(eventType, profile);
    }

}
