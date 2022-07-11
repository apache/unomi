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
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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

    public static void reIndex(CloseableHttpClient httpClient, BundleContext bundleContext, String esAddress, String indexName,
            String newIndexSettings, String painlessScript) throws IOException {
        String indexNameCloned = indexName + "-cloned";

        // Init requests
        JSONObject originalIndexSettings = new JSONObject(HttpUtils.executeGetRequest(httpClient, esAddress + "/" + indexName + "/_settings", null));
        String newIndexRequest = newIndexSettings
                .replace("#numberOfShards", originalIndexSettings.getJSONObject(indexName).getJSONObject("settings").getJSONObject("index").getString("number_of_shards"))
                .replace("#numberOfReplicas", originalIndexSettings.getJSONObject(indexName).getJSONObject("settings").getJSONObject("index").getString("number_of_replicas"))
                .replace("#maxDocValueFieldsSearch", originalIndexSettings.getJSONObject(indexName).getJSONObject("settings").getJSONObject("index").getString("max_docvalue_fields_search"))
                .replace("#mappingTotalFieldsLimit", originalIndexSettings.getJSONObject(indexName).getJSONObject("settings").getJSONObject("index").getJSONObject("mapping").getJSONObject("total_fields").getString("limit"));
        String reIndexRequest = resourceAsString(bundleContext, "requestBody/2.0.0/base_reindex_request.json")
                .replace("#source", indexNameCloned)
                .replace("#dest", indexName)
                .replace("#painless", StringUtils.isNotEmpty(painlessScript)? ", \"script\":" + painlessScript: "");

        String setIndexReadOnlyRequest = resourceAsString(bundleContext, "requestBody/2.0.0/base_set_index_readonly_request.json");

        // Set original index as readOnly
        HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexName + "/_settings", setIndexReadOnlyRequest, null);
        // Clone the original index for backup
        HttpUtils.executePostRequest(httpClient, esAddress + "/" + indexName + "/_clone/" + indexNameCloned, null, null);
        // Delete original index
        HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexName, null);
        // Recreate the original index with new mappings
        HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexName, newIndexRequest, null);
        // Reindex data from clone
        HttpUtils.executePostRequest(httpClient, esAddress + "/_reindex", reIndexRequest, null);
        // Remove clone
        HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexNameCloned, null);
    }
}
