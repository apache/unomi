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

import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author dgaillard
 */
public class MigrationUtils {
    private static final Logger logger = LoggerFactory.getLogger(MigrationUtils.class);

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
}
