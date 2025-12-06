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

    public static void configureAlias(CloseableHttpClient httpClient, String esAddress, String alias, String writeIndex, Set<String> readIndices, String configureAliasBody, MigrationContext context) throws IOException {
        String readIndicesToAdd = "";
        if (!readIndices.isEmpty()) {
            readIndicesToAdd = "," + readIndices.stream().map(index -> "{\"add\": {\"index\": \"" + index + "\", \"alias\": \"" + alias + "\", \"is_write_index\": false}}").collect(Collectors.joining(","));
        }
        if (context != null) {
            context.printMessage("Will set " + writeIndex + " as write index for alias " + alias);
            context.printMessage("Will set " + readIndices.toString() + " as read indices");
        } else {
            LOGGER.info("Will set {} as write index for alias {}", writeIndex, alias);
            LOGGER.info("Will set {} as read indices", readIndices.toString());
        }
        String requestBody = configureAliasBody.replace("#writeIndexName", writeIndex).replace("#aliasName", alias).replace("#readIndicesToAdd", readIndicesToAdd);

        HttpUtils.executePostRequest(httpClient, esAddress + "/_aliases", requestBody, null);
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
            if (status.has("error")) {
                final JSONObject error = status.getJSONObject("error");
                throw new IOException("Task error for " + taskDescription + " (task ID: " + taskId + "): " + error.getString("type") + " - " + error.getString("reason"));
            }
            if (status.has("completed") && status.getBoolean("completed")) {
                String completionMessage = formatTaskCompletion(status, taskDescription, taskId);
                if (migrationContext != null) {
                    migrationContext.printMessage(completionMessage);
                } else {
                    LOGGER.info(completionMessage);
                }
                break;
            }

            String progressMessage = formatTaskProgress(status);

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

    // Constants for task status JSON field names
    private static final String JSON_KEY_TASK = "task";
    private static final String JSON_KEY_STATUS = "status";
    private static final String JSON_KEY_RUNNING_TIME_IN_NANOS = "running_time_in_nanos";
    private static final String JSON_KEY_TOTAL = "total";
    private static final String JSON_KEY_DELETED = "deleted";
    private static final String JSON_KEY_UPDATED = "updated";
    private static final String JSON_KEY_CREATED = "created";
    private static final String JSON_KEY_NOOPS = "noops";
    private static final String JSON_KEY_BATCHES = "batches";
    private static final String JSON_KEY_VERSION_CONFLICTS = "version_conflicts";
    private static final String JSON_KEY_THROTTLED_MILLIS = "throttled_millis";
    private static final String JSON_KEY_REQUESTS_PER_SECOND = "requests_per_second";
    
    // Constants for progress bar formatting
    private static final double PROGRESS_BAR_WIDTH = 20.0;
    private static final double PROGRESS_COMPLETE = 1.0;
    private static final double PROGRESS_UNKNOWN = -1.0;
    private static final int PROGRESS_PERCENTAGE_MULTIPLIER = 100;
    private static final int NANOSECONDS_TO_MILLISECONDS = 1_000_000;
    
    // Constants for progress bar display
    private static final String PROGRESS_BAR_COMPLETED = "[====================] 100.0%";
    private static final String PROGRESS_BAR_UNKNOWN = "[                    ] 0.0%";
    private static final String PROGRESS_BAR_START = "[";
    private static final String PROGRESS_BAR_END = "]";
    private static final String PROGRESS_BAR_FILL = "=";
    private static final String PROGRESS_BAR_CURSOR = ">";
    private static final String PROGRESS_BAR_EMPTY = " ";
    
    // Constants for operation count symbols
    private static final String OPERATION_UPDATED = "↑";
    private static final String OPERATION_CREATED = "+";
    private static final String OPERATION_DELETED = "-";
    private static final String OPERATION_NOOPS = "~";
    
    // Constants for labels
    private static final String LABEL_ELAPSED = "elapsed";
    private static final String LABEL_DURATION = "duration";
    private static final String LABEL_REQUESTS_PER_SECOND = " req/s";
    
    /**
     * Data class to hold task statistics extracted from Elasticsearch task status.
     */
    private static class TaskStatistics {
        int total = -1;
        int updated = 0;
        int created = 0;
        int deleted = 0;
        int noops = 0;
        int batches = 0;
        int versionConflicts = 0;
        long runningTimeNanos = -1;
        long throttledMillis = 0;
        double requestsPerSecond = -1;
        
        /**
         * Calculates the progress percentage based on completed operations.
         * @return progress value between 0.0 and 1.0, or -1 if progress cannot be calculated
         */
        double calculateProgress() {
            if (total > 0 && deleted >= 0 && updated >= 0 && created >= 0 && noops >= 0) {
                return Math.min(PROGRESS_COMPLETE, ((double) updated + created + deleted + noops) / total);
            }
            return PROGRESS_UNKNOWN;
        }
        
        /**
         * Gets the total number of completed operations.
         * @return sum of updated, created, deleted, and noops
         */
        int getCompletedCount() {
            return updated + created + deleted + noops;
        }
    }

    /**
     * Extracts task statistics from an Elasticsearch task status JSON object.
     * Uses opt*() methods for null safety as per code quality rules.
     *
     * @param status the full task status JSON object (must not be null)
     * @return TaskStatistics object containing extracted statistics
     * @throws NullPointerException if status is null
     */
    private static TaskStatistics extractTaskStatistics(JSONObject status) {
        Objects.requireNonNull(status, "status cannot be null");
        
        TaskStatistics stats = new TaskStatistics();
        
        JSONObject task = status.optJSONObject(JSON_KEY_TASK);
        if (task != null) {
            stats.runningTimeNanos = task.optLong(JSON_KEY_RUNNING_TIME_IN_NANOS, -1);
            
            JSONObject taskStatus = task.optJSONObject(JSON_KEY_STATUS);
            if (taskStatus != null) {
                stats.total = taskStatus.optInt(JSON_KEY_TOTAL, -1);
                stats.deleted = taskStatus.optInt(JSON_KEY_DELETED, 0);
                stats.updated = taskStatus.optInt(JSON_KEY_UPDATED, 0);
                stats.created = taskStatus.optInt(JSON_KEY_CREATED, 0);
                stats.noops = taskStatus.optInt(JSON_KEY_NOOPS, 0);
                stats.batches = taskStatus.optInt(JSON_KEY_BATCHES, 0);
                stats.versionConflicts = taskStatus.optInt(JSON_KEY_VERSION_CONFLICTS, 0);
                stats.throttledMillis = taskStatus.optLong(JSON_KEY_THROTTLED_MILLIS, 0);
                
                double rps = taskStatus.optDouble(JSON_KEY_REQUESTS_PER_SECOND, -1);
                if (rps >= 0) {
                    stats.requestsPerSecond = rps;
                }
            }
        }
        
        return stats;
    }

    /**
     * Appends an operation count to the result if the count is greater than zero.
     *
     * @param result the StringBuilder to append to
     * @param count the operation count
     * @param symbol the symbol to use for this operation type
     * @param isFirst whether this is the first operation being appended
     * @return false if an operation was appended, true if it was skipped
     */
    private static boolean appendOperationCount(StringBuilder result, int count, String symbol, boolean isFirst) {
        if (count > 0) {
            if (!isFirst) {
                result.append(" ");
            }
            result.append(symbol).append(count);
            return false;
        }
        return isFirst;
    }

    /**
     * Formats operation counts in a compact format: (↑updated +created -deleted ~noops)
     *
     * @param stats the task statistics (must not be null)
     * @return formatted operation counts string, or empty string if no operations
     * @throws NullPointerException if stats is null
     */
    private static String formatOperationCounts(TaskStatistics stats) {
        Objects.requireNonNull(stats, "stats cannot be null");
        
        if (stats.updated == 0 && stats.created == 0 && stats.deleted == 0 && stats.noops == 0) {
            return "";
        }
        
        StringBuilder result = new StringBuilder(" (");
        boolean first = true;
        
        first = appendOperationCount(result, stats.updated, OPERATION_UPDATED, first);
        first = appendOperationCount(result, stats.created, OPERATION_CREATED, first);
        first = appendOperationCount(result, stats.deleted, OPERATION_DELETED, first);
        appendOperationCount(result, stats.noops, OPERATION_NOOPS, first);
        
        result.append(")");
        return result.toString();
    }

    /**
     * Formats additional task information (batches, conflicts, throttled time, duration, requests per second).
     *
     * @param stats the task statistics (must not be null)
     * @param includeRequestsPerSecond whether to include requests per second (only for progress, not completion)
     * @param useElapsedLabel whether to use "elapsed" label (true) or "duration" label (false)
     * @return formatted additional information string
     * @throws NullPointerException if stats is null
     */
    private static String formatAdditionalInfo(TaskStatistics stats, boolean includeRequestsPerSecond, boolean useElapsedLabel) {
        Objects.requireNonNull(stats, "stats cannot be null");
        
        StringBuilder result = new StringBuilder();
        
        if (stats.batches > 0) {
            result.append(" batches:").append(stats.batches);
        }
        if (stats.versionConflicts > 0) {
            result.append(" conflicts:").append(stats.versionConflicts);
        }
        if (stats.throttledMillis > 0) {
            result.append(" throttled:").append(formatDuration(stats.throttledMillis));
        }
        if (includeRequestsPerSecond && stats.requestsPerSecond >= 0) {
            result.append(" ").append(String.format("%.1f", stats.requestsPerSecond)).append(LABEL_REQUESTS_PER_SECOND);
        }
        if (stats.runningTimeNanos > 0) {
            String label = useElapsedLabel ? LABEL_ELAPSED : LABEL_DURATION;
            result.append(" ").append(label).append(":").append(formatDuration(stats.runningTimeNanos / NANOSECONDS_TO_MILLISECONDS));
        }
        
        return result.toString();
    }

    /**
     * Creates a progress bar string based on the progress percentage.
     *
     * @param progress the progress value between 0.0 and 1.0, or -1 for unknown
     * @param isCompleted whether this is a completed task (always shows 100%)
     * @return formatted progress bar string
     */
    private static String createProgressBar(double progress, boolean isCompleted) {
        if (isCompleted) {
            return PROGRESS_BAR_COMPLETED;
        }
        
        if (progress < 0) {
            return PROGRESS_BAR_UNKNOWN;
        }
        
        int filledLength = (int) (progress * PROGRESS_BAR_WIDTH);
        int leftOver = (int) (PROGRESS_BAR_WIDTH - filledLength - 1.0);
        boolean needsCursor = filledLength < PROGRESS_BAR_WIDTH;
        
        String progressBar = PROGRESS_BAR_START 
                + PROGRESS_BAR_FILL.repeat(filledLength)
                + (needsCursor ? PROGRESS_BAR_CURSOR : "")
                + PROGRESS_BAR_EMPTY.repeat(leftOver)
                + PROGRESS_BAR_END;
        
        return String.format("%s %.1f%%", progressBar, progress * PROGRESS_PERCENTAGE_MULTIPLIER);
    }

    /**
     * Formats the progress information for a task into a visually appealing string.
     * Extracts all available information from the task status response.
     *
     * @param status the full task status JSON object (must not be null)
     * @return a formatted string containing the progress bar and statistics
     * @throws NullPointerException if status is null
     */
    private static String formatTaskProgress(JSONObject status) {
        Objects.requireNonNull(status, "status cannot be null");
        
        TaskStatistics stats = extractTaskStatistics(status);
        double progress = stats.calculateProgress();
        
        String progressBar = createProgressBar(progress, false);
        
        StringBuilder result = new StringBuilder(" ").append(progressBar);
        
        if (stats.total > 0) {
            result.append(String.format(" %d/%d", stats.getCompletedCount(), stats.total));
        }
        
        String operationCounts = formatOperationCounts(stats);
        if (!operationCounts.isEmpty()) {
            result.append(operationCounts);
        }
        
        result.append(formatAdditionalInfo(stats, true, true));
        
        return result.toString();
    }

    /**
     * Builds a progress bar with statistics for a completed task.
     *
     * @param stats the task statistics
     * @return formatted progress bar string with statistics, or empty string if no task data
     */
    private static String buildCompletedProgressBarWithStats(TaskStatistics stats) {
        String progressBar = createProgressBar(PROGRESS_COMPLETE, true);
        StringBuilder progressBarWithStats = new StringBuilder(progressBar);
        
        if (stats.total >= 0) {
            progressBarWithStats.append(String.format(" %d/%d", stats.getCompletedCount(), stats.total));
        }
        
        String operationCounts = formatOperationCounts(stats);
        if (!operationCounts.isEmpty()) {
            progressBarWithStats.append(operationCounts);
        }
        
        progressBarWithStats.append(formatAdditionalInfo(stats, false, false));
        return progressBarWithStats.toString();
    }

    /**
     * Formats the completion message for a finished task with final statistics.
     *
     * @param status the full task status JSON object (must not be null)
     * @param taskDescription the description of the task (must not be null or empty)
     * @param taskId the task ID (must not be null or empty)
     * @return a formatted completion message with progress bar
     * @throws NullPointerException if status, taskDescription, or taskId is null
     * @throws IllegalArgumentException if taskDescription or taskId is empty
     */
    private static String formatTaskCompletion(JSONObject status, String taskDescription, String taskId) {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(taskDescription, "taskDescription cannot be null");
        Objects.requireNonNull(taskId, "taskId cannot be null");
        
        if (taskDescription.trim().isEmpty()) {
            throw new IllegalArgumentException("taskDescription cannot be empty");
        }
        if (taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("taskId cannot be empty");
        }
        
        StringBuilder message = new StringBuilder("Task completed: ").append(taskDescription).append(" (task ID: ").append(taskId).append(")");
        
        if (status.has(JSON_KEY_TASK)) {
            TaskStatistics stats = extractTaskStatistics(status);
            message.append(" ").append(buildCompletedProgressBarWithStats(stats));
        }
        
        return message.toString();
    }

    /**
     * Formats a duration in milliseconds into a human-readable string.
     *
     * @param millis the duration in milliseconds
     * @return a formatted duration string (e.g., "1m 23s", "45s", "2h 15m")
     */
    private static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }
        
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) {
            if (seconds > 0) {
                return minutes + "m " + seconds + "s";
            }
            return minutes + "m";
        }
        
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours < 24) {
            StringBuilder result = new StringBuilder();
            result.append(hours).append("h");
            if (minutes > 0) {
                result.append(" ").append(minutes).append("m");
            }
            if (seconds > 0 && minutes == 0) {
                result.append(" ").append(seconds).append("s");
            }
            return result.toString();
        }
        
        long days = hours / 24;
        hours = hours % 24;
        StringBuilder result = new StringBuilder();
        result.append(days).append("d");
        if (hours > 0) {
            result.append(" ").append(hours).append("h");
        }
        if (minutes > 0 && hours == 0) {
            result.append(" ").append(minutes).append("m");
        }
        return result.toString();
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
        String query = resourceAsString(bundleContext, "requestBody/3.1.0/get_item_types_query.json");

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
