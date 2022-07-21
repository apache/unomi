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

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.Migration;
import org.apache.unomi.shell.migration.utils.ConsoleUtils;
import org.apache.unomi.shell.migration.utils.MigrationUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MigrationTo200 implements Migration {

    private CloseableHttpClient httpClient;
    private Session session;
    private String esAddress;
    private BundleContext bundleContext;

    @Override
    public void execute(Session session, CloseableHttpClient httpClient, Map<String, Object> migrationConfig, BundleContext bundleContext) throws IOException {
        this.httpClient = httpClient;
        this.session = session;
        this.esAddress = (String) migrationConfig.get("esAddress");
        this.bundleContext = bundleContext;

        doExecute((String) migrationConfig.get("indexPrefix"));
    }

    private void doExecute(String indexPrefix) throws IOException {
        Set<String> indexes = MigrationUtils.getIndexesPrefixedBy(httpClient, esAddress, indexPrefix + "-event-");
        createScopeMapping(indexPrefix);
        createScopes(getSetOfScopes(indexes), indexPrefix);
        createProfileAliasDocumentsFromProfile();
    }

    private boolean scopeIndexNotExists(String indexPrefix) throws IOException {
        final HttpGet httpGet = new HttpGet(esAddress + "/" + indexPrefix + "-scope");

        httpGet.addHeader("Accept", "application/json");
        httpGet.addHeader("Content-Type", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            return response.getStatusLine().getStatusCode() != HttpStatus.SC_OK;
        }
    }

    private void createScopeMapping(String indexPrefix) throws IOException {

        if (scopeIndexNotExists(indexPrefix)) {
            System.out.println("Creation for index = \"" + indexPrefix + "-scope\" starting.");
            System.out.println("Specify the following parameters:");
            String numberOfShards = ConsoleUtils.askUserWithDefaultAnswer(session, "number_of_shards: (default: 3)", "3");
            String numberOfReplicas = ConsoleUtils.askUserWithDefaultAnswer(session, "number_of_replicas: (default: 0)", "0");
            String mappingTotalFieldsLimit = ConsoleUtils
                    .askUserWithDefaultAnswer(session, "mapping.total_fields.limit: (default: 1000)", "1000");
            String maxDocValueFieldsSearch = ConsoleUtils
                    .askUserWithDefaultAnswer(session, "max_docvalue_fields_search: (default: 1000)", "1000");

            final HttpPut httpPost = new HttpPut(esAddress + "/" + indexPrefix + "-scope");

            httpPost.addHeader("Accept", "application/json");
            httpPost.addHeader("Content-Type", "application/json");

            String request = MigrationUtils.resourceAsString(bundleContext,"requestBody/scopeMapping.json").replace("$numberOfShards", numberOfShards)
                    .replace("$numberOfReplicas", numberOfReplicas).replace("$mappingTotalFieldsLimit", mappingTotalFieldsLimit)
                    .replace("$maxDocValueFieldsSearch", maxDocValueFieldsSearch);

            httpPost.setEntity(new StringEntity(request));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    System.out.println(indexPrefix + "-scope has been correctly created");
                } else {
                    System.out.println(
                            "Failed to create the index " + indexPrefix + "-scope.Code:" + response.getStatusLine().getStatusCode());
                    throw new RuntimeException("Can not create the scope index. Stop the execution of the migration.");
                }
            }
        } else {
            System.out.println("The scope index already exists. Skipping the creation of this index");
        }

    }

    private void createScopes(Set<String> scopes, String indexPrefix) throws IOException {
        final StringBuilder body = new StringBuilder();
        String saveScopeBody = MigrationUtils.resourceAsString(bundleContext,"requestBody/bulkSaveScope.ndjson");
        scopes.forEach(scope -> body.append(saveScopeBody.replace("$scope", scope)));

        final HttpPost httpPost = new HttpPost(esAddress + "/" + indexPrefix + "-scope/_bulk");

        httpPost.addHeader("Accept", "application/json");
        httpPost.addHeader("Content-Type", "application/x-ndjson");

        httpPost.setEntity(new StringEntity(body.toString()));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                System.out.println("Creating the \"scopes\" into the index " + indexPrefix + "-scope successfully finished");
            } else {
                System.out.println("Creating the \"scopes\" into the index " + indexPrefix + "-scope has failed" + response.getStatusLine()
                        .getStatusCode());
            }
        }
    }

    private Set<String> getSetOfScopes(Set<String> indices) throws IOException {
        String joinedIndices = String.join(",", indices);
        final HttpPost httpPost = new HttpPost(esAddress + "/" + joinedIndices + "/_search");

        httpPost.addHeader("Accept", "application/json");
        httpPost.addHeader("Content-Type", "application/json");

        String request = MigrationUtils.resourceAsString(bundleContext,"requestBody/searchScope.json");

        httpPost.setEntity(new StringEntity(request));

        Set<String> scopes = new HashSet<>();
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            JSONObject responseAsJson = new JSONObject(EntityUtils.toString(response.getEntity()));
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                System.out.println("Getting the \"scope\" values from the events successfully finished. " + "Number of scope to create: "
                        + responseAsJson.getJSONObject("aggregations").getJSONObject("bucketInfos").get("count").toString());
                scopes = StreamSupport
                        .stream(responseAsJson.getJSONObject("aggregations").getJSONObject("scopes").getJSONArray("buckets").spliterator(),
                                false).map(bucketElement -> ((JSONObject) bucketElement).getString("key")).collect(Collectors.toSet());
            } else {
                System.out.println(
                        "Getting the \"scope\" values from the event has failed. Code: " + response.getStatusLine().getStatusCode());
            }
        }
        return scopes;
    }

    private void createProfileAliasDocumentsFromProfile() throws IOException {
        System.out.println("Migration \"Create profileAlias from profile\" started");
        Instant migrationTime = Instant.now();
        int size = 1000;
        doProcessProfiles(migrationTime, size);
        System.out.println("Migration \"Create profileAlias from profile\" completed.");
    }

    private void doProcessProfiles(Instant migrationTime, int offset) throws IOException {
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(createSearchRequest(offset));

            while (true) {
                JSONObject responseAsJson = getResponseAsJSON(response);

                String scrollId = responseAsJson.has("_scroll_id") ? responseAsJson.getString("_scroll_id"): null;
                JSONArray hits = getProfileHits(responseAsJson);

                if (hits.length() == 0) {
                    if (scrollId != null) {
                        CloseableHttpResponse deleteScrollResponse = httpClient.execute(createDeleteScrollRequest(scrollId));
                        if (deleteScrollResponse != null) {
                            deleteScrollResponse.close();
                        }
                    }
                    break;
                }

                StringBuilder bulkCreateRequest = new StringBuilder();
                for (Object o : hits) {
                    JSONObject hit = (JSONObject) o;
                    if (hit.has("_source")) {
                        JSONObject profile = hit.getJSONObject("_source");
                        if (profile.has("itemId")) {
                            String itemId = profile.getString("itemId");
                            String bulkSaveProfileAliases = MigrationUtils.resourceAsString(bundleContext,"requestBody/bulkSaveProfileAliases.ndjson");
                            bulkCreateRequest.append(bulkSaveProfileAliases.
                                    replace("$itemId", itemId).
                                    replace("$migrationTime", migrationTime.toString()));
                        }
                    }
                }

                CloseableHttpResponse bulkResponse = httpClient.execute(createProfileAliasRequest(bulkCreateRequest.toString()));
                if (bulkResponse != null) {
                    bulkResponse.close();
                }

                response = httpClient.execute(createSearchRequestWithScrollId(scrollId));
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private JSONObject getResponseAsJSON(CloseableHttpResponse response) throws IOException {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            return new JSONObject(EntityUtils.toString(response.getEntity()));
        }
        return new JSONObject();
    }

    private JSONArray getProfileHits(JSONObject responseAsJson) {
        if (responseAsJson.has("hits")) {
            JSONObject hitsObject = responseAsJson.getJSONObject("hits");
            if (hitsObject.has("hits")) {
                return hitsObject.getJSONArray("hits");
            }
        }
        return new JSONArray();
    }

    private HttpPost createSearchRequestWithScrollId(final String scrollId) throws IOException {
        final String requestBody = "{\n" +
                "  \"scroll_id\": \"" + scrollId + "\",\n" +
                "  \"scroll\": \"1h\"\n" +
                "}";

        final HttpPost request = new HttpPost(esAddress + "/_search/scroll");

        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(requestBody));

        return request;
    }

    private HttpGet createSearchRequest(int size) {
        return new HttpGet(esAddress + "/context-profile/_search?&scroll=1h&_source_includes=itemId&size=" + size);
    }

    private HttpEntityEnclosingRequestBase createDeleteScrollRequest(final String scrollId) throws IOException {
        final HttpEntityEnclosingRequestBase deleteRequest = new HttpEntityEnclosingRequestBase() {
            @Override
            public String getMethod() {
                return "DELETE";
            }
        };

        deleteRequest.setURI(URI.create(esAddress + "/_search/scroll"));
        deleteRequest.setEntity(new StringEntity("{ \"scroll_id\": \"" + scrollId + "\" }"));
        deleteRequest.addHeader("Accept", "application/json");
        deleteRequest.addHeader("Content-Type", "application/json");

        return deleteRequest;
    }

    private HttpPost createProfileAliasRequest(String bulkRequestAsString) throws IOException {
        final HttpPost bulkRequest = new HttpPost(esAddress + "/context-profilealias/_bulk");

        bulkRequest.addHeader("Accept", "application/json");
        bulkRequest.addHeader("Content-Type", "application/json");
        bulkRequest.setEntity(new StringEntity(bulkRequestAsString));

        return bulkRequest;
    }
}
