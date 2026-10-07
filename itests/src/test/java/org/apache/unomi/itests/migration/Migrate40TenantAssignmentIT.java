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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.itests.BaseIT;
import org.apache.unomi.itests.persistence.SearchBackendIT;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.apache.unomi.shell.migration.utils.MigrationUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * UNOMI-997: after migration, shipped definitions and geonames land in system,
 * while user content with systemscope stays in the configured tenant.
 */
@Category(SearchBackendIT.class)
public class Migrate40TenantAssignmentIT extends BaseIT {

    private static final String SOURCE_INDEX = "context-unomi997source-systemitems";
    private static final String DEST_INDEX = "context-unomi997dest-systemitems";
    private static final String GEO_SOURCE = "context-unomi997source-geonameentry";
    private static final String GEO_DEST = "context-unomi997dest-geonameentry";
    private static final String DATA_SOURCE = "context-unomi997source-profile";
    private static final String DATA_DEST = "context-unomi997dest-profile";
    private static final String CAMEL_CASE_INDEX = "context-unomi997camelcase";

    private static final String MAPPING = "{"
            + "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},"
            + "\"mappings\":{\"properties\":{"
            + "\"itemId\":{\"type\":\"keyword\"},"
            + "\"itemType\":{\"type\":\"keyword\"},"
            + "\"scope\":{\"type\":\"keyword\"},"
            + "\"tenantId\":{\"type\":\"keyword\"}"
            + "}}}";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @After
    public void deleteTestIndices() throws Exception {
        if (!persistenceCapabilities().httpAdminApi()) {
            return;
        }
        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            for (String index : new String[]{SOURCE_INDEX, DEST_INDEX, GEO_SOURCE, GEO_DEST, DATA_SOURCE, DATA_DEST, CAMEL_CASE_INDEX}) {
                MigrationUtils.deleteIndex(httpClient, es, index);
            }
        }
    }

    @Test
    public void shippedDefinitionsListIsPresentInTheMigrationBundle() {
        Map<String, Set<String>> shipped = MigrationUtils.loadShippedDefinitionIds(migrationBundle());
        Assert.assertTrue(shipped.get("conditiontype").contains("booleancondition"));
        Assert.assertTrue(shipped.get("conditiontype").contains("pasteventcondition"));
        Assert.assertTrue(shipped.get("conditiontype").contains("profilepropertycondition"));
        Assert.assertFalse("user content must not be in the shipped list by id alone",
                shipped.get("rule").contains("my-user-rule"));
    }

    @Test
    public void migrationPutsEachKindOfItemInTheRightTenant() throws Exception {
        assumeHttpAdminApi();

        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            HttpUtils.executePutRequest(httpClient, es + "/" + SOURCE_INDEX, MAPPING, null);
            // Shipped definitions: without scope, with the 3.0 default scope, and with the 3.0 _itemtype id suffix
            indexDocument(httpClient, SOURCE_INDEX, "booleanCondition", "booleanCondition", "conditionType", null);
            indexDocument(httpClient, SOURCE_INDEX, "updateProperties", "updateProperties", "rule", "systemscope");
            indexDocument(httpClient, SOURCE_INDEX, "pastEventCondition_conditiontype", "pastEventCondition_conditiontype", "conditionType", null);
            // User content with the 3.0 default scope
            indexDocument(httpClient, SOURCE_INDEX, "my-user-rule", "my-user-rule", "rule", "systemscope");
            indexDocument(httpClient, SOURCE_INDEX, "my-segment", "my-segment", "segment", "systemscope");
            indexDocument(httpClient, SOURCE_INDEX, "my-scoring", "my-scoring", "scoring", "systemscope");
            indexDocument(httpClient, SOURCE_INDEX, "my-goal_goal", "my-goal", "goal", "systemscope");
            // Literal system scope on a type that is neither shipped nor a system item type
            indexDocument(httpClient, SOURCE_INDEX, "cfg1", "cfg1", "sfdcConfiguration", "system");

            Map<String, JsonNode> byItemId = migrate(httpClient, SOURCE_INDEX, DEST_INDEX, "systemitems", false,
                    Set.of("conditiontype", "rule", "segment", "scoring", "goal"),
                    MigrationUtils.loadShippedDefinitionIds(migrationBundle()));
            Assert.assertEquals(8, byItemId.size());

            assertTenant(byItemId.get("booleanCondition"), "system");
            assertTenant(byItemId.get("updateProperties"), "system");
            assertTenant(byItemId.get("pastEventCondition_conditiontype"), "system");
            assertTenant(byItemId.get("my-user-rule"), TEST_TENANT_ID);
            assertTenant(byItemId.get("my-segment"), TEST_TENANT_ID);
            assertTenant(byItemId.get("my-scoring"), TEST_TENANT_ID);
            assertTenant(byItemId.get("my-goal"), TEST_TENANT_ID);
            assertTenant(byItemId.get("cfg1"), "system");

            // System item types get the _itemtype suffix exactly once, other types none
            Assert.assertEquals("system_booleanCondition_conditiontype", byItemId.get("booleanCondition").path("_id").asText());
            Assert.assertEquals("system_pastEventCondition_conditiontype",
                    byItemId.get("pastEventCondition_conditiontype").path("_id").asText());
            Assert.assertEquals(TEST_TENANT_ID + "_my-goal_goal", byItemId.get("my-goal").path("_id").asText());
            Assert.assertEquals("system_cfg1", byItemId.get("cfg1").path("_id").asText());

            // Migrated system items carry create audit, so the bundle redeploy is an update
            JsonNode source = byItemId.get("booleanCondition").path("_source");
            Assert.assertEquals("system-migration-4.0.0", source.path("createdBy").asText());
            Assert.assertEquals("system-migration-4.0.0", source.path("lastModifiedBy").asText());
            Assert.assertEquals("2026-01-01T00:00:00Z", source.path("creationDate").asText());
        }
    }

    @Test
    public void forcedDefaultTenantNeverUsesTheSystemTenant() throws Exception {
        assumeHttpAdminApi();

        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            HttpUtils.executePutRequest(httpClient, es + "/" + SOURCE_INDEX, MAPPING, null);
            indexDocument(httpClient, SOURCE_INDEX, "node1", "node1", "clusterNode", "system");
            indexDocument(httpClient, SOURCE_INDEX, "booleanCondition", "booleanCondition", "conditionType", null);

            Map<String, JsonNode> byItemId = migrate(httpClient, SOURCE_INDEX, DEST_INDEX, "clusterNode", true,
                    Collections.emptySet(), MigrationUtils.loadShippedDefinitionIds(migrationBundle()));
            Assert.assertEquals(2, byItemId.size());
            assertTenant(byItemId.get("node1"), TEST_TENANT_ID);
            assertTenant(byItemId.get("booleanCondition"), TEST_TENANT_ID);
        }
    }

    @Test
    public void profilesSessionsAndEventsStayInTheConfiguredTenant() throws Exception {
        assumeHttpAdminApi();

        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            HttpUtils.executePutRequest(httpClient, es + "/" + DATA_SOURCE, MAPPING, null);
            indexDocument(httpClient, DATA_SOURCE, "p1", "p1", "profile", null);
            indexDocument(httpClient, DATA_SOURCE, "s1", "s1", "session", "systemscope");
            indexDocument(httpClient, DATA_SOURCE, "e1", "e1", "event", "systemscope");

            Map<String, JsonNode> byItemId = migrate(httpClient, DATA_SOURCE, DATA_DEST, "profile", false,
                    Set.of("conditiontype", "rule"), MigrationUtils.loadShippedDefinitionIds(migrationBundle()));
            Assert.assertEquals(3, byItemId.size());
            for (String itemId : new String[]{"p1", "s1", "e1"}) {
                assertTenant(byItemId.get(itemId), TEST_TENANT_ID);
                Assert.assertEquals(TEST_TENANT_ID + "_" + itemId, byItemId.get(itemId).path("_id").asText());
            }
        }
    }

    @Test
    public void geonamesGoToTheSystemTenant() throws Exception {
        assumeHttpAdminApi();

        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            HttpUtils.executePutRequest(httpClient, es + "/" + GEO_SOURCE, MAPPING, null);
            indexDocument(httpClient, GEO_SOURCE, "paris", "paris", "geonameEntry", null);

            Map<String, JsonNode> byItemId = migrate(httpClient, GEO_SOURCE, GEO_DEST, "geonameentry", false,
                    Collections.emptySet(), Collections.emptyMap());
            assertTenant(byItemId.get("paris"), "system");
        }
    }

    @Test
    public void itemIdFixMatchesCamelCaseItemTypesAndCountsOnlyChangedDocuments() throws Exception {
        assumeHttpAdminApi();

        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            String mapping = "{"
                    + "\"settings\":{"
                    + "\"number_of_shards\":1,\"number_of_replicas\":0,"
                    + "\"analysis\":{\"analyzer\":{\"folding\":{\"type\":\"custom\",\"tokenizer\":\"keyword\",\"filter\":[\"lowercase\",\"asciifolding\"]}}}"
                    + "},"
                    + "\"mappings\":{\"properties\":{"
                    + "\"itemId\":{\"type\":\"keyword\"},"
                    + "\"itemType\":{\"type\":\"text\",\"analyzer\":\"folding\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}}"
                    + "}}}";
            HttpUtils.executePutRequest(httpClient, es + "/" + CAMEL_CASE_INDEX, mapping, null);
            indexDocument(httpClient, CAMEL_CASE_INDEX, "system_booleanCondition_conditiontype", "wrong", "conditionType", null);
            // Already consistent: must be left untouched and not counted
            indexDocument(httpClient, CAMEL_CASE_INDEX, "system_pastEventCondition_conditiontype",
                    "pastEventCondition_conditiontype", "conditionType", null);

            // Same request as migrate-4.0.0-05-fixSystemItemIds.groovy
            String fixScript = MigrationUtils.getFileWithoutComments(
                    migrationBundle(), "requestBody/4.0.0/fix_system_item_ids.painless");
            long updated = MigrationUtils.updateByQueryAndCount(httpClient, es, CAMEL_CASE_INDEX,
                    MigrationUtils.buildItemTypeUpdateRequest(fixScript, "conditionType"));
            Assert.assertEquals(1L, updated);

            JsonNode source = MAPPER.readTree(HttpUtils.executeGetRequest(httpClient,
                    es + "/" + CAMEL_CASE_INDEX + "/_doc/system_booleanCondition_conditiontype", null))
                    .path("_source");
            Assert.assertEquals("booleanCondition_conditiontype", source.path("itemId").asText());
        }
    }

    private void assumeHttpAdminApi() {
        Assume.assumeTrue(
                "HTTP admin API required (provider=" + getPersistenceBackend().providerId() + ")",
                persistenceCapabilities().httpAdminApi());
    }

    private static BundleContext migrationBundle() {
        return FrameworkUtil.getBundle(MigrationUtils.class).getBundleContext();
    }

    private void indexDocument(CloseableHttpClient httpClient, String index, String documentId, String itemId,
                               String itemType, String scope) throws Exception {
        String source = "{\"itemId\":\"" + itemId + "\",\"itemType\":\"" + itemType + "\""
                + (scope != null ? ",\"scope\":\"" + scope + "\"" : "") + "}";
        HttpUtils.executePutRequest(httpClient,
                getSearchEngineBaseUrl() + "/" + index + "/_doc/" + documentId + "?refresh=true", source, null);
    }

    /**
     * Runs the tenant assignment script from {@code source} to {@code dest}, as migrate-4.0.0-01 does,
     * and returns the migrated hits by source itemId.
     */
    private Map<String, JsonNode> migrate(CloseableHttpClient httpClient, String source, String dest, String itemType,
                                          boolean forceDefaultTenant, Set<String> systemItems,
                                          Map<String, Set<String>> shippedDefinitionIds) throws Exception {
        String es = getSearchEngineBaseUrl();
        MigrationUtils.createIndex(httpClient, es, dest, MAPPING);

        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", TEST_TENANT_ID);
        params.put("systemTenantId", "system");
        params.put("forceDefaultTenant", forceDefaultTenant);
        params.put("itemType", itemType);
        params.put("date", "2026-01-01T00:00:00Z");
        params.put("systemItems", systemItems);
        params.put("shippedDefinitionIds", shippedDefinitionIds);

        String painless = MigrationUtils.getFileWithoutComments(
                migrationBundle(), "requestBody/4.0.0/initialize_tenant_and_audit_fields.painless");
        MigrationUtils.moveToIndex(httpClient, migrationBundle(), es, source, dest, painless, params);
        HttpUtils.executePostRequest(httpClient, es + "/" + dest + "/_refresh", null, null);

        Map<String, JsonNode> byItemId = new HashMap<>();
        for (JsonNode hit : MAPPER.readTree(HttpUtils.executeGetRequest(httpClient,
                es + "/" + dest + "/_search?size=20", null)).path("hits").path("hits")) {
            byItemId.put(hit.path("_source").path("itemId").asText(), hit);
        }
        return byItemId;
    }

    private static void assertTenant(JsonNode hit, String expectedTenant) {
        Assert.assertNotNull(hit);
        Assert.assertTrue(hit.path("_id").asText().startsWith(expectedTenant + "_"));
        Assert.assertEquals(expectedTenant, hit.path("_source").path("tenantId").asText());
    }
}
