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
package org.apache.unomi.itests.graphql;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A profile's {@code cdp_events} connection is scoped to that profile. A supplied event filter must be
 * combined with that scope, never replace it: otherwise a caller could read one profile's events by
 * querying another profile and pointing the filter at the first.
 * <p>
 * The query here asks profile A for its events while filtering on profile B's id. The two conditions
 * are mutually exclusive, so a correctly-scoped query returns nothing; a query that let the filter
 * replace the scope would return B's event.
 */
public class GraphQLProfileEventsScopeIT extends BaseGraphQLIT {

    private static final String PROFILE_A = "f006-profile-a";
    private static final String PROFILE_B = "f006-profile-b";
    private static final String EVENT_B = "f006-event-b";

    @Before
    public void setUp() throws InterruptedException {
        removeItems(Event.class);

        final Profile profileA = new Profile(PROFILE_A);
        persistenceService.save(profileA);
        final Profile profileB = new Profile(PROFILE_B);
        persistenceService.save(profileB);

        // An event that belongs to B only. If the profile scope on A's cdp_events were replaced by the
        // client filter, this is the event that would leak.
        persistenceService.save(new Event(EVENT_B, "testProfileUpdated", null, profileB, "test", profileB, null, new Date()));

        refreshPersistence(Event.class, Profile.class);
        keepTrying("Event for profile B should be queryable via persistence",
                () -> persistenceService.query("itemId", EVENT_B, null, Event.class),
                events -> events != null && events.size() == 1,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testProfileEventsCannotBeWidenedToAnotherProfileByFilter() throws Exception {
        try (CloseableHttpResponse response = postWithAuthType("graphql/security/profile-events-scope.json", AuthType.PUBLIC_KEY)) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals(200, response.getStatusLine().getStatusCode());
            final List<Map> edges = context.getValue("data.cdp.getProfile.cdp_events.edges");
            // Scope (profileId = A) AND filter (profileId = B) is unsatisfiable, so nothing is returned.
            // A regression that replaced the scope with the filter would return profile B's event here.
            Assert.assertTrue("Profile A's events query must not return another profile's events",
                    edges == null || edges.isEmpty());
        }
    }
}
