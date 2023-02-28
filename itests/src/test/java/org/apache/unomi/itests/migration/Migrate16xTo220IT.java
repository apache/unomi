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
 * limitations under the License
 */
package org.apache.unomi.itests.migration;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.api.*;
import org.apache.unomi.itests.BaseIT;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.apache.unomi.shell.migration.utils.MigrationUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Migrate16xTo220IT extends BaseIT {

    private int eventCount = 0;
    private int sessionCount = 0;

    private static final int NUMBER_DUPLICATE_SESSIONS = 3;
    private static final int NUMBER_PERSONASESSIONS = 2;
    @Override
    @Before
    public void waitForStartup() throws InterruptedException {

        // Restore snapshot from 1.6.x
        try (CloseableHttpClient httpClient = HttpUtils.initHttpClient(true, null)) {
            // Create snapshot repo
            HttpUtils.executePutRequest(httpClient, "http://localhost:9400/_snapshot/snapshots_repository/", resourceAsString("migration/create_snapshots_repository.json"), null);
            // Get snapshot, insure it exists
            String snapshot = HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/_snapshot/snapshots_repository/snapshot_1.6.x", null);
            if (snapshot == null || !snapshot.contains("snapshot_1.6.x")) {
                throw new RuntimeException("Unable to retrieve 1.6.x snapshot for ES restore");
            }
            // Restore the snapshot
            HttpUtils.executePostRequest(httpClient, "http://localhost:9400/_snapshot/snapshots_repository/snapshot_1.6.x/_restore?wait_for_completion=true", "{}", null);
            fillNumberEventAndSessionBeforeMigration(httpClient);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Do migrate the data set
        String commandResults = executeCommand("unomi:migrate 1.6.0 true");

        // Prin the resulted output in the karaf shell directly
        System.out.println("Migration command output results:");
        System.out.println(commandResults);

        // Call super for starting Unomi and wait for the complete startup
        super.waitForStartup();
    }

    @After
    public void cleanup() throws InterruptedException {
        removeItems(Profile.class);
        removeItems(ProfileAlias.class);
        removeItems(Session.class);
        removeItems(Event.class);
        removeItems(Scope.class);
    }

    @Test
    public void checkMigratedData() throws Exception {
        checkMergedProfilesAliases();
        checkProfileInterests();
        checkScopeHaveBeenCreated();
        checkLoginEventWithScope();
        checkFormEventRestructured();
        checkViewEventRestructured();
        checkEventTypesNotPersistedAnymore();
        checkForMappingUpdates();
        checkEventSessionRollover2_2_0();
    }

    /**
     * Checks if at least the new index for events and sessions exists.
     * Also checks:
     * - duplicated sessions are correctly removed (-3 sessions in final count)
     * - persona sessions are now merged in session index due to index reduction in 2_2_0 (+2 sessions in final count)
     */
    private void checkEventSessionRollover2_2_0() throws IOException {
        Assert.assertTrue(MigrationUtils.indexExists(httpClient, "http://localhost:9400", "context-event-000001"));
        Assert.assertTrue(MigrationUtils.indexExists(httpClient, "http://localhost:9400", "context-session-000001"));

        int newEventcount = 0;
        for (String eventIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, "http://localhost:9400", "context-event-0")) {
            JsonNode jsonNode = objectMapper.readTree(HttpUtils.executePostRequest(httpClient, "http://localhost:9400" + "/" + eventIndex + "/_count", resourceAsString("migration/match_all_body_request.json"), null));
            newEventcount += jsonNode.get("count").asInt();
        }

        int newSessioncount = 0;
        for (String sessionIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, "http://localhost:9400", "context-session-0")) {
            JsonNode jsonNode = objectMapper.readTree(HttpUtils.executePostRequest(httpClient, "http://localhost:9400" + "/" + sessionIndex + "/_count", resourceAsString("migration/match_all_body_request.json"), null));
            newSessioncount += jsonNode.get("count").asInt();
        }
        Assert.assertEquals(eventCount, newEventcount);
        Assert.assertEquals(sessionCount - NUMBER_DUPLICATE_SESSIONS + NUMBER_PERSONASESSIONS, newSessioncount);
    }

    /**
     * Multiple index mappings have been update, check a simple check that after migration those mappings contains the latest modifications.
     */
    private void checkForMappingUpdates() throws IOException {
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/context-systemitems/_mapping", null).contains("\"match\":\"*\",\"match_mapping_type\":\"string\",\"mapping\":{\"analyzer\":\"folding\""));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/context-systemitems/_mapping", null).contains("\"condition\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/context-systemitems/_mapping", null).contains("\"entryCondition\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/context-systemitems/_mapping", null).contains("\"parentCondition\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/context-systemitems/_mapping", null).contains("\"startEvent\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/context-systemitems/_mapping", null).contains("\"data\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/context-systemitems/_mapping", null).contains("\"parameterValues\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/context-profile/_mapping", null).contains("\"interests\":{\"type\":\"nested\""));
        for (String eventIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, "http://localhost:9400", "context-event-")) {
            Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, "http://localhost:9400/" + eventIndex + "/_mapping", null).contains("\"flattenedProperties\":{\"type\":\"flattened\"}"));
        }
    }

    /**
     * Data set contains a form event (id: 7b55b4fd-5ff0-4a85-9dc4-ffde322a1de6) with this data:
     * {
     *   "properties": {
     *     "pets": "cat",
     *     "firstname": "foo",
     *     "sports": [
     *       "football",
     *       "tennis"
     *     ],
     *     "city": "Berlin",
     *     "age": "15",
     *     "email": "foo@bar.fr",
     *     "drone": "dewey",
     *     "lastname": "bar",
     *     "contactMethod": [
     *       "postalMethod",
     *       "phoneMethod"
     *     ]
     *   }
     * }
     */
    private void checkFormEventRestructured() {
        List<Event> events = persistenceService.query("eventType", "form", null, Event.class);
        for (Event formEvent : events) {
            Assert.assertEquals(0, formEvent.getProperties().size());
            Map<String, Object> fields = (Map<String, Object>) formEvent.getFlattenedProperties().get("fields");
            Assert.assertTrue(fields.size() > 0);

            if (Objects.equals(formEvent.getItemId(), "7b55b4fd-5ff0-4a85-9dc4-ffde322a1de6")) {
                // check singled valued
                Assert.assertEquals("cat", fields.get("pets"));
                // check multi-valued
                List<String> sports = (List<String>) fields.get("sports");
                Assert.assertEquals(2, sports.size());
                Assert.assertTrue(sports.contains("football"));
                Assert.assertTrue(sports.contains("tennis"));
            }
        }
    }

    private void checkLoginEventWithScope() {
        List<Event> events = persistenceService.query("eventType", "view", null, Event.class);
        List<String> digitallLoginEvent = Arrays.asList("4054a3e0-35ef-4256-999b-b9c05c1209f1", "f3f71ff8-2d6d-4b6c-8bdc-cb39905cddfe", "ff24ae6f-5a98-421e-aeb0-e86855b462ff");
        for (Event loginEvent : events) {
            if (loginEvent.getItemId().equals("5c4ac1df-f42b-4117-9432-12fdf9ecdf98")) {
                Assert.assertEquals(loginEvent.getScope(), "systemsite");
                Assert.assertEquals(loginEvent.getTarget().getScope(), "systemsite");
                Assert.assertEquals(loginEvent.getSource().getScope(), "systemsite");
            }
            if (digitallLoginEvent.contains(loginEvent.getItemId())) {
                Assert.assertEquals(loginEvent.getScope(), "digitall");
                Assert.assertEquals(loginEvent.getTarget().getScope(), "digitall");
                Assert.assertEquals(loginEvent.getSource().getScope(), "digitall");
            }
        }
    }

    /**
     * Data set contains a view event (id: a4aa836b-c437-48ef-be02-6fbbcba3a1de) with two interests: football:50 and basketball:30
     * Data set contains a view event (id: 34d53399-f173-451f-8d48-f34f5d9618a9) with two URL Parameters: paramerter_test:value, multiple_paramerter_test:[value1, value2]
     */
    private void checkViewEventRestructured() {
        List<Event> events = persistenceService.query("eventType", "view", null, Event.class);
        for (Event viewEvent : events) {

            // check interests
            if (Objects.equals(viewEvent.getItemId(), "a4aa836b-c437-48ef-be02-6fbbcba3a1de")) {
                CustomItem target = (CustomItem) viewEvent.getTarget();
                Assert.assertNull(target.getProperties().get("interests"));
                Map<String, Object> interests = (Map<String, Object>) viewEvent.getFlattenedProperties().get("interests");
                Assert.assertEquals(30, interests.get("basketball"));
                Assert.assertEquals(50, interests.get("football"));
            }

            // check URL parameters
            if (Objects.equals(viewEvent.getItemId(), "34d53399-f173-451f-8d48-f34f5d9618a9")) {
                CustomItem target = (CustomItem) viewEvent.getTarget();
                Map<String, Object> pageInfo = (Map<String, Object>) target.getProperties().get("pageInfo");
                Assert.assertNull(pageInfo.get("parameters"));
                Map<String, Object> parameters = (Map<String, Object>) viewEvent.getFlattenedProperties().get("URLParameters");
                Assert.assertEquals("value", parameters.get("paramerter_test"));
                List<String> multipleParameterTest = (List<String>) parameters.get("multiple_paramerter_test");
                Assert.assertEquals(2, multipleParameterTest.size());
                Assert.assertTrue(multipleParameterTest.contains("value1"));
                Assert.assertTrue(multipleParameterTest.contains("value2"));
            }
        }
    }


    /**
     * Data set contains 2 events that are not persisted anymore:
     * One updateProperties event
     * One sessionCreated event
     * This test ensures that both have been removed.
     */
    private void checkEventTypesNotPersistedAnymore() {
        Assert.assertEquals(0, persistenceService.query("eventType", "updateProperties", null, Event.class).size());
        Assert.assertEquals(0, persistenceService.query("eventType", "sessionCreated", null, Event.class).size());
    }

    /**
     * Data set contains multiple events, this test is generic enough to ensure all existing events have the scope created correctly
     * So the data set can contain multiple different scope it's not a problem.
     */
    private void checkScopeHaveBeenCreated() {
        // check that the scope mySite have been created based on the previous existings events
        Map<String, Long> existingScopesFromEvents = persistenceService.aggregateWithOptimizedQuery(null, new TermsAggregate("scope"), Event.ITEM_TYPE);
        for (String scopeFromEvents : existingScopesFromEvents.keySet()) {
            if (!Objects.equals(scopeFromEvents, "_filtered")) {
                Scope scope = scopeService.getScope(scopeFromEvents);
                Assert.assertNotNull(String.format("Unable to find registered scope %s", scopeFromEvents), scope);
            }
        }
    }

    /**
     * Data set contains a profile (id: e67ecc69-a7b3-47f1-b91f-5d6e7b90276e) with two interests: football:50 and basketball:30
     * Also it's first name is test_profile
     */
    private void checkProfileInterests() {
        // check that the test_profile interests have been migrated to new data structure
        Profile profile = persistenceService.load("e67ecc69-a7b3-47f1-b91f-5d6e7b90276e", Profile.class);
        Assert.assertEquals("test_profile", profile.getProperty("firstName"));

        List<Map<String, Object>> interests = (List<Map<String, Object>>) profile.getProperty("interests");
        Assert.assertEquals(2, interests.size());
        for (Map<String, Object> interest : interests) {
            if ("basketball".equals(interest.get("key"))) {
                Assert.assertEquals(30, interest.get("value"));
            }
            if ("football".equals(interest.get("key"))) {
                Assert.assertEquals(50, interest.get("value"));
            }
        }
    }

    /**
     * Data set contains a master profile: 468ca2bf-7d24-41ea-9ef4-5b96f78207e4
     * And two profiles that have been merged with this master profile: c33dec90-ffc9-4484-9e61-e42c323f268f and ac5b6b0f-afce-4c4f-9391-4ff0b891b254
     */
    private void checkMergedProfilesAliases() {
        // Check that both profiles aliases have been created and the merged profiles are now deleted.
        List<String> mergedProfiles = Arrays.asList("c33dec90-ffc9-4484-9e61-e42c323f268f", "ac5b6b0f-afce-4c4f-9391-4ff0b891b254");
        String masterProfile = "468ca2bf-7d24-41ea-9ef4-5b96f78207e4";
        for (String mergedProfile : mergedProfiles) {
            // control the created alias
            ProfileAlias alias = persistenceService.load(mergedProfile, ProfileAlias.class);
            Assert.assertNotNull(alias);
            Assert.assertEquals(alias.getProfileID(), masterProfile);

            // control the merged profile do not exist anymore
            Assert.assertNull(persistenceService.load(mergedProfile, Profile.class));
        }

        // Check master profile still exists a no alias have been created for him
        Assert.assertNotNull(persistenceService.load(masterProfile, Profile.class));
        Assert.assertNull(persistenceService.load(masterProfile, ProfileAlias.class));
    }

    private void fillNumberEventAndSessionBeforeMigration(CloseableHttpClient httpClient) {
        try {
            for (String eventIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, "http://localhost:9400", "context-event-date")) {
                JsonNode jsonNode = objectMapper.readTree(HttpUtils.executePostRequest(httpClient, "http://localhost:9400" + "/" + eventIndex + "/_count", resourceAsString("migration/must_not_match_some_eventype_body.json"), null));
                eventCount += jsonNode.get("count").asInt();
            }

            for (String eventIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, "http://localhost:9400", "context-session-date")) {
                JsonNode jsonNode = objectMapper.readTree(HttpUtils.executePostRequest(httpClient, "http://localhost:9400" + "/" + eventIndex + "/_count", resourceAsString("migration/match_all_body_request.json"), null));
                sessionCount += jsonNode.get("count").asInt();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }
}
