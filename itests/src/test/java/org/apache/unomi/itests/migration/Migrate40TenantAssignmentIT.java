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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * UNOMI-997: after migration, shipped definitions and geonames land in system,
 * while user content with systemscope stays in the configured tenant.
 */
@Category(SearchBackendIT.class)
public class Migrate40TenantAssignmentIT extends BaseIT {

    private static final String SOURCE_INDEX = "context-unomi997systemitems";
    private static final String DEST_INDEX = "context-unomi997systemitems-dest";
    private static final String GEO_SOURCE = "context-unomi997geonameentry";
    private static final String GEO_DEST = "context-unomi997geonameentry-dest";

    @After
    public void deleteTestIndices() throws Exception {
        if (!persistenceCapabilities().httpAdminApi()) {
            return;
        }
        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            MigrationUtils.deleteIndex(httpClient, es, SOURCE_INDEX);
            MigrationUtils.deleteIndex(httpClient, es, DEST_INDEX);
            MigrationUtils.deleteIndex(httpClient, es, GEO_SOURCE);
            MigrationUtils.deleteIndex(httpClient, es, GEO_DEST);
        }
    }

    @Test
    public void shippedDefinitionsListIsPresentInTheMigrationBundle() throws Exception {
        Set<String> shipped = MigrationUtils.loadShippedDefinitionIds(
                FrameworkUtil.getBundle(MigrationUtils.class).getBundleContext());
        Assert.assertTrue(shipped.contains("conditiontype:booleancondition"));
        Assert.assertTrue(shipped.contains("conditiontype:pasteventcondition"));
        Assert.assertTrue(shipped.contains("conditiontype:profilepropertycondition"));
        Assert.assertFalse("user content must not be in the shipped list by id alone",
                shipped.contains("rule:my-user-rule"));
    }

    @Test
    public void migrationPutsEachKindOfItemInTheRightTenant() throws Exception {
        Assume.assumeTrue(
                "HTTP admin API required (provider=" + getPersistenceBackend().providerId() + ")",
                persistenceCapabilities().httpAdminApi());

        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            BundleContext migrationBundles = FrameworkUtil.getBundle(MigrationUtils.class).getBundleContext();
            Set<String> shipped = MigrationUtils.loadShippedDefinitionIds(migrationBundles);

            String mapping = "{"
                    + "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},"
                    + "\"mappings\":{\"properties\":{"
                    + "\"itemId\":{\"type\":\"keyword\"},"
                    + "\"itemType\":{\"type\":\"keyword\"},"
                    + "\"scope\":{\"type\":\"keyword\"},"
                    + "\"tenantId\":{\"type\":\"keyword\"}"
                    + "}}}";
            HttpUtils.executePutRequest(httpClient, es + "/" + SOURCE_INDEX, mapping, null);
            HttpUtils.executePutRequest(httpClient, es + "/" + SOURCE_INDEX
                    + "/_doc/booleanCondition?refresh=true",
                    "{\"itemId\":\"booleanCondition\",\"itemType\":\"conditionType\"}", null);
            HttpUtils.executePutRequest(httpClient, es + "/" + SOURCE_INDEX
                    + "/_doc/my-user-rule?refresh=true",
                    "{\"itemId\":\"my-user-rule\",\"itemType\":\"rule\",\"scope\":\"systemscope\"}", null);
            HttpUtils.executePutRequest(httpClient, es + "/" + SOURCE_INDEX
                    + "/_doc/my-segment?refresh=true",
                    "{\"itemId\":\"my-segment\",\"itemType\":\"segment\",\"scope\":\"systemscope\"}", null);
            HttpUtils.executePutRequest(httpClient, es + "/" + SOURCE_INDEX
                    + "/_doc/my-scoring?refresh=true",
                    "{\"itemId\":\"my-scoring\",\"itemType\":\"scoring\",\"scope\":\"systemscope\"}", null);
            HttpUtils.executePutRequest(httpClient, es + "/" + SOURCE_INDEX
                    + "/_doc/my-goal?refresh=true",
                    "{\"itemId\":\"my-goal\",\"itemType\":\"goal\",\"scope\":\"systemscope\"}", null);

            MigrationUtils.createIndex(httpClient, es, DEST_INDEX, mapping);

            Set<String> systemItems = new HashSet<>();
            Collections.addAll(systemItems, "conditiontype", "rule", "segment", "scoring", "goal");

            Map<String, Object> params = new HashMap<>();
            params.put("tenantId", TEST_TENANT_ID);
            params.put("systemTenantId", "system");
            params.put("forceDefaultTenant", false);
            params.put("itemType", "systemitems");
            params.put("date", "2026-01-01T00:00:00Z");
            params.put("systemItems", systemItems);
            params.put("shippedDefinitionIds", shipped);

            String painless = MigrationUtils.getFileWithoutComments(
                    migrationBundles, "requestBody/4.0.0/initialize_tenant_and_audit_fields.painless");
            MigrationUtils.moveToIndex(httpClient, migrationBundles, es, SOURCE_INDEX, DEST_INDEX, painless, params);
            HttpUtils.executePostRequest(httpClient, es + "/" + DEST_INDEX + "/_refresh", null, null);

            JsonNode hits = new ObjectMapper().readTree(HttpUtils.executeGetRequest(httpClient,
                    es + "/" + DEST_INDEX + "/_search?size=20", null)).path("hits").path("hits");
            Assert.assertEquals(5, hits.size());

            Map<String, JsonNode> byItemId = new HashMap<>();
            for (JsonNode hit : hits) {
                byItemId.put(hit.path("_source").path("itemId").asText(), hit);
            }

            assertTenant(byItemId.get("booleanCondition"), "system");
            assertTenant(byItemId.get("my-user-rule"), TEST_TENANT_ID);
            assertTenant(byItemId.get("my-segment"), TEST_TENANT_ID);
            assertTenant(byItemId.get("my-scoring"), TEST_TENANT_ID);
            assertTenant(byItemId.get("my-goal"), TEST_TENANT_ID);
            Assert.assertFalse("no user-created item may land in system",
                    byItemId.get("my-user-rule").path("_id").asText().startsWith("system_"));
        }
    }

    @Test
    public void geonamesGoToTheSystemTenant() throws Exception {
        Assume.assumeTrue(
                "HTTP admin API required (provider=" + getPersistenceBackend().providerId() + ")",
                persistenceCapabilities().httpAdminApi());

        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            BundleContext migrationBundles = FrameworkUtil.getBundle(MigrationUtils.class).getBundleContext();

            String mapping = "{"
                    + "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},"
                    + "\"mappings\":{\"properties\":{"
                    + "\"itemId\":{\"type\":\"keyword\"},"
                    + "\"itemType\":{\"type\":\"keyword\"},"
                    + "\"tenantId\":{\"type\":\"keyword\"}"
                    + "}}}";
            HttpUtils.executePutRequest(httpClient, es + "/" + GEO_SOURCE, mapping, null);
            HttpUtils.executePutRequest(httpClient, es + "/" + GEO_SOURCE
                    + "/_doc/paris?refresh=true",
                    "{\"itemId\":\"paris\",\"itemType\":\"geonameEntry\"}", null);
            MigrationUtils.createIndex(httpClient, es, GEO_DEST, mapping);

            Map<String, Object> params = new HashMap<>();
            params.put("tenantId", TEST_TENANT_ID);
            params.put("systemTenantId", "system");
            params.put("forceDefaultTenant", false);
            params.put("itemType", "geonameentry");
            params.put("date", "2026-01-01T00:00:00Z");
            params.put("systemItems", Collections.emptySet());
            params.put("shippedDefinitionIds", Collections.emptySet());

            String painless = MigrationUtils.getFileWithoutComments(
                    migrationBundles, "requestBody/4.0.0/initialize_tenant_and_audit_fields.painless");
            MigrationUtils.moveToIndex(httpClient, migrationBundles, es, GEO_SOURCE, GEO_DEST, painless, params);
            HttpUtils.executePostRequest(httpClient, es + "/" + GEO_DEST + "/_refresh", null, null);

            JsonNode hit = new ObjectMapper().readTree(HttpUtils.executeGetRequest(httpClient,
                    es + "/" + GEO_DEST + "/_search?size=1", null)).path("hits").path("hits").get(0);
            assertTenant(hit, "system");
        }
    }

    @Test
    public void camelCaseItemTypeKeywordQueryUpdatesDocuments() throws Exception {
        Assume.assumeTrue(
                "HTTP admin API required (provider=" + getPersistenceBackend().providerId() + ")",
                persistenceCapabilities().httpAdminApi());

        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            BundleContext migrationBundles = FrameworkUtil.getBundle(MigrationUtils.class).getBundleContext();

            String index = "context-unomi997camelcase";
            try {
                String mapping = "{"
                        + "\"settings\":{"
                        + "\"number_of_shards\":1,\"number_of_replicas\":0,"
                        + "\"analysis\":{\"analyzer\":{\"folding\":{\"type\":\"custom\",\"tokenizer\":\"keyword\",\"filter\":[\"lowercase\",\"asciifolding\"]}}}"
                        + "},"
                        + "\"mappings\":{\"properties\":{"
                        + "\"itemId\":{\"type\":\"keyword\"},"
                        + "\"itemType\":{\"type\":\"text\",\"analyzer\":\"folding\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}}"
                        + "}}}";
                HttpUtils.executePutRequest(httpClient, es + "/" + index, mapping, null);
                HttpUtils.executePutRequest(httpClient, es + "/" + index
                        + "/_doc/system_booleanCondition_conditiontype?refresh=true",
                        "{\"itemId\":\"wrong\",\"itemType\":\"conditionType\"}", null);

                String fixScript = MigrationUtils.getFileWithoutComments(
                        migrationBundles, "requestBody/4.0.0/fix_system_item_ids.painless");
                String body = "{\"script\":{\"source\":"
                        + new ObjectMapper().writeValueAsString(fixScript)
                        + ",\"lang\":\"painless\"},"
                        + "\"query\":{\"bool\":{\"should\":["
                        + "{\"term\":{\"itemType.keyword\":\"conditionType\"}},"
                        + "{\"term\":{\"itemType\":\"conditiontype\"}}"
                        + "],\"minimum_should_match\":1}}}";
                long updated = MigrationUtils.updateByQueryAndCount(httpClient, es, index, body);
                Assert.assertEquals(1L, updated);

                JsonNode source = new ObjectMapper().readTree(HttpUtils.executeGetRequest(httpClient,
                        es + "/" + index + "/_doc/system_booleanCondition_conditiontype", null))
                        .path("_source");
                Assert.assertEquals("booleanCondition_conditiontype", source.path("itemId").asText());
            } finally {
                MigrationUtils.deleteIndex(httpClient, es, index);
            }
        }
    }

    private static void assertTenant(JsonNode hit, String expectedTenant) {
        Assert.assertNotNull(hit);
        Assert.assertTrue(hit.path("_id").asText().startsWith(expectedTenant + "_"));
        Assert.assertEquals(expectedTenant, hit.path("_source").path("tenantId").asText());
    }
}
