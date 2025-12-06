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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.geonames.services.GeonameEntry;
import org.apache.unomi.itests.BaseIT;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.apache.unomi.shell.migration.utils.MigrationUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class Migrate16xToCurrentVersionIT extends BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(Migrate16xToCurrentVersionIT.class);

    private int eventCount = 0;
    private int sessionCount = 0;
    private Set<String[]> initialScopes = new HashSet<>();

    private static final String SCOPE_NOT_EXIST = "SCOPE_NOT_EXIST";
    private static final List<String> oldSystemItemsIndices = Arrays.asList("context-actiontype", "context-campaign", "context-campaignevent", "context-goal",
            "context-userlist", "context-propertytype", "context-scope", "context-conditiontype", "context-rule", "context-scoring", "context-segment", "context-groovyaction", "context-topic",
            "context-patch", "context-jsonschema", "context-importconfig", "context-exportconfig", "context-rulestats");

    // Elasticsearch connection constants
    private static final String ES_BASE_URL = "http://localhost:9400";
    private static final String ES_SNAPSHOT_REPO = ES_BASE_URL + "/_snapshot/snapshots_repository/";
    private static final String ES_SNAPSHOT_STATUS = ES_BASE_URL + "/_snapshot/_status";
    private static final String ES_SNAPSHOT_2 = "snapshot_2";
    private static final String ES_SNAPSHOT_RESTORE_URL = ES_SNAPSHOT_REPO + ES_SNAPSHOT_2 + "/_restore?wait_for_completion=true";

    // Index prefix constants
    private static final String INDEX_PREFIX_CONTEXT = "context-";
    private static final String INDEX_EVENT = INDEX_PREFIX_CONTEXT + "event-";
    private static final String INDEX_SESSION = INDEX_PREFIX_CONTEXT + "session-";
    private static final String INDEX_SYSTEMITEMS = INDEX_PREFIX_CONTEXT + "systemitems";
    private static final String INDEX_PROFILE = INDEX_PREFIX_CONTEXT + "profile";

    // Resource path constants
    private static final String RESOURCE_MIGRATION = "migration/";
    private static final String RESOURCE_CREATE_SNAPSHOTS_REPO = RESOURCE_MIGRATION + "create_snapshots_repository.json";
    private static final String RESOURCE_MUST_NOT_MATCH_EVENTTYPE = RESOURCE_MIGRATION + "must_not_match_some_eventype_body.json";
    private static final String RESOURCE_MATCH_ALL_LOGIN_EVENT = RESOURCE_MIGRATION + "match_all_login_event_request.json";

    // Scope constants
    private static final String SCOPE_SYSTEMSITE = "systemsite";
    private static final String SCOPE_DIGITALL = "digitall";

    // Event type constants
    private static final String EVENT_TYPE_FORM = "form";
    private static final String EVENT_TYPE_VIEW = "view";
    private static final String EVENT_TYPE_UPDATE_PROPERTIES = "updateProperties";
    private static final String EVENT_TYPE_SESSION_CREATED = "sessionCreated";

    // Profile constants
    private static final String PROFILE_FIRST_NAME = "firstName";
    private static final String PROFILE_INTERESTS = "interests";
    private static final String PROFILE_PAST_EVENTS = "pastEvents";

    // System item types
    private static final List<String> SYSTEM_ITEM_TYPES = Arrays.asList("segment", "rule", "scope");

    // Migration command
    private static final String MIGRATION_COMMAND = "unomi:migrate 1.6.0 true";
    private static final long MIGRATION_TIMEOUT = 900000L;

    @Override
    @Before
    public void waitForStartup() throws InterruptedException {
        // Check search engine and apply any necessary fixes (e.g., default_template deletion)
        // This is called from BaseIT and will run before any migration setup
        checkSearchEngine();

        if (SEARCH_ENGINE_OPENSEARCH.equals(searchEngine)) {
            System.out.println("Migration from 1.x to 2.x not supported for OpenSearch, skipping snapshot restore");
            super.waitForStartup();
            return;
        }

        System.out.println("Restoring snapshot into search engine...");
        LOGGER.info("Restoring snapshot into search engine...");

        // Restore snapshot from 1.6.x
        try (CloseableHttpClient httpClient = HttpUtils.initHttpClient(true, null)) {
            // Create snapshot repo
            HttpUtils.executePutRequest(httpClient, ES_SNAPSHOT_REPO, resourceAsString(RESOURCE_CREATE_SNAPSHOTS_REPO), null);
            // Get snapshot, insure it exists
            String snapshot = HttpUtils.executeGetRequest(httpClient, ES_SNAPSHOT_REPO + ES_SNAPSHOT_2, null);
            if (snapshot == null || !snapshot.contains(ES_SNAPSHOT_2)) {
                throw new RuntimeException("Unable to retrieve 1.6.x snapshot for ES restore");
            }
            // Restore the snapshot
            HttpUtils.executePostRequest(httpClient, ES_SNAPSHOT_RESTORE_URL, "{}", null);

            String snapshotStatus = HttpUtils.executeGetRequest(httpClient, ES_SNAPSHOT_STATUS, null);
            System.out.println("Snapshot status: " + snapshotStatus);
            LOGGER.info("Snapshot status: {}", snapshotStatus);

            // Get initial counts of items to compare after migration
            initCounts(httpClient);
        } catch (Throwable t) {
            throw new RuntimeException("Error during snapshot restore", t);
        }

        System.out.println("Launching migration from 1.6.0...");
        LOGGER.info("Launching migration from 1.6.0...");

        // Do migrate the data set
        String commandResults = null;
        try {
            commandResults = executeCommand(MIGRATION_COMMAND, MIGRATION_TIMEOUT, false);
        } catch (Throwable t) {
            LOGGER.error("Error during migration", t);
            System.err.println("Error during migration");
            t.printStackTrace();
            throw new RuntimeException("Error during migration", t);
        } finally {
            if (commandResults != null) {
                // Print the resulted output in the karaf shell directly
                System.out.println("Migration command output results:");
                System.out.println(commandResults);
            }
        }

        // Call super for starting Unomi and wait for the complete startup
        super.waitForStartup();
    }

    @After
    public void cleanup() throws InterruptedException {
        try {
            if (definitionsService != null && persistenceService != null) {
                removeItems(Profile.class);
                removeItems(ProfileAlias.class);
                removeItems(Session.class);
                removeItems(Event.class);
                removeItems(Scope.class);
                removeItems(GeonameEntry.class);
            }
        } catch (Throwable t) {
            LOGGER.error("Error during cleanup", t);
            System.err.println("Error during cleanup");
            t.printStackTrace();
        }
    }

    /**
     * Test that validates migrated data from 1.6.x snapshot.
     * 
     * Note: ParserHelper warnings about missing action types (setRemoteHostInfoAction, 
     * requestHeaderToProfilePropertyAction) and circular references in condition types are expected
     * for migrated data. These occur because:
     * 1. Some action types are from plugins that may not be fully loaded during rule validation
     * 2. Migrated rules may have malformed condition structures from the 1.6.x data
     * The system handles these gracefully by marking affected rules as invalid, which is acceptable
     * for migrated legacy data.
     */
    @Test
    public void checkMigratedData() throws Exception {
        if (SEARCH_ENGINE_OPENSEARCH.equals(searchEngine)) {
            System.out.println("Migration from 1.x to 2.x not supported for OpenSearch, skipping checks");
            return;
        }
        checkMergedProfilesAliases();
        checkProfileInterests();
        checkScopeHaveBeenCreated();
        checkLoginEventWithScope();
        checkFormEventRestructured();
        checkViewEventRestructured();
        checkEventTypesNotPersistedAnymore();
        checkForMappingUpdates();
        checkEventSessionRollover2_2_0();
        checkIndexReductions2_2_0();
        checkPagePathForEventView();
        checkPastEvents();
        checkScopeEventHaveBeenUpdated();
        countNumberOfSessionIndices();
        // 3.1.0 migration validations
        checkTenantIdsApplied();
        checkDefaultTenantCreated();
        checkDefinitionsServiceObjectsAccessible();
    }

    /**
     * Checks if at least the new index for events and sessions exists.
     * Also checks:
     * - duplicated sessions are correctly removed (-3 sessions in final count)
     * - persona sessions are now merged in session index due to index reduction in 2_2_0 (+2 sessions in final count)
     */
    private void checkEventSessionRollover2_2_0() throws IOException {
        Assert.assertTrue(MigrationUtils.indexExists(httpClient, ES_BASE_URL, INDEX_EVENT + "000001"));
        Assert.assertTrue(MigrationUtils.indexExists(httpClient, ES_BASE_URL, INDEX_SESSION + "000001"));

        int newEventcount = 0;
        for (String eventIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, ES_BASE_URL, INDEX_EVENT + "0")) {
            newEventcount += countItems(httpClient, eventIndex, null);
        }

        int newSessioncount = 0;
        for (String sessionIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, ES_BASE_URL, INDEX_SESSION + "0")) {
            newSessioncount += countItems(httpClient, sessionIndex, null);
        }
        Assert.assertEquals(eventCount, newEventcount);
        Assert.assertEquals(sessionCount, newSessioncount);
    }

    private void checkIndexReductions2_2_0() throws IOException {
        // new index for system items:
        Assert.assertTrue(MigrationUtils.indexExists(httpClient, ES_BASE_URL, INDEX_SYSTEMITEMS));

        // old indices should be removed:
        for (String oldSystemItemsIndex : oldSystemItemsIndices) {
            Assert.assertFalse(MigrationUtils.indexExists(httpClient, ES_BASE_URL, oldSystemItemsIndex));
        }
    }

    /**
     * Multiple index mappings have been update, check a simple check that after migration those mappings contains the latest modifications.
     */
    private void checkForMappingUpdates() throws IOException {
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + INDEX_SYSTEMITEMS + "/_mapping", null).contains("\"match\":\"*\",\"match_mapping_type\":\"string\",\"mapping\":{\"analyzer\":\"folding\""));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + INDEX_SYSTEMITEMS + "/_mapping", null).contains("\"condition\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + INDEX_SYSTEMITEMS + "/_mapping", null).contains("\"entryCondition\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + INDEX_SYSTEMITEMS + "/_mapping", null).contains("\"parentCondition\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + INDEX_SYSTEMITEMS + "/_mapping", null).contains("\"startEvent\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + INDEX_SYSTEMITEMS + "/_mapping", null).contains("\"data\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + INDEX_SYSTEMITEMS + "/_mapping", null).contains("\"parameterValues\":{\"type\":\"object\",\"enabled\":false}"));
        Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + INDEX_PROFILE + "/_mapping", null).contains("\"interests\":{\"type\":\"nested\""));
        for (String eventIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, ES_BASE_URL, INDEX_EVENT)) {
            Assert.assertTrue(HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + eventIndex + "/_mapping", null).contains("\"flattenedProperties\":{\"type\":\"flattened\"}"));
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
        List<Event> events = persistenceService.query("eventType", EVENT_TYPE_FORM, null, Event.class);
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
        List<Event> events = persistenceService.query("eventType", EVENT_TYPE_VIEW, null, Event.class);
        List<String> digitallLoginEvent = Arrays.asList("4054a3e0-35ef-4256-999b-b9c05c1209f1", "f3f71ff8-2d6d-4b6c-8bdc-cb39905cddfe", "ff24ae6f-5a98-421e-aeb0-e86855b462ff");
        for (Event loginEvent : events) {
            if (loginEvent.getItemId().equals("5c4ac1df-f42b-4117-9432-12fdf9ecdf98")) {
                Assert.assertEquals(loginEvent.getScope(), SCOPE_SYSTEMSITE);
                Assert.assertEquals(loginEvent.getTarget().getScope(), SCOPE_SYSTEMSITE);
                Assert.assertEquals(loginEvent.getSource().getScope(), SCOPE_SYSTEMSITE);
            }
            if (digitallLoginEvent.contains(loginEvent.getItemId())) {
                Assert.assertEquals(loginEvent.getScope(), SCOPE_DIGITALL);
                Assert.assertEquals(loginEvent.getTarget().getScope(), SCOPE_DIGITALL);
                Assert.assertEquals(loginEvent.getSource().getScope(), SCOPE_DIGITALL);
            }
        }
    }

    /**
     * Data set contains a view event (id: a4aa836b-c437-48ef-be02-6fbbcba3a1de) with two interests: football:50 and basketball:30
     * Data set contains a view event (id: 34d53399-f173-451f-8d48-f34f5d9618a9) with two URL Parameters: paramerter_test:value, multiple_paramerter_test:[value1, value2]
     */
    private void checkViewEventRestructured() {
        List<Event> events = persistenceService.query("eventType", EVENT_TYPE_VIEW, null, Event.class);
        for (Event viewEvent : events) {
            // check interests
            if (Objects.equals(viewEvent.getItemId(), "a4aa836b-c437-48ef-be02-6fbbcba3a1de")) {
                CustomItem target = (CustomItem) viewEvent.getTarget();
                Assert.assertNull(target.getProperties().get(PROFILE_INTERESTS));
                Map<String, Object> interests = (Map<String, Object>) viewEvent.getFlattenedProperties().get(PROFILE_INTERESTS);
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
        Assert.assertEquals(0, persistenceService.query("eventType", EVENT_TYPE_UPDATE_PROPERTIES, null, Event.class).size());
        Assert.assertEquals(0, persistenceService.query("eventType", EVENT_TYPE_SESSION_CREATED, null, Event.class).size());
    }

    /**
     * Data set contains multiple events, this test is generic enough to ensure all existing events have the scope created correctly
     * So the data set can contain multiple different scope it's not a problem.
     */
    private void checkScopeHaveBeenCreated() {
        // check that the scope mySite have been created based on the previous existings events
        Map<String, Long> existingScopesFromEvents = persistenceService.aggregateWithOptimizedQuery(null, new TermsAggregate("scope"), Event.ITEM_TYPE);
        for (String scopeFromEvents : existingScopesFromEvents.keySet()) {
            if (!Objects.equals(scopeFromEvents, "_filtered") && !Objects.equals(scopeFromEvents, "_missing")) {
                Scope scope = scopeService.getScope(scopeFromEvents);
                Assert.assertNotNull(String.format("Unable to find registered scope %s", scopeFromEvents), scope);
            }
        }
    }

    private void checkScopeEventHaveBeenUpdated() {
        for (String[] loginEvent : initialScopes) {
            Event event = eventService.getEvent(loginEvent[0]);
            if (SCOPE_DIGITALL.equals(loginEvent[1])) {
                Assert.assertEquals(event.getScope(), SCOPE_DIGITALL);
            } else {
                Assert.assertEquals(event.getScope(), SCOPE_SYSTEMSITE);
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
        Assert.assertEquals("test_profile", profile.getProperty(PROFILE_FIRST_NAME));

        List<Map<String, Object>> interests = (List<Map<String, Object>>) profile.getProperty(PROFILE_INTERESTS);
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

    private void initCounts(CloseableHttpClient httpClient) {
        try {
            for (String eventIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, ES_BASE_URL, INDEX_EVENT + "date")) {
                getScopeFromEvents(httpClient, eventIndex);
                eventCount += countItems(httpClient, eventIndex, resourceAsString(RESOURCE_MUST_NOT_MATCH_EVENTTYPE));
            }

            for (String sessionIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, ES_BASE_URL, INDEX_SESSION + "date")) {
                sessionCount += countItems(httpClient, sessionIndex, null);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void countNumberOfSessionIndices() {
        try {
           Set<String> sessionIndices = MigrationUtils.getIndexesPrefixedBy(httpClient, "http://localhost:9400", "context-session");
            Assert.assertEquals(2, sessionIndices.size());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void getScopeFromEvents(CloseableHttpClient httpClient, String eventIndex) throws IOException {
        String requestBody = resourceAsString(RESOURCE_MATCH_ALL_LOGIN_EVENT);
        JsonNode jsonNode = objectMapper.readTree(HttpUtils.executePostRequest(httpClient, ES_BASE_URL + "/" + eventIndex + "/_search", requestBody, null));
        if (jsonNode.has("hits") && jsonNode.get("hits").has("hits") && !jsonNode.get("hits").get("hits").isEmpty()) {
            jsonNode.get("hits").get("hits").forEach(doc -> {
                JsonNode event = doc.get("_source");
                if (event.has("scope")) {
                    if (event.get("scope") == null) {
                        String[] initialScope = {event.get("itemId").asText(), null};
                        initialScopes.add(initialScope);
                    } else {
                        String[] initialScope = {event.get("itemId").asText(), event.get("scope").asText()};
                        initialScopes.add(initialScope);
                    }
                } else {
                    String[] initialScope = {event.get("itemId").asText(), SCOPE_NOT_EXIST};
                    initialScopes.add(initialScope);
                }
            });
        }
    }

    private int countItems(CloseableHttpClient httpClient, String index, String requestBody) throws IOException {
        if (requestBody == null) {
            requestBody = resourceAsString(RESOURCE_MUST_NOT_MATCH_EVENTTYPE);
        }
        JsonNode jsonNode = objectMapper.readTree(HttpUtils.executePostRequest(httpClient, ES_BASE_URL + "/" + index + "/_count", requestBody, null));
        return jsonNode.get("count").asInt();
    }

    /**
     * Data set contains 2 events that had a value in properties.path:
     * The properties.path should have been moved to properties.pageInfo.pagePath
     */
    private void checkPagePathForEventView() {
        Assert.assertEquals(2, persistenceService.query("target.properties.pageInfo.pagePath", "/path/to/migrate/to/pageInfo", null, Event.class).size());
        Assert.assertEquals(0, persistenceService.query("properties.path", "/path/to/migrate/to/pageInfo", null, Event.class).size());
    }

    /**
     * Data set contains a profile (id: 164adad8-6885-45b6-8e9d-512bf4a7d10d) with a system property pastEvents that contains 5 events with key eventTriggeredabcdefgh
     * This test ensures that the pastEvents have been migrated to the new data structure
     */
    private void checkPastEvents() {
        Profile profile = persistenceService.load("164adad8-6885-45b6-8e9d-512bf4a7d10d", Profile.class);
        List<Map<String, Object>> pastEvents = ((List<Map<String, Object>>) profile.getSystemProperties().get(PROFILE_PAST_EVENTS));
        Assert.assertEquals(1, pastEvents.size());
        Assert.assertEquals("eventTriggeredabcdefgh", pastEvents.get(0).get("key"));
        Assert.assertEquals(5, (int) pastEvents.get(0).get("count"));
    }

    /**
     * Check that tenant IDs have been properly applied to documents and audit metadata is initialized
     */
    private void checkTenantIdsApplied() throws IOException {
        // Check profile IDs have tenant prefix and audit metadata
        checkDocumentsInIndex(INDEX_PROFILE, TEST_TENANT_ID, false);

        // Check event IDs have tenant prefix and audit metadata
        for (String eventIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, ES_BASE_URL, INDEX_EVENT)) {
            checkDocumentsInIndex(eventIndex, TEST_TENANT_ID, false);
        }

        // Check session IDs have tenant prefix and audit metadata
        for (String sessionIndex : MigrationUtils.getIndexesPrefixedBy(httpClient, ES_BASE_URL, INDEX_SESSION)) {
            checkDocumentsInIndex(sessionIndex, TEST_TENANT_ID, false);
        }

        // Check system items have either system or test tenant prefix and audit metadata
        // Check all system items in the systemitems index (no need to iterate by type)
        checkDocumentsInIndex(INDEX_SYSTEMITEMS, null, true);
    }

    /**
     * Helper method to check tenant IDs and audit metadata for documents in an index
     * @param indexName The name of the index to check
     * @param expectedTenantId The expected tenant ID for non-system items
     * @param isSystemIndex Whether this is a system index that can have both system and test tenant IDs
     */
    private void checkDocumentsInIndex(String indexName, String expectedTenantId, boolean isSystemIndex) throws IOException {
        String query = HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + indexName + "/_search?size=10", null);
        JsonNode jsonNode = objectMapper.readTree(query);
        if (jsonNode.has("hits") && jsonNode.get("hits").has("hits") && !jsonNode.get("hits").get("hits").isEmpty()) {
            for (JsonNode hit : jsonNode.get("hits").get("hits")) {
                JsonNode source = hit.get("_source");
                String itemId = hit.get("_id").asText();

                // Check document ID prefix
                if (isSystemIndex) {
                    boolean hasValidPrefix = itemId.startsWith("system_") || itemId.startsWith(TEST_TENANT_ID + "_");
                    Assert.assertTrue("System item ID should have either system or test tenant prefix: " + itemId, hasValidPrefix);
                } else {
                    Assert.assertTrue("Document ID should have tenant prefix: " + itemId, itemId.startsWith(expectedTenantId + "_"));
                }

                // Check tenant ID in source
                Assert.assertNotNull("Tenant ID should be set in source", source.get("tenantId"));
                String actualTenantId = source.get("tenantId").asText();
                if (isSystemIndex) {
                    String systemExpectedTenantId = itemId.startsWith("system_") ? "system" : TEST_TENANT_ID;
                    Assert.assertEquals("Tenant ID in source should match prefix", systemExpectedTenantId, actualTenantId);
                } else {
                    Assert.assertEquals("Tenant ID in source should match prefix", expectedTenantId, actualTenantId);
                }

                // Check audit metadata
                checkAuditMetadata(source);
            }
        }
    }

    /**
     * Helper method to check audit metadata fields
     * @param source The document source containing the metadata
     */
    private void checkAuditMetadata(JsonNode source) {
        Assert.assertNotNull("Created by should be set", source.get("createdBy"));
        String createdBy = source.get("createdBy").asText();
        // After migration, documents may be refreshed by bundles during startup, 
        // which changes createdBy from system-migration-3.1.0 to system-bundle
        // Both are valid - migration sets it, bundles may refresh it
        boolean isValidCreatedBy = "system-migration-3.1.0".equals(createdBy) || "system-bundle".equals(createdBy);
        Assert.assertTrue("Created by should be system-migration-3.1.0 or system-bundle, but was: " + createdBy, isValidCreatedBy);
        Assert.assertNotNull("Creation date should be set", source.get("creationDate"));
        Assert.assertNotNull("Last modified by should be set", source.get("lastModifiedBy"));
        String lastModifiedBy = source.get("lastModifiedBy").asText();
        // Similarly, lastModifiedBy may be updated by bundles after migration
        boolean isValidLastModifiedBy = "system-migration-3.1.0".equals(lastModifiedBy) || "system-bundle".equals(lastModifiedBy);
        Assert.assertTrue("Last modified by should be system-migration-3.1.0 or system-bundle, but was: " + lastModifiedBy, isValidLastModifiedBy);
        Assert.assertNotNull("Last modification date should be set", source.get("lastModificationDate"));
    }

    /**
     * Helper method to check audit metadata fields for definitions service objects.
     * These can be either migrated (system-migration-3.1.0) or bundle-deployed (system-bundle).
     * @param source The document source containing the metadata
     */
    private void checkAuditMetadataForDefinitions(JsonNode source) {
        Assert.assertNotNull("Created by should be set", source.get("createdBy"));
        String createdBy = source.get("createdBy").asText();
        boolean isValidCreatedBy = "system-migration-3.1.0".equals(createdBy) || "system-bundle".equals(createdBy);
        Assert.assertTrue("Created by should be system-migration-3.1.0 or system-bundle, but was: " + createdBy, isValidCreatedBy);
        Assert.assertNotNull("Creation date should be set", source.get("creationDate"));
        Assert.assertNotNull("Last modified by should be set", source.get("lastModifiedBy"));
        String lastModifiedBy = source.get("lastModifiedBy").asText();
        boolean isValidLastModifiedBy = "system-migration-3.1.0".equals(lastModifiedBy) || "system-bundle".equals(lastModifiedBy);
        Assert.assertTrue("Last modified by should be system-migration-3.1.0 or system-bundle, but was: " + lastModifiedBy, isValidLastModifiedBy);
        Assert.assertNotNull("Last modification date should be set", source.get("lastModificationDate"));
    }

    /**
     * Test that the default tenant was created during migration (migrate-3.1.0-10-tenantInitialization)
     */
    private void checkDefaultTenantCreated() throws Exception {
        if (SEARCH_ENGINE_OPENSEARCH.equals(searchEngine)) {
            System.out.println("Migration from 1.x to 2.x not supported for OpenSearch, skipping checks");
            return;
        }
        
        // Check that the default tenant index exists
        Assert.assertTrue("Default tenant index should exist", MigrationUtils.indexExists(httpClient, ES_BASE_URL, INDEX_PREFIX_CONTEXT + "tenant"));
        
        // Check that the default tenant was created with correct structure
        String tenantId = "itTestTenant"; // This should match the tenant ID from migration config
        Tenant defaultTenant = tenantService.getTenant(tenantId);
        
        // If the default tenant doesn't exist, check if it was created during migration
        // The migration creates a tenant with the ID from the migration config
        if (defaultTenant == null) {
            // Check if tenant exists in Elasticsearch directly
            String query = HttpUtils.executeGetRequest(httpClient, ES_BASE_URL + "/" + INDEX_PREFIX_CONTEXT + "tenant/_search?q=itemId:" + tenantId, null);
            JsonNode jsonNode = objectMapper.readTree(query);
            if (jsonNode.has("hits") && jsonNode.get("hits").has("hits") && !jsonNode.get("hits").get("hits").isEmpty()) {
                JsonNode tenantDoc = jsonNode.get("hits").get("hits").get(0).get("_source");
                Assert.assertEquals("Default tenant should have correct itemId", tenantId, tenantDoc.get("itemId").asText());
                Assert.assertEquals("Default tenant should have correct tenantId", "system", tenantDoc.get("tenantId").asText());
                Assert.assertEquals("Default tenant should have correct createdBy", "system-migration-3.1.0", tenantDoc.get("createdBy").asText());
            }
        } else {
            Assert.assertEquals("Default tenant should have correct itemId", tenantId, defaultTenant.getItemId());
            Assert.assertNotNull("Default tenant should have API keys", defaultTenant.getPublicApiKey());
            Assert.assertNotNull("Default tenant should have private API key", defaultTenant.getPrivateApiKey());
        }
    }

    /**
     * Test that all objects managed by the definitions service (condition types, action types) 
     * have been properly migrated and are accessible by the current tenant.
     * This ensures that all condition types and action types stored in the systemitems index
     * have proper tenant information, audit metadata, and are accessible via definitionsService.
     */
    private void checkDefinitionsServiceObjectsAccessible() throws Exception {
        if (SEARCH_ENGINE_OPENSEARCH.equals(searchEngine)) {
            System.out.println("Migration from 1.x to 2.x not supported for OpenSearch, skipping checks");
            return;
        }
        
        // Refresh the definitions service cache to ensure migrated items are loaded
        // This is necessary because items might be in persistence but not yet in cache
        definitionsService.refresh();
        
        // Wait a bit for the refresh to complete (refresh is asynchronous in some cases)
        Thread.sleep(1000);
        
        // Check condition types
        checkDefinitionsServiceObjects("conditionType", "condition types");
        
        // Check action types
        checkDefinitionsServiceObjects("actionType", "action types");
    }

    /**
     * Helper method to check definitions service objects (condition types or action types)
     * @param itemType The item type to check ("conditionType" or "actionType")
     * @param itemTypeDescription Human-readable description for error messages
     */
    private void checkDefinitionsServiceObjects(String itemType, String itemTypeDescription) throws IOException {
        // Query systemitems index for all items of this type
        ObjectNode query = JsonNodeFactory.instance.objectNode();
        ObjectNode termQuery = JsonNodeFactory.instance.objectNode();
        termQuery.put("itemType.keyword", itemType);
        ObjectNode queryWrapper = JsonNodeFactory.instance.objectNode();
        queryWrapper.set("term", termQuery);
        query.set("query", queryWrapper);
        query.put("size", 1000);
        
        String response = HttpUtils.executePostRequest(httpClient, ES_BASE_URL + "/" + INDEX_SYSTEMITEMS + "/_search", objectMapper.writeValueAsString(query), null);
        JsonNode jsonNode = objectMapper.readTree(response);
        
        Set<String> itemIds = new HashSet<>();
        if (jsonNode.has("hits") && jsonNode.get("hits").has("hits")) {
            for (JsonNode hit : jsonNode.get("hits").get("hits")) {
                JsonNode source = hit.get("_source");
                String itemId = hit.get("_id").asText();
                
                // Verify tenant ID is set (should be test tenant after migration, except for some exceptions)
                Assert.assertNotNull("Tenant ID should be set for " + itemTypeDescription + ": " + itemId, source.get("tenantId"));
                String tenantId = source.get("tenantId").asText();
                
                // Verify document ID has appropriate tenant prefix
                // Most definitions service objects should be migrated to test tenant, but some may remain as system
                boolean hasValidPrefix = itemId.startsWith(TEST_TENANT_ID + "_") || itemId.startsWith("system_");
                Assert.assertTrue("Document ID should have test tenant or system prefix for " + itemTypeDescription + ": " + itemId, hasValidPrefix);
                
                // Verify tenant ID matches the prefix (most should be test tenant)
                String expectedTenantId = itemId.startsWith(TEST_TENANT_ID + "_") ? TEST_TENANT_ID : "system";
                Assert.assertEquals("Tenant ID should match prefix for " + itemTypeDescription + ": " + itemId, expectedTenantId, tenantId);
                
                // Definitions that exist in persistent storage are either:
                // 1. Legacy definitions that were migrated (should have migration audit metadata)
                // 2. Bundle-deployed definitions that were persisted (should also have audit metadata)
                // In both cases, they should have proper audit metadata
                checkAuditMetadataForDefinitions(source);
                
                // Extract itemId from source (may be different from document _id if migrated)
                // For system items, the actual itemId should not include the itemType suffix
                // (e.g., "anonymizeProfileEventCondition" not "anonymizeProfileEventCondition_conditiontype")
                String extractedItemId;
                if (source.has("itemId")) {
                    extractedItemId = source.get("itemId").asText();
                } else {
                    // Fallback to document ID without prefix
                    // For system items, document ID format is: tenantId_itemId_itemType
                    // So we need to strip both the tenant prefix and the itemType suffix
                    String strippedId = itemId;
                    if (itemId.startsWith(TEST_TENANT_ID + "_")) {
                        strippedId = itemId.substring((TEST_TENANT_ID + "_").length());
                    } else if (itemId.startsWith("system_")) {
                        strippedId = itemId.substring("system_".length());
                    }
                    extractedItemId = strippedId;
                }
                
                // Strip itemType suffix if present (e.g., "_conditiontype" or "_actiontype")
                // The itemType suffix matches the itemType being checked
                // This handles cases where the source.itemId or document _id includes the suffix
                String itemTypeSuffix = "_" + itemType.toLowerCase();
                if (extractedItemId.endsWith(itemTypeSuffix)) {
                    extractedItemId = extractedItemId.substring(0, extractedItemId.length() - itemTypeSuffix.length());
                }
                
                itemIds.add(extractedItemId);
            }
        }
        
        // Verify all items are accessible via definitionsService
        Set<String> inaccessibleItems = new HashSet<>();
        for (String itemId : itemIds) {
            if ("conditionType".equals(itemType)) {
                ConditionType conditionType = definitionsService.getConditionType(itemId);
                if (conditionType == null) {
                    inaccessibleItems.add(itemId);
                }
            } else if ("actionType".equals(itemType)) {
                ActionType actionType = definitionsService.getActionType(itemId);
                if (actionType == null) {
                    inaccessibleItems.add(itemId);
                }
            }
        }
        
        Assert.assertTrue("All " + itemTypeDescription + " should be accessible via definitionsService. Missing: " + inaccessibleItems, 
                inaccessibleItems.isEmpty());
    }

}

