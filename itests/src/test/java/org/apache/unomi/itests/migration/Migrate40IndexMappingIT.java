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
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * UNOMI-996: migration must find bundled mappings (including cluster nodes) and
 * copy the mapping of an unknown index instead of stopping.
 */
@Category(SearchBackendIT.class)
public class Migrate40IndexMappingIT extends BaseIT {

    private static final String UNKNOWN_INDEX = "context-unomi996unknown";
    private static final String DEST_INDEX = "context-unomi996unknown-dest";

    @After
    public void deleteTestIndices() throws Exception {
        if (!persistenceCapabilities().httpAdminApi()) {
            return;
        }
        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            MigrationUtils.deleteIndex(httpClient, es, UNKNOWN_INDEX);
            MigrationUtils.deleteIndex(httpClient, es, DEST_INDEX);
        }
    }

    @Test
    public void mappingsAreBundledWithTheMigrationCommandForBothEngines() {
        // Look in the migration command bundle only: the started persistence bundle ships these files too
        Bundle migrationBundle = FrameworkUtil.getBundle(MigrationUtils.class);
        for (String engine : Arrays.asList(SEARCH_ENGINE_ELASTICSEARCH, SEARCH_ENGINE_OPENSEARCH)) {
            for (String mappingFile : Arrays.asList("clusterNode.json", "profile.json", "event.json", "geonameEntry.json")) {
                String path = "META-INF/cxs/migration-mappings/" + engine + "/" + mappingFile;
                Assert.assertNotNull(path + " should be shipped with the migration command", migrationBundle.getEntry(path));
            }
        }
    }

    @Test
    public void searchEngineIsDetected() throws Exception {
        assumeHttpAdminApi();
        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            Assert.assertEquals(getPersistenceBackend().providerId(),
                    MigrationUtils.getSearchEngine(httpClient, getSearchEngineBaseUrl()));
        }
    }

    @Test
    public void unknownIndexKeepsDocumentsOnTheConfiguredTenant() throws Exception {
        JsonNode hits = migrateUnknownIndex(true);
        Assert.assertEquals(2, hits.size());
        for (JsonNode hit : hits) {
            String id = hit.path("_id").asText();
            Assert.assertTrue("document id should use the configured tenant: " + id, id.startsWith(TEST_TENANT_ID + "_"));
            Assert.assertEquals(TEST_TENANT_ID, hit.path("_source").path("tenantId").asText());
        }
    }

    @Test
    public void knownIndexKeepsSystemScopeDocumentsOnTheSystemTenant() throws Exception {
        JsonNode hits = migrateUnknownIndex(false);
        Assert.assertEquals(2, hits.size());
        for (JsonNode hit : hits) {
            boolean systemScope = "system".equals(hit.path("_source").path("scope").asText());
            String expectedTenant = systemScope ? "system" : TEST_TENANT_ID;
            Assert.assertEquals(expectedTenant + "_" + hit.path("_source").path("itemId").asText(), hit.path("_id").asText());
            Assert.assertEquals(expectedTenant, hit.path("_source").path("tenantId").asText());
        }
    }

    private void assumeHttpAdminApi() {
        Assume.assumeTrue(
                "HTTP admin API required to create migration fixture indices (provider="
                        + getPersistenceBackend().providerId() + ")",
                persistenceCapabilities().httpAdminApi());
    }

    /**
     * Reindexes a fixture index holding one document without scope and one with scope "system"
     * through the 4.0 tenant script, the way the migration does, and returns the resulting hits.
     */
    private JsonNode migrateUnknownIndex(boolean forceDefaultTenant) throws Exception {
        assumeHttpAdminApi();

        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            BundleContext migrationBundles = FrameworkUtil.getBundle(MigrationUtils.class).getBundleContext();

            String sourceSettings = "{"
                    + "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},"
                    + "\"mappings\":{\"properties\":{"
                    + "\"itemId\":{\"type\":\"keyword\"},"
                    + "\"itemType\":{\"type\":\"keyword\"},"
                    + "\"scope\":{\"type\":\"keyword\"},"
                    + "\"tenantId\":{\"type\":\"keyword\"}"
                    + "}}}";
            HttpUtils.executePutRequest(httpClient, es + "/" + UNKNOWN_INDEX, sourceSettings, null);
            HttpUtils.executePutRequest(httpClient, es + "/" + UNKNOWN_INDEX
                    + "/_doc/unknown1?refresh=true",
                    "{\"itemId\":\"unknown1\",\"itemType\":\"sfdcConfiguration\"}", null);
            HttpUtils.executePutRequest(httpClient, es + "/" + UNKNOWN_INDEX
                    + "/_doc/unknown2?refresh=true",
                    "{\"itemId\":\"unknown2\",\"itemType\":\"sfdcConfiguration\",\"scope\":\"system\"}", null);

            String mapping = MigrationUtils.extractMappingFromIndex(httpClient, es, UNKNOWN_INDEX);
            Assert.assertTrue("copied mapping should keep itemType", mapping.contains("itemType"));

            String itemType = MigrationUtils.resolveItemType(UNKNOWN_INDEX, "context",
                    Arrays.asList("profile", "event", "clusterNode", "generic"));
            Assert.assertEquals("unomi996unknown", itemType);

            String destSettings = "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},\"mappings\":"
                    + mapping + "}";
            MigrationUtils.createIndex(httpClient, es, DEST_INDEX, destSettings);

            Map<String, Object> params = new HashMap<>();
            params.put("tenantId", TEST_TENANT_ID);
            params.put("systemTenantId", "system");
            params.put("forceDefaultTenant", forceDefaultTenant);
            params.put("itemType", itemType);
            params.put("date", "2026-01-01T00:00:00Z");
            params.put("systemItems", Collections.emptySet());
            params.put("shippedDefinitionIds", Collections.emptyMap());

            String painless = MigrationUtils.getFileWithoutComments(
                    migrationBundles, "requestBody/4.0.0/initialize_tenant_and_audit_fields.painless");
            MigrationUtils.moveToIndex(httpClient, migrationBundles, es, UNKNOWN_INDEX, DEST_INDEX, painless, params);
            HttpUtils.executePostRequest(httpClient, es + "/" + DEST_INDEX + "/_refresh", null, null);

            String search = HttpUtils.executeGetRequest(httpClient,
                    es + "/" + DEST_INDEX + "/_search?size=10", null);
            return new ObjectMapper().readTree(search).path("hits").path("hits");
        }
    }
}
