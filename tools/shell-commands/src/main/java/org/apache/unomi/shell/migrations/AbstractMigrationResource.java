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
package org.apache.unomi.shell.migrations;

import org.apache.felix.service.command.CommandSession;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.shell.utils.ConsoleUtils;
import org.apache.unomi.shell.utils.HttpUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * @author dgaillard
 */
public abstract class AbstractMigrationResource {
    protected CloseableHttpClient httpClient;
    protected CommandSession session;

    protected AbstractMigrationResource(CommandSession session) throws IOException {
        this.session = session;
    }

    protected void initHttpClient() throws IOException {
        if (httpClient == null) {
            String confirmation = ConsoleUtils.askUserWithAuthorizedAnswer(session,"We need to initialize a HttpClient, do we need to trust all certificates? (yes/no):", Arrays.asList("yes", "no"));
            httpClient = HttpUtils.initHttpClient(confirmation.equalsIgnoreCase("yes"));
        }
    }

    protected void closeHttpClient() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    protected List<JSONArray> getDataToMigrate(String url, int offset, int size) throws IOException {
        String jsonData = "{\"query\":{\"bool\":{\"should\":[{\"match_all\":{}}]}},\"from\":" + offset + ",\"size\":" + size + "}";

        HttpEntity entity = HttpUtils.executePostRequest(httpClient, url, jsonData, null);
        JSONObject responseJSON = new JSONObject(EntityUtils.toString(entity));
        EntityUtils.consumeQuietly(entity);

        List<JSONArray> totalHits = new ArrayList<>();
        if (responseJSON.has("hits")) {
            JSONObject hitsObject = responseJSON.getJSONObject("hits");
            JSONArray hits = hitsObject.getJSONArray("hits");
            totalHits.add(hits);
            int newOffset = size + offset;
            if (newOffset <= hitsObject.getInt("total")) {
                totalHits.addAll(getDataToMigrate(url, newOffset, size));
            }
        }

        return totalHits;
    }

    protected void bulkUpdate(String url, String jsonData) throws IOException {
        HttpEntity entity = HttpUtils.executePostRequest(httpClient, url, jsonData, null);
        JSONObject responseJSON = new JSONObject(EntityUtils.toString(entity));
        EntityUtils.consumeQuietly(entity);
    }
}
