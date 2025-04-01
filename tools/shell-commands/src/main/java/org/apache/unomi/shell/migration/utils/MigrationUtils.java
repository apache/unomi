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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.unomi.shell.migration.service.MigrationConfig.*;

/**
 * @author dgaillard
 */
public class MigrationUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationUtils.class);

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
        if (url == null) {
            throw new RuntimeException("Resource not found: " + resource);
        }
        try (InputStream stream = url.openStream()) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFileWithoutComments(BundleContext bundleContext, final String resource) {
        final URL url = bundleContext.getBundle().getResource(resource);
        try {
            // Read the entire file into a string to preserve exact line endings
            String fileContent;
            try (InputStream stream = url.openStream()) {
                fileContent = IOUtils.toString(stream, StandardCharsets.UTF_8);
            }
            
            // Process the content
            StringBuilder result = new StringBuilder();
            StringBuilder currentLine = new StringBuilder();
            boolean inBlockComment = false;
            boolean inString = false;
            char stringChar = 0;
            boolean lastWasSpace = false;
            
            for (int i = 0; i < fileContent.length(); i++) {
                char ch = fileContent.charAt(i);
                
                // Handle string literals - only if we're not in a comment
                if (!inBlockComment && (ch == '"' || ch == '\'')) {
                    if (!inString) {
                        inString = true;
                        stringChar = ch;
                    } else if (ch == stringChar) {
                        inString = false;
                        stringChar = 0;
                    }
                    currentLine.append(ch);
                    continue;
                }
                
                // If we're in a string, just append the character
                if (inString) {
                    currentLine.append(ch);
                    continue;
                }
                
                // Handle line endings - replace with space
                if (ch == '\n' || ch == '\r') {
                    // Check for Windows line endings (\r\n)
                    boolean isWindowsLineEnding = (ch == '\r' && i + 1 < fileContent.length() && fileContent.charAt(i + 1) == '\n');
                    
                    if (inBlockComment) {
                        // Just skip newlines in block comments
                        if (isWindowsLineEnding) {
                            i++; // Skip the \n part of \r\n
                        }
                    } else {
                        if (currentLine.length() > 0) {
                            // Process the current line
                            result.append(handleInlineComments(currentLine.toString()));
                            currentLine.setLength(0);
                        }
                        // Add a space if the last character wasn't already a space
                        if (!lastWasSpace) {
                            result.append(' ');
                            lastWasSpace = true;
                        }
                        if (isWindowsLineEnding) {
                            i++; // Skip the \n part of \r\n
                        }
                    }
                    continue;
                }
                
                // Handle block comments
                if (!inBlockComment && ch == '/' && i + 1 < fileContent.length() && fileContent.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    i++; // Skip the *
                    continue;
                }
                if (inBlockComment && ch == '*' && i + 1 < fileContent.length() && fileContent.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++; // Skip the /
                    continue;
                }
                
                // Handle inline comments
                if (!inBlockComment && ch == '/' && i + 1 < fileContent.length() && fileContent.charAt(i + 1) == '/') {
                    // Process the content before the inline comment
                    if (currentLine.length() > 0) {
                        result.append(currentLine);
                    }
                    currentLine.setLength(0);
                    
                    // Skip to the end of line
                    while (i < fileContent.length() && fileContent.charAt(i) != '\n' && fileContent.charAt(i) != '\r') {
                        i++;
                    }
                    i--; // Step back one character so the line ending is processed in the next loop iteration
                    continue;
                }
                
                // Only append if we're not in a comment
                if (!inBlockComment) {
                    // Handle spaces to avoid multiple consecutive spaces
                    if (ch == ' ') {
                        if (!lastWasSpace) {
                            currentLine.append(ch);
                            lastWasSpace = true;
                        }
                    } else {
                        currentLine.append(ch);
                        lastWasSpace = false;
                    }
                }
            }
            
            // Process any remaining content
            if (currentLine.length() > 0 && !inBlockComment) {
                result.append(handleInlineComments(currentLine.toString()));
            }
            
            return result.toString().trim();
        } catch (IOException e) {
            throw new RuntimeException("Error reading file " + resource, e);
        }
    }
    
    private static String handleInlineComments(String line) {
        int commentPos = indexOfOutsideString(line, "//");
        if (commentPos != -1) {
            return line.substring(0, commentPos);
        }
        return line;
    }
    
    private static int indexOfOutsideString(String line, String search) {
        boolean inString = false;
        char stringChar = 0;
        
        for (int i = 0; i < line.length() - search.length() + 1; i++) {
            char c = line.charAt(i);
            
            // Handle string literals
            if (c == '"' || c == '\'') {
                if (!inString) {
                    inString = true;
                    stringChar = c;
                } else if (c == stringChar) {
                    inString = false;
                }
                continue;
            }
            
            // Only look for comments outside strings
            if (!inString) {
                boolean found = true;
                for (int j = 0; j < search.length(); j++) {
                    if (line.charAt(i + j) != search.charAt(j)) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    return i;
                }
            }
        }
        
        return -1;
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
                return indexesAsJson.keySet().stream().filter(alias -> alias.startsWith(prefix)).collect(Collectors.toSet());
            }
        }
        return Collections.emptySet();
    }

    public static void cleanAllIndexWithRollover(CloseableHttpClient httpClient, BundleContext bundleContext, String esAddress, String prefix, String indexName) throws IOException {
        Set<String> indexes = getIndexesPrefixedBy(httpClient, esAddress, prefix + "-" + indexName + "-000");
        List<String> sortedIndexes = new ArrayList<>(indexes);
        Collections.sort(sortedIndexes);

        if (!sortedIndexes.isEmpty()) {
            String lastIndexName = sortedIndexes.remove(sortedIndexes.size() - 1);
            sortedIndexes.forEach(index -> {
                try {
                    deleteIndex(httpClient, esAddress, index);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            String matchAllBodyRequest = resourceAsString(bundleContext, "requestBody/2.2.0/match_all_body_request.json");

            try {
                deleteByQuery(httpClient, esAddress, lastIndexName, matchAllBodyRequest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String extractMappingFromBundles(BundleContext bundleContext, String fileName) throws IOException {
        for (Bundle bundle : bundleContext.getBundles()) {
            Enumeration<URL> predefinedMappings = bundle.findEntries("META-INF/cxs/mappings", fileName, true);
            if (predefinedMappings == null) {
                continue;
            }
            if (predefinedMappings.hasMoreElements()) {
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

    public static String buildIndexCreationRequestWithRollover(String baseIndexSettings, String mapping, MigrationContext context, String lifeCycleName, String rolloverAlias) throws IOException {
        return buildIndexCreationRequest(baseIndexSettings, mapping, context, false)
                .replace("#lifecycleName", lifeCycleName)
                .replace("#lifecycleRolloverAlias", rolloverAlias);
    }

    public static String buildRolloverPolicyCreationRequest(String baseRequest, MigrationContext migrationContext) throws IOException {

        StringJoiner rolloverHotActions = new StringJoiner(", ");

        String rolloverMaxAge = migrationContext.getConfigString("rolloverMaxAge");
        String rolloverMaxSize = migrationContext.getConfigString("rolloverMaxSize");
        String rolloverMaxDocs = migrationContext.getConfigString("rolloverMaxDocs");
        if (StringUtils.isNotBlank(rolloverMaxAge)) {
            rolloverHotActions.add("\"max_age\": \"" + rolloverMaxAge + "\"");
        }
        if (StringUtils.isNotBlank(rolloverMaxSize)) {
            rolloverHotActions.add("\"max_size\": \"" + rolloverMaxSize + "\"");
        }
        if (StringUtils.isNotBlank(rolloverMaxDocs)) {
            rolloverHotActions.add("\"max_docs\": \"" + rolloverMaxDocs + "\"");
        }
        return baseRequest.replace("#rolloverHotActions", rolloverHotActions.toString());
    }

    public static void moveToIndex(CloseableHttpClient httpClient, BundleContext bundleContext, String esAddress, String sourceIndexName, String targetIndexName, String painlessScript) throws Exception {
        moveToIndex(httpClient, bundleContext, esAddress, sourceIndexName, targetIndexName, painlessScript, null);
    }

    public static void moveToIndex(CloseableHttpClient httpClient, BundleContext bundleContext, String esAddress, String sourceIndexName, String targetIndexName, String painlessScript, Map<String, Object> scriptParams) throws Exception {
        String reIndexRequest = resourceAsString(bundleContext, "requestBody/2.2.0/base_reindex_request.json")
                .replace("#source", sourceIndexName)
                .replace("#dest", targetIndexName)
                .replace("#painless", StringUtils.isNotEmpty(painlessScript) ? getScriptPart(painlessScript, scriptParams) : "");

        // Reindex
        JSONObject task = new JSONObject(HttpUtils.executePostRequest(httpClient, esAddress + "/_reindex?wait_for_completion=false", reIndexRequest, null));
        //Wait for the reindex task to finish
        waitForTaskToFinish(httpClient, esAddress, task.getString("task"), null, "Reindex operation from " + sourceIndexName + " to " + targetIndexName);
    }

    public static void deleteIndex(CloseableHttpClient httpClient, String esAddress, String indexName) throws Exception {
        if (indexExists(httpClient, esAddress, indexName)) {
            HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexName, null);
        }
    }

    public static void reIndex(CloseableHttpClient httpClient, BundleContext bundleContext, String esAddress, String indexName, String newIndexSettings, String painlessScript, MigrationContext migrationContext, String migrationUniqueName) throws Exception {
        reIndex(httpClient, bundleContext, esAddress, indexName, newIndexSettings, painlessScript, null, migrationContext, migrationUniqueName);
    }

    public static void reIndex(CloseableHttpClient httpClient, BundleContext bundleContext, String esAddress, String indexName, String newIndexSettings, String painlessScript, Map<String, Object> scriptParams, MigrationContext migrationContext, String migrationUniqueName) throws Exception {
        if (indexName.endsWith("-cloned")) {
            // We should never reIndex a clone ...
            return;
        }

        String indexNameCloned = indexName + "-cloned";

        String reIndexRequest = resourceAsString(bundleContext, "requestBody/2.0.0/base_reindex_request.json")
                .replace("#source", indexNameCloned)
                .replace("#dest", indexName)
                .replace("#painless", StringUtils.isNotEmpty(painlessScript) ? getScriptPart(painlessScript, scriptParams) : "");

        String setIndexReadOnlyRequest = resourceAsString(bundleContext, "requestBody/2.0.0/base_set_index_readonly_request.json");

        migrationContext.performMigrationStep(migrationUniqueName + " - reindex step for: " + indexName + " (clone creation)", () -> {
            // Delete clone in case it already exists, could be incomplete from a previous reindex attempt, so better create a fresh one.
            if (indexExists(httpClient, esAddress, indexNameCloned)) {
                HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexNameCloned, null);
            }
            // Set original index as readOnly
            HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexName + "/_settings", setIndexReadOnlyRequest, null);
            // Clone the original index for backup
            HttpUtils.executePostRequest(httpClient, esAddress + "/" + indexName + "/_clone/" + indexNameCloned, null, null);
        });

        migrationContext.performMigrationStep(migrationUniqueName + " - reindex step for: " + indexName + " (recreate the index and perform the re-indexation)", () -> {
            // Delete original index if it still exists
            if (indexExists(httpClient, esAddress, indexName)) {
                HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexName, null);
            }
            // Recreate the original index with new mappings
            HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexName, newIndexSettings, null);
            // Reindex data from clone
            JSONObject task = new JSONObject(HttpUtils.executePostRequest(httpClient, esAddress + "/_reindex?wait_for_completion=false", reIndexRequest, null));
            //Wait for the reindex task to finish
            waitForTaskToFinish(httpClient, esAddress, task.getString("task"), migrationContext, "Reindex operation for " + indexName);
        });

        migrationContext.performMigrationStep(migrationUniqueName + " - reindex step for: " + indexName + " (delete clone)", () -> {
            // Delete original index if it still exists
            if (indexExists(httpClient, esAddress, indexNameCloned)) {
                HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexNameCloned, null);
            }
        });

        migrationContext.performMigrationStep(migrationUniqueName + " - reindex step for: " + indexName + " (refresh at the end)", () -> {
            // Do a refresh
            HttpUtils.executePostRequest(httpClient, esAddress + "/" + indexName + "/_refresh", null, null);

            waitForYellowStatus(httpClient, esAddress, migrationContext);
        });
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
            if (hits.isEmpty()) {
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
            response = HttpUtils.executePostRequest(httpClient, esAddress + "/_search/scroll", "{\n" + "  \"scroll_id\": \"" + scrollId + "\",\n" + "  \"scroll\": \"" + scrollDuration + "\"\n" + "}", null);
        }
    }

    /**
     * Utility method that waits for the ES cluster to be in yellow status
     */
    public static void waitForYellowStatus(CloseableHttpClient httpClient, String esAddress, MigrationContext migrationContext) throws Exception {
        while (true) {
            final JSONObject status = new JSONObject(HttpUtils.executeGetRequest(httpClient, esAddress + "/_cluster/health?wait_for_status=yellow&timeout=60s", null));
            if (!status.get("timed_out").equals("true")) {
                migrationContext.printMessage("ES Cluster status is " + status.get("status"));
                break;
            }
            migrationContext.printMessage("Waiting for ES Cluster status to be Yellow, current status is " + status.get("status"));
        }

    }

    /**
     * Updates documents in an index based on a specified query.
     *
     * <p>This method sends a request to update documents that match the provided query in the specified index. The update operation is
     * performed asynchronously, and the method waits for the task to complete before returning.</p>
     *
     * @param httpClient the CloseableHttpClient used to send the request to the Elasticsearch server
     * @param esAddress the address of the Elasticsearch server
     * @param indexName the name of the index where documents should be updated
     * @param requestBody the JSON body containing the query and update instructions for the documents
     * @throws Exception if there is an error during the HTTP request or while waiting for the task to finish
     */
    public static void updateByQuery(CloseableHttpClient httpClient, String esAddress, String indexName, String requestBody) throws Exception {
        JSONObject task = new JSONObject(HttpUtils.executePostRequest(httpClient, esAddress + "/" + indexName + "/_update_by_query?wait_for_completion=false", requestBody, null));

        //Wait for the update task to finish
        waitForTaskToFinish(httpClient, esAddress, task.getString("task"), null, "Update by query operation for " + indexName);
    }

    /**
     * Deletes documents from an index based on a specified query.
     *
     * <p>This method sends a request to the Elasticsearch cluster to delete documents
     * that match the provided query in the specified index. The deletion operation is
     * performed asynchronously, and the method waits for the task to complete before returning.</p>
     *
     * @param httpClient  the CloseableHttpClient used to send the request to the Elasticsearch server
     * @param esAddress   the address of the Elasticsearch server
     * @param indexName   the name of the index from which documents should be deleted
     * @param requestBody the JSON body containing the query that defines which documents to delete
     * @throws Exception if there is an error during the HTTP request or while waiting for the task to finish
     */
    public static void deleteByQuery(CloseableHttpClient httpClient, String esAddress, String indexName, String requestBody) throws Exception {
        JSONObject task = new JSONObject(HttpUtils.executePostRequest(httpClient, esAddress + "/" + indexName + "/_delete_by_query?wait_for_completion=false", requestBody, null));
        //Wait for the deletion task to finish
        waitForTaskToFinish(httpClient, esAddress, task.getString("task"), null, "Delete by query operation for " + indexName);
    }

    public static void waitForTaskToFinish(CloseableHttpClient httpClient, String esAddress, String taskId, MigrationContext migrationContext, String taskDescription) throws IOException {
        while (true) {
            final JSONObject status = new JSONObject(
                    HttpUtils.executeGetRequest(httpClient, esAddress + "/_tasks/" + taskId,
                            null));
            if (status.has("completed") && status.getBoolean("completed")) {
                if (migrationContext != null) {
                    migrationContext.printMessage("Task completed: " + taskDescription + " (task ID: " + taskId + ")");
                } else {
                    LOGGER.info("Task completed: {} (task ID: {})", taskDescription, taskId);
                }
                break;
            }
            if (status.has("error")) {
                final JSONObject error = status.getJSONObject("error");
                throw new IOException("Task error for " + taskDescription + " (task ID: " + taskId + "): " + error.getString("type") + " - " + error.getString("reason"));
            }

            double progress = -1;
            String progressMessage = "";
            if (status.has("task")) {
                JSONObject task = status.getJSONObject("task");
                if (task.has("status")) {
                    JSONObject taskStatus = task.getJSONObject("status");
                    int total = taskStatus.has("total") ? taskStatus.getInt("total") : -1;
                    int deleted = taskStatus.has("deleted") ? taskStatus.getInt("deleted") : -1;
                    int updated = taskStatus.has("updated") ? taskStatus.getInt("updated") : -1;
                    int created = taskStatus.has("created") ? taskStatus.getInt("created") : -1;
                    int noops = taskStatus.has("noops") ? taskStatus.getInt("noops") : -1;
                    if (total > 0 && deleted >= 0 && updated >= 0 && created >= 0 && noops >= 0) {
                        progress = ((double) updated + created + deleted + noops) / total;
                    }
                    
                    progressMessage = formatTaskProgress(progress, total, updated, created, deleted, noops);
                }
            }

            if (migrationContext != null) {
                migrationContext.printMessage(String.format("Task %s: %s%s", taskId, taskDescription, progressMessage));
            } else {
                LOGGER.info("Task {}: {}{}", taskId, taskDescription, progressMessage);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Formats the progress information for a task into a visually appealing string.
     *
     * @param progress the progress value between 0 and 1
     * @param total the total number of items to process
     * @param updated number of updated items
     * @param created number of created items
     * @param deleted number of deleted items
     * @param noops number of items that required no changes
     * @return a formatted string containing the progress bar and statistics
     */
    private static String formatTaskProgress(double progress, int total, int updated, int created, int deleted, int noops) {
        // Validate progress value
        if (progress < 0) {
            return " [                    ] 0.0%";
        }
        progress = Math.min(1.0, progress); // Ensure progress doesn't exceed 1.0
        
        // Create a compact progress bar
        int barWidth = 20;
        int filledLength = (int) (progress * barWidth);
        String progressBar = "[" + "=".repeat(filledLength) + ">".repeat(filledLength < barWidth ? 1 : 0) + " ".repeat(barWidth - filledLength - 1) + "]";
        
        // Format statistics in a compact way
        StringBuilder stats = new StringBuilder();
        if (total > 0) {
            stats.append(String.format(" %d/%d", updated + created + deleted + noops, total));
        }
        if (updated > 0 || created > 0 || deleted > 0 || noops > 0) {
            stats.append(" (");
            if (updated > 0) stats.append("↑").append(updated);
            if (created > 0) {
                if (updated > 0) stats.append(" ");
                stats.append("+").append(created);
            }
            if (deleted > 0) {
                if (created > 0 || updated > 0) stats.append(" ");
                stats.append("-").append(deleted);
            }
            if (noops > 0) {
                if (deleted > 0 || created > 0 || updated > 0) stats.append(" ");
                stats.append("~").append(noops);
            }
            stats.append(")");
        }
        
        return String.format(" %s %.1f%%%s", progressBar, progress * 100, stats.toString());
    }

    public static String getElasticMajorVersion(CloseableHttpClient httpClient, String esAddress) throws IOException {
        String response = HttpUtils.executeGetRequest(httpClient, esAddress, null);
        JSONObject jsonResponse = new JSONObject(response);
        String version = jsonResponse.getJSONObject("version").getString("number");
        return version.split("\\.")[0]; // Return major version number
    }

    public interface ScrollCallback {
        void execute(String hits);
    }

    private static String getScriptPart(String painlessScript, Map<String, Object> params) {
        JSONObject scriptObj = new JSONObject();
        scriptObj.put("source", painlessScript);
        scriptObj.put("lang", "painless");
        
        if (params != null && !params.isEmpty()) {
            JSONObject paramsObj = new JSONObject();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                paramsObj.put(entry.getKey(), entry.getValue());
            }
            scriptObj.put("params", paramsObj);
        }
        
        return ", \"script\": " + scriptObj.toString();
    }

    /**
     * Creates a new index with the specified settings
     *
     * @param httpClient the HTTP client to use
     * @param esAddress the Elasticsearch address
     * @param indexName the name of the index to create
     * @param settings the settings and mappings for the index
     * @throws IOException if there is an error during the HTTP request
     */
    public static void createIndex(CloseableHttpClient httpClient, String esAddress, String indexName, String settings) throws IOException {
        HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexName, settings, null);
    }

    /**
     * Indexes a document in Elasticsearch
     *
     * @param httpClient the HTTP client to use
     * @param esAddress the Elasticsearch address
     * @param indexName the name of the index
     * @param type the document type (e.g., "_doc")
     * @param id the document ID
     * @param jsonData the document data in JSON format
     * @throws IOException if there is an error during the HTTP request
     */
    public static void indexData(CloseableHttpClient httpClient, String esAddress, String indexName, String type, String id, String jsonData) throws IOException {
        HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexName + "/" + type + "/" + id, jsonData, null);
    }

    /**
     * Gets all unique item types from the specified index
     *
     * @param httpClient the HTTP client to use
     * @param esAddress the Elasticsearch address
     * @param indexPrefix the index prefix
     * @param indexName the name of the index, can be "*" to get all item types from all indices
     * @param bundleContext the bundle context to load resources
     * @return Set of unique item types
     * @throws IOException if there is an error during the HTTP request
     */
    public static Set<String> getAllItemTypes(CloseableHttpClient httpClient, String esAddress, String indexPrefix, String indexName, BundleContext bundleContext) throws IOException {
        String systemItemsIndex = indexPrefix + "-" + indexName;
        String query = resourceAsString(bundleContext, "requestBody/3.0.0/get_item_types_query.json");

        String response = HttpUtils.executePostRequest(httpClient, esAddress + "/" + systemItemsIndex + "/_search", query, null);
        JSONObject jsonResponse = new JSONObject(response);
        JSONArray buckets = jsonResponse.getJSONObject("aggregations").getJSONObject("itemTypes").getJSONArray("buckets");
        
        Set<String> itemTypes = new HashSet<>();
        for (int i = 0; i < buckets.length(); i++) {
            itemTypes.add(buckets.getJSONObject(i).getString("key"));
        }
        
        return itemTypes;
    }
}
