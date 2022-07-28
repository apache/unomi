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
package org.apache.unomi.shell.migration.impl;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.Migration;
import org.apache.unomi.shell.migration.MigrationConfig;
import org.apache.unomi.shell.migration.utils.ConsoleUtils;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

public class MigrationTo150 implements Migration {

    public static final String INDEX_DATE_PREFIX = "date-";

    @Override
    public void execute(Session session, CloseableHttpClient httpClient, MigrationConfig migrationConfig, BundleContext bundleContext) throws IOException {
        String esAddress = migrationConfig.getString(MigrationConfig.CONFIG_ES_ADDRESS, session);
        String es5Address = ConsoleUtils.askUserWithDefaultAnswer(session, "SOURCE Elasticsearch 5.6 cluster address (default: http://localhost:9210) : ", "http://localhost:9210");
        String sourceIndexPrefix = migrationConfig.getString(MigrationConfig.INDEX_PREFIX, session);
        String destIndexPrefix = ConsoleUtils.askUserWithDefaultAnswer(session, "TARGET index prefix (default: context) : ", "context");
        int numberOfShards = Integer.parseInt(ConsoleUtils.askUserWithDefaultAnswer(session, "Number of shards for TARGET (default: 5) : ", "5"));
        int numberOfReplicas = Integer.parseInt(ConsoleUtils.askUserWithDefaultAnswer(session, "Number of replicas for TARGET (default: 1) : ", "1"));
        Set<String> monthlyIndexTypes = new HashSet<>();
        monthlyIndexTypes.add("event");
        monthlyIndexTypes.add("session");
        long startTime = System.currentTimeMillis();
        // let's try to connect to see if its correct
        JSONObject indicesStats = new JSONObject(HttpUtils.executeGetRequest(httpClient, es5Address + "/_stats", null));
        JSONObject indices = indicesStats.getJSONObject("indices");
        Set<String> indexNames = new TreeSet<>(indices.keySet());
        Set<String> monthlyIndexNames = new TreeSet<>();
        for (String indexName : indexNames) {
            if (indexName.startsWith(sourceIndexPrefix + "-")) {
                monthlyIndexNames.add(indexName);
            }
        }

        // now let's load all installed mappings but we will still be missing some from optional extensions such as the Salesforce connector
        for (Bundle bundle : bundleContext.getBundles()) {
            Enumeration<URL> predefinedMappings = bundle.findEntries("META-INF/cxs/mappings", "*.json", true);
            if (predefinedMappings == null) {
                continue;
            }
            while (predefinedMappings.hasMoreElements()) {
                URL predefinedMappingURL = predefinedMappings.nextElement();
                final String path = predefinedMappingURL.getPath();
                String itemType = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                String mappingDefinition = loadMappingFile(predefinedMappingURL);
                JSONObject newTypeMapping = new JSONObject(mappingDefinition);
                if (!monthlyIndexTypes.contains(itemType)) {
                    String indexName = "geonameEntry".equals(itemType) ? "geonames" : sourceIndexPrefix;

                    JSONObject es5TypeMapping = getES5TypeMapping(session, httpClient, es5Address, indexName, itemType);
                    int es5MappingsTotalFieldsLimit = getES5MappingsTotalFieldsLimit(httpClient, es5Address, indexName);
                    String destIndexName = itemType.toLowerCase();
                    if (!indexExists(httpClient, esAddress, destIndexPrefix, destIndexName)) {
                        createESIndex(httpClient, esAddress, destIndexPrefix, destIndexName, numberOfShards, numberOfReplicas, es5MappingsTotalFieldsLimit, getMergedTypeMapping(es5TypeMapping, newTypeMapping));
                        reIndex(session, httpClient, esAddress, es5Address, indexName, getIndexName(destIndexPrefix, destIndexName), itemType);
                    } else {
                        ConsoleUtils.printMessage(session, "Index " + getIndexName(destIndexPrefix, itemType.toLowerCase()) + " already exists, skipping re-indexation...");
                    }
                } else {
                    for (String indexName : monthlyIndexNames) {
                        // we need to extract the date part
                        String datePart = indexName.substring(sourceIndexPrefix.length() + 1);
                        String destIndexName = itemType.toLowerCase() + "-" + INDEX_DATE_PREFIX + datePart;
                        JSONObject es5TypeMapping = getES5TypeMapping(session, httpClient, es5Address, indexName, itemType);
                        int es5MappingsTotalFieldsLimit = getES5MappingsTotalFieldsLimit(httpClient, es5Address, indexName);
                        if (!indexExists(httpClient, esAddress, destIndexPrefix, destIndexName)) {
                            createESIndex(httpClient, esAddress, destIndexPrefix, destIndexName, numberOfShards, numberOfReplicas, es5MappingsTotalFieldsLimit, getMergedTypeMapping(es5TypeMapping, newTypeMapping));
                            reIndex(session, httpClient, esAddress, es5Address, indexName, getIndexName(destIndexPrefix, destIndexName), itemType);
                        } else {
                            ConsoleUtils.printMessage(session, "Index " + getIndexName(destIndexPrefix, destIndexName) + " already exists, skipping re-indexation...");
                        }
                    }
                }
            }
        }

        long totalMigrationTime = System.currentTimeMillis() - startTime;
        ConsoleUtils.printMessage(session, "Migration operations completed in " + totalMigrationTime + "ms");
    }

    private String loadMappingFile(URL predefinedMappingURL) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(predefinedMappingURL.openStream()));
        StringBuilder content = new StringBuilder();
        String l;
        while ((l = reader.readLine()) != null) {
            content.append(l);
        }
        return content.toString();
    }

    private boolean indexExists(CloseableHttpClient httpClient, String esAddress, String indexPrefix, String indexName) {
        try {
            HttpUtils.executeHeadRequest(httpClient, esAddress + "/" + getIndexName(indexPrefix, indexName), null);
        } catch (IOException e) {
            // this simply means the index doesn't exist (normally)
            return false;
        }
        return true;
    }

    private String getIndexName(String indexPrefix, String indexName) {
        return indexPrefix + "-" + indexName;
    }

    private void createESIndex(CloseableHttpClient httpClient, String esAddress, String indexPrefix, String indexName, int numberOfShards, int numberOfReplicas, int mappingTotalFieldsLimit, JSONObject indexBody) throws IOException {
        indexBody.put("settings", new JSONObject()
                .put("index", new JSONObject()
                        .put("number_of_shards", numberOfShards)
                        .put("number_of_replicas", numberOfReplicas)
                        .put("max_docvalue_fields_search", 1000)
                )
                .put("analysis", new JSONObject()
                        .put("analyzer", new JSONObject()
                                .put("folding", new JSONObject()
                                        .put("type", "custom")
                                        .put("tokenizer", "keyword")
                                        .put("filter", new JSONArray().put("lowercase").put("asciifolding"))
                                )
                        )
                )
        );
        if (mappingTotalFieldsLimit != -1) {
            indexBody.getJSONObject("settings").getJSONObject("index")
                    .put("mapping", new JSONObject()
                            .put("total_fields", new JSONObject()
                                    .put("limit", mappingTotalFieldsLimit)
                            )
                    );
        }
        HttpUtils.executePutRequest(httpClient, esAddress + "/" + getIndexName(indexPrefix, indexName), indexBody.toString(), null);
    }

    private void reIndex(Session session, CloseableHttpClient httpClient,
                           String esAddress,
                           String es5Address,
                           String sourceIndexName,
                           String destIndexName,
                           String itemType) throws IOException {
        JSONObject reindexSettings = new JSONObject();
        reindexSettings
                .put("source", new JSONObject()
                        .put("remote", new JSONObject()
                                .put("host", es5Address)
                        )
                        .put("index", sourceIndexName)
                        .put("type", itemType)
                )
                .put("dest", new JSONObject()
                        .put("index", destIndexName)
                );
        ConsoleUtils.printMessage(session, "Reindexing " + sourceIndexName + " to " + destIndexName + "...");
        long startTime = System.currentTimeMillis();
        try {
            String response = HttpUtils.executePostRequest(httpClient, esAddress + "/_reindex", reindexSettings.toString(), null);
            long reindexationTime = System.currentTimeMillis() - startTime;
            ConsoleUtils.printMessage(session, "Reindexing completed in " + reindexationTime + "ms. Result=" + response);
        } catch (IOException ioe) {
            ConsoleUtils.printException(session, "Error executing reindexing", ioe);
            ConsoleUtils.printMessage(session, "Attempting to delete index " + destIndexName + " so that we can restart from this point...");
            deleteIndex(session, httpClient, esAddress, destIndexName);
            throw ioe;
        }
    }

    private void deleteIndex(Session session, CloseableHttpClient httpClient,
                        String esAddress,
                        String indexName) {
        try {
            HttpUtils.executeDeleteRequest(httpClient, esAddress + "/" + indexName, null);
        } catch (IOException ioe) {
            ConsoleUtils.printException(session, "Error attempting to delete index" + indexName, ioe);
        }
    }

    private JSONObject getES5TypeMapping(Session session, CloseableHttpClient httpClient, String es5Address, String indexName, String typeName) throws IOException {
        String response = HttpUtils.executeGetRequest(httpClient, es5Address + "/" + indexName, null);
        if (response != null) {
            JSONObject indexInfo = new JSONObject(response).getJSONObject(indexName);
            JSONObject allTypeMappings = indexInfo.getJSONObject("mappings");
            if (allTypeMappings.has(typeName)) {
                return allTypeMappings.getJSONObject(typeName);
            } else {
                return new JSONObject();
            }
        } else {
            return new JSONObject();
        }
    }

    private int getES5MappingsTotalFieldsLimit(CloseableHttpClient httpClient, String es5Address, String indexName) throws IOException {
        String response = HttpUtils.executeGetRequest(httpClient, es5Address + "/" + indexName, null);
        if (response != null) {
            JSONObject indexInfo = new JSONObject(response).getJSONObject(indexName);
            JSONObject settings = indexInfo.getJSONObject("settings");
            if (settings.has("index")) {
                JSONObject indexSettings = settings.getJSONObject("index");
                if (indexSettings.has("mapping")) {
                    JSONObject mappingIndexSettings = indexSettings.getJSONObject("mapping");
                    if (mappingIndexSettings.has("total_fields")) {
                        JSONObject totalFieldsMappingIndexSettings = mappingIndexSettings.getJSONObject("total_fields");
                        if (totalFieldsMappingIndexSettings.has("limit")) {
                            return totalFieldsMappingIndexSettings.getInt("limit");
                        }
                    }
                }
            }
        }
        return -1;
    }

    private JSONObject getMergedTypeMapping(JSONObject oldTypeMappings, JSONObject newTypeMappings) {
        JSONObject mappings = new JSONObject(oldTypeMappings.toString());
        if (newTypeMappings.has("dynamic_templates")) {
            mappings.put("dynamic_templates", newTypeMappings.getJSONArray("dynamic_templates"));
        }
        if (newTypeMappings.has("properties")) {
            if (mappings.has("properties")) {
                mappings.put("properties", getMergedPropertyMappings(mappings.getJSONObject("properties"), newTypeMappings.getJSONObject("properties")));
            } else {
                mappings.put("properties", newTypeMappings.getJSONObject("properties"));
            }
        }
        return new JSONObject().put("mappings", mappings);
    }

    private JSONObject getMergedPropertyMappings(JSONObject oldProperties, JSONObject newProperties) {
        JSONObject result = new JSONObject();
        for (String oldPropertyName : oldProperties.keySet()) {
            if (!newProperties.has(oldPropertyName)) {
                // we copy the old value over to the result
                result.put(oldPropertyName, oldProperties.get(oldPropertyName));
                continue;
            }
            JSONObject oldProperty = oldProperties.getJSONObject(oldPropertyName);
            JSONObject newProperty = newProperties.getJSONObject(oldPropertyName);
            if (oldProperty.has("properties") && newProperty.has("properties")) {
                // we are in the case of an object, we merge deeper
                JSONObject newObjectMapping = new JSONObject(newProperty.toString());
                newObjectMapping.put("properties", getMergedPropertyMappings(oldProperty.getJSONObject("properties"), newProperty.getJSONObject("properties")));
                result.put(oldPropertyName, newObjectMapping);
            } else {
                // in all other cases we copy the new value.
                result.put(oldPropertyName, newProperties.get(oldPropertyName));
            }
        }
        for (String newPropertyName : newProperties.keySet()) {
            if (!oldProperties.has(newPropertyName)) {
                result.putOnce(newPropertyName, newProperties.get(newPropertyName));
            }
        }
        return result;
    }
}
