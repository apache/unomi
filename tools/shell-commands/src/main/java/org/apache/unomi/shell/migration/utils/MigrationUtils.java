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
package org.apache.unomi.shell.migration.utils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.shell.migration.service.MigrationContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.unomi.shell.migration.service.MigrationConfig.*;

/**
 * @author dgaillard
 */
public class MigrationUtils {

    public static JSONObject queryWithScroll(CloseableHttpClient httpClient, String url) throws IOException {
        url += "?scroll=1m";

        return new JSONObject(HttpUtils.executeGetRequest(httpClient, url, null));
    }

    public static JSONObject continueQueryWithScroll(CloseableHttpClient httpClient, String url, String scrollId) throws IOException {
        url += "/_search/scroll?scroll=1m&scroll_id=" + scrollId;

        return new JSONObject(HttpUtils.executeGetRequest(httpClient, url, null));
    }

    public static void bulkUpdate(CloseableHttpClient httpClient, String url, String jsonData) throws IOException {
        HttpUtils.executePostRequest(httpClient, url, jsonData, null);
    }

    public static String resourceAsString(BundleContext bundleContext, final String resource) {
        final URL url = bundleContext.getBundle().getResource(resource);
        try (InputStream stream = url.openStream()) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFileWithoutComments(BundleContext bundleContext, final String resource) {
        final URL url = bundleContext.getBundle().getResource(resource);
        try (InputStream stream = url.openStream()) {
            DataInputStream in = new DataInputStream(stream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            StringBuilder value = new StringBuilder();
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("/*") && !line.startsWith(" *") && !line.startsWith("*/"))
                    value.append(line);
            }
            in.close();
            return value.toString();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean indexExists(CloseableHttpClient httpClient, String esAddress, String indexName) throws IOException {
        final HttpGet httpGet = new HttpGet(esAddress + "/" + indexName);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
        }
    }

    public static Set<String> getIndexesPrefixedBy(CloseableHttpClient httpClient, String esAddress, String prefix) throws IOException {
        try (CloseableHttpResponse response = httpClient.execute(new HttpGet(esAddress + "/_aliases"))) {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                JSONObject indexesAsJson = new JSONObject(EntityUtils.toString(response.getEntity()));
                return indexesAsJson.keySet().stream().
                        filter(alias -> alias.startsWith(prefix)).
                        collect(Collectors.toSet());
            }
        }
        return Collections.emptySet();
    }

    public static String extractMappingFromBundles(BundleContext bundleContext, String fileName) throws IOException {
        for (Bundle bundle : bundleContext.getBundles()) {
            Enumeration<URL> predefinedMappings = bundle.findEntries("META-INF/cxs/mappings", fileName, true);
            if (predefinedMappings == null) {
                continue;
            }
            while (predefinedMappings.hasMoreElements()) {
                URL predefinedMappingURL = predefinedMappings.nextElement();
                return IOUtils.toString(predefinedMappingURL);
            }
        }

        throw new RuntimeException("no mapping found in bundles for: " + fileName);
    }

    public static String buildIndexCreationRequest(String baseIndexSettings, String mapping, MigrationContext context, boolean isMonthlyIndex) throws IOException {
        String settings = baseIndexSettings
                .replace("#numberOfShards", context.getConfigString(isMonthlyIndex ? MONTHLY_NUMBER_OF_SHARDS : NUMBER_OF_SHARDS))
                .replace("#numberOfReplicas", context.getConfigString(isMonthlyIndex ? MONTHLY_NUMBER_OF_REPLICAS : NUMBER_OF_REPLICAS))
                .replace("#maxDocValueFieldsSearch", context.getConfigString(isMonthlyIndex ? MONTHLY_MAX_DOC_VALUE_FIELDS_SEARCH : MAX_DOC_VALUE_FIELDS_SEARCH))
                .replace("#mappingTotalFieldsLimit", context.getConfigString(isMonthlyIndex ? MONTHLY_TOTAL_FIELDS_LIMIT : TOTAL_FIELDS_LIMIT));

        return settings.replace("#mappings", mapping);
    }

    public static void reIndex(CloseableHttpClient httpClient, BundleContext bundleContext, String esAddress, String indexName,
                               String newIndexSettings, String painlessScript, MigrationContext migrationContext) throws Exception {
        if (indexName.endsWith("-cloned")) {
            // We should never reIndex a clone ...
            return;
        }

        String indexNameCloned = indexName + "-cloned";

        String reIndexRequest = resourceAsString(bundleContext, "requestBody/2.0.0/base_reindex_request.json")
                .replace("#source", indexNameCloned).replace("#dest", indexName)
                .replace("#painless", StringUtils.isNotEmpty(painlessScript) ? getScriptPart(painlessScript) : "");

        String setIndexReadOnlyRequest = resourceAsString(bundleContext, "requestBody/2.0.0/base_set_index_readonly_request.json");

        migrationContext.performMigrationStep("Reindex step for: " + indexName + " (clone creation)", () -> {
            // Delete clone in case it already exists, could be incomplete from a previous reindex attempt, so better create a fresh one.
            if (indexExists(httpClient, esAddress, indexNameCloned)) {
                HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexNameCloned, null);
            }
            // Set original index as readOnly
            HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexName + "/_settings", setIndexReadOnlyRequest, null);
            // Clone the original index for backup
            HttpUtils.executePostRequest(httpClient, esAddress + "/" + indexName + "/_clone/" + indexNameCloned, null, null);
        });

        migrationContext.performMigrationStep("Reindex step for: " + indexName + " (recreate the index and perform the re-indexation)", () -> {
            // Delete original index if it still exists
            if (indexExists(httpClient, esAddress, indexName)) {
                HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexName, null);
            }
            // Recreate the original index with new mappings
            HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexName, newIndexSettings, null);
            // Reindex data from clone
            HttpUtils.executePostRequest(httpClient, esAddress + "/_reindex", reIndexRequest, null);
        });

        migrationContext.performMigrationStep("Reindex step for: " + indexName + " (delete clone)", () -> {
            // Delete original index if it still exists
            if (indexExists(httpClient, esAddress, indexNameCloned)) {
                HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexNameCloned, null);
            }
        });
        // Do a refresh
        HttpUtils.executePostRequest(httpClient, esAddress + "/" + indexName + "/_refresh", null, null);

        waitForYellowStatus(httpClient, esAddress, migrationContext);
    }

    public static void scrollQuery(CloseableHttpClient httpClient, String esAddress, String queryURL, String query, String scrollDuration, ScrollCallback scrollCallback) throws IOException {
        String response = HttpUtils.executePostRequest(httpClient, esAddress + queryURL + "?scroll=" + scrollDuration, query, null);

        while (true) {
            JSONObject responseAsJson = new JSONObject(response);
            String scrollId = responseAsJson.has("_scroll_id") ? responseAsJson.getString("_scroll_id") : null;
            JSONArray hits = new JSONArray();
            if (responseAsJson.has("hits")) {
                JSONObject hitsObject = responseAsJson.getJSONObject("hits");
                if (hitsObject.has("hits")) {
                    hits = hitsObject.getJSONArray("hits");
                }
            }

            // no more results, delete scroll
            if (hits.length() == 0) {
                if (scrollId != null) {
                    HttpUtils.executeDeleteRequest(httpClient, esAddress + "/_search/scroll/" + scrollId, null);
                }
                break;
            }

            // execute callback
            if (scrollCallback != null) {
                scrollCallback.execute(hits.toString());
            }

            // scroll
            response = HttpUtils.executePostRequest(httpClient, esAddress + "/_search/scroll", "{\n" +
                    "  \"scroll_id\": \"" + scrollId + "\",\n" +
                    "  \"scroll\": \"" + scrollDuration + "\"\n" +
                    "}", null);
        }
    }

    /**
     * Utility method that waits for the ES cluster to be in yellow status
     */
    public static void waitForYellowStatus(CloseableHttpClient httpClient, String esAddress, MigrationContext migrationContext) throws Exception {
        while (true) {
            final JSONObject status = new JSONObject(HttpUtils.executeGetRequest(httpClient, esAddress + "/_cluster/health?wait_for_status=yellow&timeout=60s", null));
            if (!status.get("timed_out").equals("true")) {
                migrationContext.printMessage("ES Cluster status is "  + status.get("status"));
                break;
            }
            migrationContext.printMessage("Waiting for ES Cluster status to be Yellow, current status is " + status.get("status"));
        }

    }

    public interface ScrollCallback {
        void execute(String hits);
    }

    private static String getScriptPart(String painlessScript) {
        return ", \"script\": {\"source\": \"" + painlessScript + "\", \"lang\": \"painless\"}";
    }
}
