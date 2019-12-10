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
import org.apache.unomi.shell.migration.utils.ConsoleUtils;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class MigrationTo150 implements Migration {

    public static final String INDEX_DATE_PREFIX = "date-";

    @Override
    public Version getFromVersion() {
        return new Version("1.3.0");
    }

    @Override
    public Version getToVersion() {
        return new Version("1.5.0");
    }

    @Override
    public String getDescription() {
        return "Migrate the data from ElasticSearch 5.6 to 7.4";
    }

    @Override
    public void execute(Session session, CloseableHttpClient httpClient, String esAddress, BundleContext bundleContext) throws IOException {
        String es5Address = ConsoleUtils.askUserWithDefaultAnswer(session, "SOURCE Elasticsearch 5.6 cluster address (default: http://localhost:9210) : ", "http://localhost:9210");
        String sourceIndexPrefix = ConsoleUtils.askUserWithDefaultAnswer(session, "SOURCE index name (default: context) : ", "context");
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
                JSONObject indexBody = new JSONObject();
                indexBody.put("mappings", new JSONObject(mappingDefinition));
                if (!monthlyIndexTypes.contains(itemType)) {
                    createESIndex(httpClient, esAddress, destIndexPrefix, itemType.toLowerCase(), numberOfShards, numberOfReplicas, indexBody);
                    if ("geonameEntry".equals(itemType)) {
                        reIndex(session, httpClient, esAddress, es5Address, "geonames", destIndexPrefix + "-" + itemType.toLowerCase(), itemType);
                    } else {
                        reIndex(session, httpClient, esAddress, es5Address, sourceIndexPrefix, destIndexPrefix + "-" + itemType.toLowerCase(), itemType);
                    }
                } else {
                    for (String indexName : monthlyIndexNames) {
                        // we need to extract the date part
                        String datePart = indexName.substring(sourceIndexPrefix.length() + 1);
                        createESIndex(httpClient, esAddress, destIndexPrefix, itemType.toLowerCase() + "-" + INDEX_DATE_PREFIX + datePart, numberOfShards, numberOfReplicas, indexBody);
                        reIndex(session, httpClient, esAddress, es5Address, indexName, destIndexPrefix + "-" + itemType.toLowerCase() + "-" + INDEX_DATE_PREFIX + datePart, itemType);
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

    private String createESIndex(CloseableHttpClient httpClient, String esAddress, String indexPrefix, String indexName, int numberOfShards, int numberOfReplicas, JSONObject indexBody) throws IOException {
        indexBody.put("settings", new JSONObject()
                .put("index", new JSONObject()
                        .put("number_of_shards", numberOfShards)
                        .put("number_of_replicas", numberOfReplicas)
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
        return HttpUtils.executePutRequest(httpClient, esAddress + "/" + indexPrefix + "-" + indexName, indexBody.toString(), null);
    }

    private String reIndex(Session session, CloseableHttpClient httpClient,
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
        String response = HttpUtils.executePostRequest(httpClient, esAddress + "/_reindex", reindexSettings.toString(), null);
        long reindexationTime = System.currentTimeMillis() - startTime;
        ConsoleUtils.printMessage(session, "Reindexing completed in " + reindexationTime + "ms. Result=" + response);
        return response;
    }

}
