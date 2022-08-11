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
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static String buildIndexCreationRequest(CloseableHttpClient httpClient, String esAddress, String baseIndexSettings,
                                            String originalIndexForSettingsExtraction, String mapping) throws IOException {

        String settings = baseIndexSettings;

        // Extract existing settings on index that still exists
        if (originalIndexForSettingsExtraction != null) {
            JSONObject originalIndexSettings = new JSONObject(HttpUtils.executeGetRequest(httpClient, esAddress + "/" + originalIndexForSettingsExtraction + "/_settings", null));
            settings = settings
                    .replace("#numberOfShards", originalIndexSettings.getJSONObject(originalIndexForSettingsExtraction).getJSONObject("settings").getJSONObject("index").getString("number_of_shards"))
                    .replace("#numberOfReplicas", originalIndexSettings.getJSONObject(originalIndexForSettingsExtraction).getJSONObject("settings").getJSONObject("index").getString("number_of_replicas"))
                    .replace("#maxDocValueFieldsSearch", originalIndexSettings.getJSONObject(originalIndexForSettingsExtraction).getJSONObject("settings").getJSONObject("index").getString("max_docvalue_fields_search"))
                    .replace("#mappingTotalFieldsLimit", originalIndexSettings.getJSONObject(originalIndexForSettingsExtraction).getJSONObject("settings").getJSONObject("index").getJSONObject("mapping").getJSONObject("total_fields").getString("limit"));
        }

        return settings.replace("#mappings", mapping);
    }

    public static void reIndex(CloseableHttpClient httpClient, BundleContext bundleContext, String esAddress, String indexName,
            String newIndexSettings, String painlessScript) throws IOException {
        String indexNameCloned = indexName + "-cloned";

        String reIndexRequest = resourceAsString(bundleContext, "requestBody/2.0.0/base_reindex_request.json")
                .replace("#source", indexNameCloned).replace("#dest", indexName)
                .replace("#painless", StringUtils.isNotEmpty(painlessScript) ? getScriptPart(painlessScript) : "");

        String setIndexReadOnlyRequest = resourceAsString(bundleContext, "requestBody/2.0.0/base_set_index_readonly_request.json");

        // Set original index as readOnly
        HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexName + "/_settings", setIndexReadOnlyRequest, null);
        // Clone the original index for backup
        HttpUtils.executePostRequest(httpClient, esAddress + "/" + indexName + "/_clone/" + indexNameCloned, null, null);
        // Delete original index
        HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexName, null);
        // Recreate the original index with new mappings
        HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexName, newIndexSettings, null);
        // Reindex data from clone
        HttpUtils.executePostRequest(httpClient, esAddress + "/_reindex", reIndexRequest, null);
        // Remove clone
        HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexNameCloned, null);
    }

    public static void scrollQuery(CloseableHttpClient httpClient, String esAddress, String queryURL, String query, String scrollDuration, ScrollCallback scrollCallback) throws IOException {
        String response = HttpUtils.executePostRequest(httpClient, esAddress + queryURL + "?scroll=" + scrollDuration, query, null);

        while (true) {
            JSONObject responseAsJson = new JSONObject(response);
            String scrollId = responseAsJson.has("_scroll_id") ? responseAsJson.getString("_scroll_id"): null;
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

    public interface ScrollCallback {
        void execute(String hits);
    }

    private static String getScriptPart(String painlessScript) {
        return ", \"script\": {\"source\": \"" + painlessScript + "\", \"lang\": \"painless\"}";
    }
}
