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

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.itests.BaseIT;
import org.apache.unomi.itests.persistence.SearchBackendIT;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.apache.unomi.shell.migration.utils.MigrationUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.osgi.framework.FrameworkUtil;

import java.util.Arrays;

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
    public void clusterNodeMappingIsBundledWithTheMigrationCommand() throws Exception {
        String mapping = MigrationUtils.extractMappingFromBundles(
                FrameworkUtil.getBundle(MigrationUtils.class).getBundleContext(), "clusterNode.json");
        Assert.assertTrue("clusterNode mapping should define cpuLoad", mapping.contains("cpuLoad"));
    }

    @Test
    public void unknownIndexKeepsDocumentsOnTheConfiguredTenant() throws Exception {
        Assume.assumeTrue(
                "HTTP admin API required to create migration fixture indices (provider="
                        + getPersistenceBackend().providerId() + ")",
                persistenceCapabilities().httpAdminApi());

        try (CloseableHttpClient httpClient = createSearchEngineHttpClient()) {
            String es = getSearchEngineBaseUrl();
            org.osgi.framework.BundleContext migrationBundles =
                    FrameworkUtil.getBundle(MigrationUtils.class).getBundleContext();

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

            String mapping = MigrationUtils.resolveIndexMapping(
                    migrationBundles, httpClient, es, UNKNOWN_INDEX, "does-not-exist.json");
            Assert.assertTrue("copied mapping should keep itemType", mapping.contains("itemType"));

            Assert.assertEquals("sfdcconfiguration",
                    MigrationUtils.resolveItemType(UNKNOWN_INDEX, "context",
                            Arrays.asList("profile", "event", "clusterNode", "generic")));

            String destSettings = "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},\"mappings\":"
                    + mapping + "}";
            MigrationUtils.createIndex(httpClient, es, DEST_INDEX, destSettings);

            String painless = MigrationUtils.getFileWithoutComments(
                    migrationBundles, "requestBody/4.0.0/initialize_tenant_and_audit_fields.painless");
            JSONObject paramsObj = new JSONObject();
            paramsObj.put("tenantId", TEST_TENANT_ID);
            paramsObj.put("systemTenantId", "system");
            paramsObj.put("forceDefaultTenant", true);
            paramsObj.put("itemType", "generic");
            paramsObj.put("date", "2026-01-01T00:00:00Z");
            paramsObj.put("systemItems", new JSONArray());

            JSONObject scriptObj = new JSONObject();
            scriptObj.put("source", painless);
            scriptObj.put("lang", "painless");
            scriptObj.put("params", paramsObj);

            JSONObject reindex = new JSONObject();
            reindex.put("source", new JSONObject().put("index", UNKNOWN_INDEX));
            reindex.put("dest", new JSONObject().put("index", DEST_INDEX));
            reindex.put("script", scriptObj);
            HttpUtils.executePostRequest(httpClient, es + "/_reindex?refresh=true&wait_for_completion=true",
                    reindex.toString(), null);

            String search = HttpUtils.executeGetRequest(httpClient,
                    es + "/" + DEST_INDEX + "/_search?size=10", null);
            JSONArray hits = new JSONObject(search).getJSONObject("hits").getJSONArray("hits");
            Assert.assertEquals(2, hits.length());
            for (int i = 0; i < hits.length(); i++) {
                JSONObject hit = hits.getJSONObject(i);
                String id = hit.getString("_id");
                JSONObject source = hit.getJSONObject("_source");
                Assert.assertTrue("document id should use the configured tenant: " + id,
                        id.startsWith(TEST_TENANT_ID + "_"));
                Assert.assertFalse("unknown index documents must not use the system tenant: " + id,
                        id.startsWith("system_"));
                Assert.assertEquals(TEST_TENANT_ID, source.getString("tenantId"));
                Assert.assertNotEquals("system", source.getString("tenantId"));
            }
        }
    }
}
