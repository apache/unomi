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
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.Migration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.service.component.annotations.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MigrationTo200 implements Migration {

    private CloseableHttpClient httpClient;
    private Session session;
    private String esAddress;

    @Override
    public Version getFromVersion() {
        return new Version("1.5.0");
    }

    @Override
    public Version getToVersion() {
        return new Version("2.0.0");
    }

    @Override
    public String getDescription() {
        return "Updates mapping for an index with prefix \"context-event\". Adds the \"sourceId\" field and copies value from the \"scope\" field to it.";
    }

    @Override
    public void execute(Session session, CloseableHttpClient httpClient, String esAddress, BundleContext bundleContext) throws IOException {
        this.httpClient = httpClient;
        this.session = session;
        this.esAddress = esAddress;

        doMigrate_Copy_Scope_to_SourceId();
        doMigrate_Create_ProfileAlias_From_Profile();
    }

    private void doMigrate_Copy_Scope_to_SourceId() throws IOException {
        try (CloseableHttpResponse response = httpClient.execute(new HttpGet(esAddress + "/_aliases"))) {

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                JSONObject indicesAsJson = new JSONObject(EntityUtils.toString(response.getEntity()));

                final Set<String> indices = indicesAsJson.keySet().stream().
                        filter(alias -> alias.startsWith("context-event")).
                        collect(Collectors.toSet());

                for (String indexName : indices) {
                    updateMapping(indexName);
                }
            }
        }
    }

    private void updateMapping(final String indexName) throws IOException {
        HttpPut httpPut = new HttpPut(esAddress + "/" + indexName + "/_mapping");

        httpPut.addHeader("Accept", "application/json");
        httpPut.addHeader("Content-Type", "application/json");

        String request = "{\n" +
                "\"properties\": {\n" +
                " \"sourceId\": {\n" +
                "  \"analyzer\": \"folding\",\n" +
                "  \"type\": \"text\",\n" +
                "  \"fields\": {\n" +
                "   \"keyword\": {\n" +
                "    \"type\": \"keyword\",\n" +
                "    \"ignore_above\": 256\n" +
                "    }\n" +
                "   }\n" +
                "  }\n" +
                " }\n" +
                "}";

        httpPut.setEntity(new StringEntity(request));

        try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
            JSONObject responseAsJson = new JSONObject(EntityUtils.toString(response.getEntity()));

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK
                    && responseAsJson.has("acknowledged") && responseAsJson.getBoolean("acknowledged")) {
                System.out.println("Mapping for index = \"" + indexName + "\" successfully updated.");

                copyValueScopeToSourceId(indexName, httpClient);
            } else {
                System.out.println("Update the mapping for index = \"" + indexName + "\" failed.");
            }
        }
    }

    private void copyValueScopeToSourceId(final String indexName, final CloseableHttpClient httpClient) throws IOException {
        final HttpPost httpPost = new HttpPost(esAddress + "/" + indexName + "/_update_by_query");

        httpPost.addHeader("Accept", "application/json");
        httpPost.addHeader("Content-Type", "application/json");

        String request = "{\n" +
                "  \"script\": {\n" +
                "    \"source\": \"ctx._source.sourceId = ctx._source.scope\",\n" +
                "    \"lang\": \"painless\"\n" +
                "  }\n" +
                "}";

        httpPost.setEntity(new StringEntity(request));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            JSONObject responseAsJson = new JSONObject(EntityUtils.toString(response.getEntity()));

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                System.out.println("Copying the \"scope\" field to the \"sourceId\" field for index = \"" + indexName + "\" successfully completed. Total: " +
                        responseAsJson.get("total") + ", updated: " + responseAsJson.get("updated") + ".");
            } else {
                System.out.println("Copying the \"scope\" field to the \"sourceId\" field for index = \"" + indexName + "\" failed.");
            }
        }
    }

    private void doMigrate_Create_ProfileAlias_From_Profile() throws IOException {
        System.out.println("Migration \"Create profileAlias from profile\" started");
        Instant migrationTime = Instant.now();
        int initialOffset = 1000;
        int size = 1000;
        doProcessResponse(migrationTime, initialOffset, size, null);
        System.out.println("Migration \"Create profileAlias from profile\" completed.");
    }

    private void doProcessResponse(Instant migrationTime, int initialOffset, int offset, String scrollId) throws IOException {
        HttpUriRequest request = scrollId == null
                ? createSearchRequest(offset)
                : createSearchRequestWithScrollId(scrollId);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                JSONObject responseAsJson = new JSONObject(EntityUtils.toString(response.getEntity()));

                if (responseAsJson.has("hits")) {
                    JSONObject hitsObject = responseAsJson.getJSONObject("hits");
                    if (hitsObject.has("hits")) {
                        StringBuilder bulkCreateRequest = new StringBuilder();
                        JSONArray hits = hitsObject.getJSONArray("hits");
                        for (Object o : hits) {
                            JSONObject hit = (JSONObject) o;
                            if (hit.has("_source")) {
                                JSONObject hitSource = hit.getJSONObject("_source");
                                if (hitSource.has("itemId")) {
                                    String itemId = hitSource.getString("itemId");
                                    bulkCreateRequest.append("{\"create\":{\"_id\":\"").append(itemId).append("\"}}\n").
                                            append("{\"itemId\": \"").append(itemId).append("\", ").
                                            append("\"itemType\": \"profileAlias\", ").
                                            append("\"profileID\": \"").append(itemId).append("\", ").
                                            append("\"scope\": null, ").
                                            append("\"clientID\": \"defaultClientId\", ").
                                            append("\"creationTime\": \"").append(migrationTime.toString()).append("\", ").
                                            append("\"modifiedTime\": \"").append(migrationTime.toString()).append("\" ").
                                            append("}\n");
                                }
                            }
                        }

                        CloseableHttpResponse bulkResponse = httpClient.execute(createProfileAliasRequest(bulkCreateRequest.toString()));
                        if (bulkResponse != null) {
                            bulkResponse.close();
                        }
                    }

                    if (hitsObject.getJSONObject("total").getInt("value") > offset) {
                        doProcessResponse(migrationTime, initialOffset, offset + initialOffset, responseAsJson.getString("_scroll_id"));
                    } else {
                        if (scrollId != null) {
                            CloseableHttpResponse deleteScrollResponse = httpClient.execute(createDeleteScrollRequest(scrollId));
                            if (deleteScrollResponse != null) {
                                deleteScrollResponse.close();
                            }
                        }
                    }
                }
            }
        }
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
