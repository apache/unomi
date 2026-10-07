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
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.itests.BaseIT;
import org.apache.unomi.itests.persistence.SearchBackendIT;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.apache.unomi.shell.migration.utils.MigrationUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * UNOMI-998: migrate a synthetic Unomi 3.0 data set with {@code unomi:migrate 3.0.0}
 * before Unomi starts, then assert the post-migration shape that 996/997 fixed.
 * <p>
 * Must run before {@link Migrate16xToCurrentVersionIT} so that the 1.6 snapshot restore
 * can wipe this fixture afterward. Uses HTTP seeding so both Elasticsearch and OpenSearch
 * can run it ({@code httpAdminApi}), unlike the 1.6 snapshot path.
 * <p>
 * One {@code @Test} method only: {@code @Before} must not re-seed on every method, or the
 * second migrate is skipped by migration history and leaves documents without tenants.
 * <p>
 * Pax Exam still installs the full feature set for the shared suite. The boot-feature
 * mapping path is guarded by asserting that the migration command bundle itself ships
 * the engine mappings (UNOMI-996).
 */
@Category(SearchBackendIT.class)
public class Migrate30ToCurrentVersionIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(Migrate30ToCurrentVersionIT.class);

    private static final String MIGRATION_COMMAND = "unomi:migrate 3.0.0 true";
    private static final long MIGRATION_TIMEOUT = 900000L;

    private static final String INDEX_PREFIX = "context-";
    private static final String INDEX_SYSTEMITEMS = INDEX_PREFIX + "systemitems";
    private static final String INDEX_PROFILE = INDEX_PREFIX + "profile";
    private static final String INDEX_GEONAME = INDEX_PREFIX + "geonameentry";
    private static final String INDEX_CLUSTERNODE = INDEX_PREFIX + "clusternode";
    private static final String INDEX_SFDC = INDEX_PREFIX + "sfdcconfiguration";
    private static final String INDEX_TENANT = INDEX_PREFIX + "tenant";

    private static final String USER_RULE_ID = "unomi998-user-rule";
    private static final String USER_SEGMENT_ID = "unomi998-user-segment";
    private static final String GEO_ID = "unomi998-paris";
    private static final String NODE_ID = "unomi998-node";
    private static final String SFDC_ID = "unomi998-sfdc";
    private static final String PROFILE_ID = "unomi998-profile";
    private static final String OLD_CONDITION_MARKER = "unomi998-3.0-seed-booleanCondition";

    private static final String SIMPLE_MAPPING = "{"
            + "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},"
            + "\"mappings\":{\"properties\":{"
            + "\"itemId\":{\"type\":\"keyword\"},"
            + "\"itemType\":{\"type\":\"keyword\"},"
            + "\"scope\":{\"type\":\"keyword\"},"
            + "\"tenantId\":{\"type\":\"keyword\"},"
            + "\"metadata\":{\"type\":\"object\",\"enabled\":true},"
            + "\"name\":{\"type\":\"keyword\"},"
            + "\"properties\":{\"type\":\"object\",\"enabled\":true}"
            + "}}}";

    private CloseableHttpClient httpClient;

    @Override
    @Before
    public void waitForStartup() throws InterruptedException {
        checkSearchEngine();

        if (!persistenceCapabilities().httpAdminApi()) {
            System.out.println("httpAdminApi not supported for provider "
                    + getPersistenceBackend().providerId() + " — starting Unomi without 3.0 migrate seed");
            super.waitForStartup();
            return;
        }

        try {
            httpClient = createSearchEngineHttpClient();
            seedUnomi30Dataset(httpClient);
            assertMigrationBundleShipsEngineMappings();

            System.out.println("Launching migration from 3.0.0...");
            LOGGER.info("Launching migration from 3.0.0...");
            String migrationOutput = executeCommand(MIGRATION_COMMAND, MIGRATION_TIMEOUT, false);
            System.out.println("Migration command output results:");
            System.out.println(migrationOutput);
            Assert.assertNotNull(migrationOutput);
            Assert.assertFalse("migration must not abort",
                    migrationOutput.toLowerCase().contains("migration process aborted"));
            Assert.assertTrue("migration must run 4.0.0 tenant scripts",
                    migrationOutput.contains("tenantDocumentIds"));
            Assert.assertTrue("migration must finish",
                    migrationOutput.contains("Finish execution of:")
                            || migrationOutput.contains("migrationStatus"));

            prepareSearchEngineAfterMigration();
        } catch (Throwable t) {
            LOGGER.error("Error during 3.0 migration setup", t);
            throw new RuntimeException("Error during 3.0 migration setup", t);
        }

        super.waitForStartup();
    }

    @Test
    public void checkMigratedDataFrom30() throws Exception {
        Assume.assumeTrue(
                "HTTP admin API required (provider=" + getPersistenceBackend().providerId() + ")",
                persistenceCapabilities().httpAdminApi());

        assertDefaultTenantExists();
        assertUserContentStaysOutOfSystemTenant();
        assertGeonamesClusterNodeAndSfdc();
        assertEverySeededDocumentHasATenant();
        assertShippedDefinitionsAreFourZero();
        assertApiKeyFileNamesCorrectHeaders();
    }

    private void assertDefaultTenantExists() throws Exception {
        Assert.assertTrue("tenant index must exist",
                MigrationUtils.indexExists(httpClient, getSearchEngineBaseUrl(), INDEX_TENANT));

        Tenant tenant = tenantService.getTenant(TEST_TENANT_ID);
        Assert.assertNotNull("default tenant must exist after migration (UNOMI-998)", tenant);
        Assert.assertEquals(TEST_TENANT_ID, tenant.getItemId());
        Assert.assertNotNull("tenant must have a public API key", tenant.getPublicApiKey());
        Assert.assertNotNull("tenant must have a private API key", tenant.getPrivateApiKey());
    }

    private void assertUserContentStaysOutOfSystemTenant() throws Exception {
        JsonNode rule = findByItemId(INDEX_SYSTEMITEMS, USER_RULE_ID);
        Assert.assertNotNull("user rule must still exist", rule);
        Assert.assertEquals(TEST_TENANT_ID, rule.path("_source").path("tenantId").asText());
        Assert.assertTrue(rule.path("_id").asText().startsWith(TEST_TENANT_ID + "_"));

        JsonNode segment = findByItemId(INDEX_SYSTEMITEMS, USER_SEGMENT_ID);
        Assert.assertNotNull("user segment must still exist", segment);
        Assert.assertEquals(TEST_TENANT_ID, segment.path("_source").path("tenantId").asText());
        Assert.assertFalse("user segment must not use the system document id",
                segment.path("_id").asText().startsWith("system_"));
    }

    private void assertGeonamesClusterNodeAndSfdc() throws Exception {
        JsonNode geo = findByItemId(INDEX_GEONAME, GEO_ID);
        Assert.assertNotNull(geo);
        Assert.assertEquals("system", geo.path("_source").path("tenantId").asText());

        JsonNode node = findByItemId(INDEX_CLUSTERNODE, NODE_ID);
        Assert.assertNotNull(node);
        Assert.assertEquals("cluster nodes stay in the configured tenant", TEST_TENANT_ID,
                node.path("_source").path("tenantId").asText());

        // Unknown index type uses forceDefaultTenant — literal scope "system" does not win
        JsonNode sfdc = findByItemId(INDEX_SFDC, SFDC_ID);
        Assert.assertNotNull(sfdc);
        Assert.assertEquals(TEST_TENANT_ID, sfdc.path("_source").path("tenantId").asText());

        JsonNode profile = findByItemId(INDEX_PROFILE, PROFILE_ID);
        Assert.assertNotNull(profile);
        Assert.assertEquals(TEST_TENANT_ID, profile.path("_source").path("tenantId").asText());
    }

    private void assertEverySeededDocumentHasATenant() throws Exception {
        for (String index : Arrays.asList(INDEX_SYSTEMITEMS, INDEX_PROFILE, INDEX_GEONAME, INDEX_CLUSTERNODE, INDEX_SFDC, INDEX_TENANT)) {
            if (!MigrationUtils.indexExists(httpClient, getSearchEngineBaseUrl(), index)) {
                continue;
            }
            JsonNode hits = getObjectMapper().readTree(HttpUtils.executeGetRequest(httpClient,
                    getSearchEngineBaseUrl() + "/" + index + "/_search?size=100", null))
                    .path("hits").path("hits");
            Assert.assertTrue(index + " should have documents", hits.size() > 0);
            for (JsonNode hit : hits) {
                Assert.assertTrue(index + " document " + hit.path("_id").asText() + " must have tenantId",
                        hit.path("_source").hasNonNull("tenantId")
                                && !hit.path("_source").path("tenantId").asText().isBlank());
            }
        }
    }

    private void assertShippedDefinitionsAreFourZero() throws Exception {
        ConditionType booleanCondition = definitionsService.getConditionType("booleanCondition");
        Assert.assertNotNull("booleanCondition must be available after migration", booleanCondition);
        String description = booleanCondition.getMetadata() != null
                ? booleanCondition.getMetadata().getDescription() : null;
        Assert.assertNotEquals("3.0 seeded marker must not win over the 4.0 bundle definition",
                OLD_CONDITION_MARKER, description);

        // Post-2.2 document id for system condition types: system_<id>_conditiontype
        JsonNode migratedShipped = getByDocumentId(INDEX_SYSTEMITEMS, "system_booleanCondition_conditiontype");
        if (migratedShipped == null) {
            migratedShipped = findByItemId(INDEX_SYSTEMITEMS, "booleanCondition");
        }
        Assert.assertNotNull("booleanCondition must exist in systemitems after migration", migratedShipped);
        Assert.assertEquals("shipped definitions go to system", "system",
                migratedShipped.path("_source").path("tenantId").asText());
        Assert.assertTrue("shipped definition document id must use the system tenant prefix",
                migratedShipped.path("_id").asText().startsWith("system_"));
    }

    private void assertApiKeyFileNamesCorrectHeaders() throws Exception {
        Path secretsDir = Paths.get(karafData(), "migration", "secrets");
        Assert.assertTrue("migration secrets directory must exist: " + secretsDir, Files.isDirectory(secretsDir));

        Path keyFile = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(secretsDir, "tenant-api-keys-" + TEST_TENANT_ID + "-*.txt")) {
            for (Path path : stream) {
                keyFile = path;
                break;
            }
        }
        Assert.assertNotNull("migration must write a tenant API key file", keyFile);

        String content = Files.readString(keyFile, StandardCharsets.UTF_8);
        Assert.assertTrue("public key must name X-Unomi-Api-Key\n" + content,
                content.contains("X-Unomi-Api-Key"));
        Assert.assertTrue("private key must mention Basic authentication\n" + content,
                content.toLowerCase().contains("basic"));
        Assert.assertFalse("must not advertise the non-existent X-Unomi-Public-Key header\n" + content,
                content.contains("X-Unomi-Public-Key"));
        Assert.assertFalse("must not advertise the non-existent X-Unomi-Key header\n" + content,
                content.contains("X-Unomi-Key header"));
    }

    private void seedUnomi30Dataset(CloseableHttpClient client) throws Exception {
        String es = getSearchEngineBaseUrl();
        System.out.println("Seeding synthetic Unomi 3.0 dataset...");
        LOGGER.info("Seeding synthetic Unomi 3.0 dataset...");

        for (String index : Arrays.asList(INDEX_SYSTEMITEMS, INDEX_PROFILE, INDEX_GEONAME, INDEX_CLUSTERNODE, INDEX_SFDC)) {
            MigrationUtils.deleteIndex(client, es, index);
            MigrationUtils.createIndex(client, es, index, SIMPLE_MAPPING);
        }

        putDoc(client, INDEX_SYSTEMITEMS, "booleanCondition",
                "{\"itemId\":\"booleanCondition\",\"itemType\":\"conditionType\","
                        + "\"metadata\":{\"id\":\"booleanCondition\",\"description\":\"" + OLD_CONDITION_MARKER + "\"}}");
        putDoc(client, INDEX_SYSTEMITEMS, USER_RULE_ID,
                "{\"itemId\":\"" + USER_RULE_ID + "\",\"itemType\":\"rule\",\"scope\":\"systemscope\","
                        + "\"metadata\":{\"id\":\"" + USER_RULE_ID + "\",\"name\":\"UNOMI-998 user rule\"}}");
        putDoc(client, INDEX_SYSTEMITEMS, USER_SEGMENT_ID,
                "{\"itemId\":\"" + USER_SEGMENT_ID + "\",\"itemType\":\"segment\",\"scope\":\"systemscope\","
                        + "\"metadata\":{\"id\":\"" + USER_SEGMENT_ID + "\",\"name\":\"UNOMI-998 user segment\"}}");

        putDoc(client, INDEX_GEONAME, GEO_ID,
                "{\"itemId\":\"" + GEO_ID + "\",\"itemType\":\"geonameEntry\",\"name\":\"Paris\"}");
        putDoc(client, INDEX_CLUSTERNODE, NODE_ID,
                "{\"itemId\":\"" + NODE_ID + "\",\"itemType\":\"clusterNode\",\"scope\":\"system\"}");
        putDoc(client, INDEX_SFDC, SFDC_ID,
                "{\"itemId\":\"" + SFDC_ID + "\",\"itemType\":\"sfdcConfiguration\",\"scope\":\"system\"}");
        putDoc(client, INDEX_PROFILE, PROFILE_ID,
                "{\"itemId\":\"" + PROFILE_ID + "\",\"itemType\":\"profile\",\"properties\":{\"firstName\":\"Ada\"}}");

        HttpUtils.executePostRequest(client, es + "/_refresh", null, null);
    }

    private void assertMigrationBundleShipsEngineMappings() {
        Bundle migrationBundle = FrameworkUtil.getBundle(MigrationUtils.class);
        Assert.assertNotNull(migrationBundle);
        for (String engine : Arrays.asList("elasticsearch", "opensearch")) {
            for (String mappingFile : Arrays.asList("clusterNode.json", "profile.json", "geonameEntry.json", "systemItems.json")) {
                String path = "META-INF/cxs/migration-mappings/" + engine + "/" + mappingFile;
                Assert.assertNotNull("boot migrate needs " + path + " in shell-commands",
                        migrationBundle.getEntry(path));
            }
        }
    }

    private void putDoc(CloseableHttpClient client, String index, String id, String source) throws Exception {
        HttpUtils.executePutRequest(client,
                getSearchEngineBaseUrl() + "/" + index + "/_doc/" + id + "?refresh=true", source, null);
    }

    private JsonNode getByDocumentId(String index, String documentId) throws Exception {
        try {
            String response = HttpUtils.executeGetRequest(httpClient,
                    getSearchEngineBaseUrl() + "/" + index + "/_doc/" + documentId, null);
            if (response == null || response.isBlank()) {
                return null;
            }
            JsonNode node = getObjectMapper().readTree(response);
            if (!node.path("found").asBoolean(false)) {
                return null;
            }
            return node;
        } catch (IOException e) {
            // HttpUtils throws on HTTP 404 when the document is missing
            return null;
        }
    }

    private JsonNode findByItemId(String index, String itemId) throws Exception {
        // After migrate reindex, string fields use the folding text mapping with a .keyword subfield.
        // Term/prefix on the analyzed field miss camelCase ids such as booleanCondition.
        String body = "{\"size\":20,\"query\":{\"bool\":{\"should\":["
                + "{\"term\":{\"itemId.keyword\":\"" + itemId + "\"}},"
                + "{\"prefix\":{\"itemId.keyword\":\"" + itemId + "_\"}},"
                + "{\"term\":{\"itemId\":\"" + itemId + "\"}},"
                + "{\"prefix\":{\"itemId\":\"" + itemId + "_\"}}"
                + "],\"minimum_should_match\":1}}}";
        JsonNode hits = getObjectMapper().readTree(HttpUtils.executePostRequest(httpClient,
                getSearchEngineBaseUrl() + "/" + index + "/_search", body, null))
                .path("hits").path("hits");
        for (JsonNode hit : hits) {
            String found = hit.path("_source").path("itemId").asText();
            String docId = hit.path("_id").asText();
            if (found.equals(itemId) || found.startsWith(itemId + "_")
                    || docId.equals(itemId) || docId.contains("_" + itemId + "_")
                    || docId.endsWith("_" + itemId)) {
                return hit;
            }
        }
        return null;
    }
}
