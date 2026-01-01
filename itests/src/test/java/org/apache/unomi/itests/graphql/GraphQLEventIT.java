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

public class GraphQLEventIT extends BaseGraphQLIT {

    private final String profileID = "profile-1";
    private final String eventID = "event-1";
    private Profile profile;

    @Before
    public void setUp() throws InterruptedException {
        profile = new Profile(profileID);
        persistenceService.save(profile);

        removeItems(Event.class);
    }


    @Test
    public void testGetEvent_notExists() throws Exception {
        try (CloseableHttpResponse response = post("graphql/event/get-event-not-exists.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertNull(context.getValue("data.cdp.getEvent"));
        }
    }

    @Test
    public void testGetEvent() throws Exception {
        final Event event = createEvent(eventID, profile);
        refreshPersistence(Event.class);

        try (CloseableHttpResponse response = post("graphql/event/get-event.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertNotNull(context.getValue("data.cdp.getEvent"));
            Assert.assertEquals(event.getItemId(), context.getValue("data.cdp.getEvent.id"));
            Assert.assertEquals(event.getProfileId(), context.getValue("data.cdp.getEvent.cdp_profileID.id"));
        }
    }

    @Test
    public void testFindEvents() throws Exception {
        createEvent(eventID, profile);
        createEvent("event-2", profile);
        final Profile profile2 = new Profile("profile-2");
        persistenceService.save(profile2);
        createEvent("event-3", profile2);

        // Wait for events to be properly indexed before querying via GraphQL
        refreshPersistence(Event.class, Profile.class);
        // Verify events are queryable via persistence service first
        keepTrying("Events should be queryable via persistence",
                () -> {
                        List<Event> events = persistenceService.query("itemId", eventID, null, Event.class);
                        return events != null && events.size() == 1;
                },
                (found) -> found, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Wait for events to be indexed and queryable via GraphQL
        ResponseContext[] contextHolder = new ResponseContext[1];
        CloseableHttpResponse response = keepTrying("GraphQL query should return events",
                () -> {
                    try {
                        CloseableHttpResponse resp = post("graphql/event/find-events.json");
                        if (resp != null && resp.getEntity() != null) {
                            // Buffer entity to allow multiple reads
                            org.apache.http.entity.BufferedHttpEntity bufferedEntity =
                                new org.apache.http.entity.BufferedHttpEntity(resp.getEntity());
                            resp.setEntity(bufferedEntity);
                        }
                        return resp;
                    } catch (Exception e) {
                        return null;
                    }
                },
                resp -> {
                    if (resp == null || resp.getEntity() == null) return false;
                    try {
                        final ResponseContext context = ResponseContext.parse(resp.getEntity());
                        List<Map> edges = context.getValue("data.cdp.findEvents.edges");
                        if (edges != null && edges.size() == 1) {
                            contextHolder[0] = context;
                            return true;
                        }
                        return false;
                    } catch (Exception e) {
                        return false;
                    }
                },
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        try {
            Assert.assertNotNull("Response context should be available", contextHolder[0]);
            final ResponseContext context = contextHolder[0];
            Assert.assertNotNull(context.getValue("data.cdp.findEvents"));
            List<Map> edges = context.getValue("data.cdp.findEvents.edges");
            Assert.assertEquals(1, edges.size());
            Assert.assertEquals(profileID, context.getValue("data.cdp.findEvents.edges[0].node.cdp_profileID.id"));
            Assert.assertEquals(eventID, context.getValue("data.cdp.findEvents.edges[0].node.id"));
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Test
    public void testProcessEvents() throws Exception {
        final Profile originalProfile = persistenceService.load(profileID, Profile.class);
        Assert.assertNull(originalProfile.getProperty("firstName"));
        Assert.assertNull(originalProfile.getProperty("lastName"));

        try (CloseableHttpResponse response = post("graphql/event/process-events.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());
            Assert.assertEquals(Integer.valueOf(1), context.getValue("data.cdp.processEvents"));

            final Profile updatedProfile = persistenceService.load(profileID, Profile.class);
            Assert.assertEquals("Gigi", updatedProfile.getProperty("firstName"));
            Assert.assertEquals("Bergkamp", updatedProfile.getProperty("lastName"));
        }
    }

    private Event createEvent(final String eventID, final Profile profile) throws InterruptedException {
        // Use a test-specific event type instead of "profileUpdated" to avoid triggering rules
        // that match profileUpdated events and creating loops during integration tests
        Event event = new Event(eventID, "testProfileUpdated", null, profile, "test", profile, null, new Date());
        persistenceService.save(event);
        return event;
    }
}
