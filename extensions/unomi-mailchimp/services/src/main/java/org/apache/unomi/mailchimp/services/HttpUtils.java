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
package org.apache.unomi.mailchimp.services;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * Created by dsalhotra on 27/06/2017.
 */
public class HttpUtils {

    private static Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    public static CloseableHttpClient initHttpClient() {
        return HttpClients.createDefault();
    }

    public static JsonNode executePatchRequest(CloseableHttpClient httpClient, String url, Map<String, String> headers, String body) {
        HttpPatch httpPatch = new HttpPatch(url);

        addHeaders(headers, httpPatch);

        return addBodyAndExecuteRequest(httpClient, body, httpPatch);
    }

    public static JsonNode executePostRequest(CloseableHttpClient httpClient, String url, Map<String, String> headers, String body) {
        HttpPost httpPost = new HttpPost(url);

        addHeaders(headers, httpPost);

        return addBodyAndExecuteRequest(httpClient, body, httpPost);
    }

    public static JsonNode executeGetRequest(CloseableHttpClient httpClient, String url, Map<String, String> headers, boolean allow404Response) {
        HttpGet httpGet = new HttpGet(url);

        addHeaders(headers, httpGet);

        return executeRequest(httpClient, httpGet, allow404Response);
    }

    public static JsonNode executeDeleteRequest(CloseableHttpClient httpClient, String url, Map<String, String> headers) {
        HttpDelete httpDelete = new HttpDelete(url);

        addHeaders(headers, httpDelete);

        return executeRequest(httpClient, httpDelete, false);
    }

    private static JsonNode addBodyAndExecuteRequest(CloseableHttpClient httpClient, String body, HttpEntityEnclosingRequestBase request) {
        try {
            StringEntity stringEntity = new StringEntity(body);
            stringEntity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
            request.setEntity(stringEntity);

            return executeRequest(httpClient, request, false);
        } catch (UnsupportedEncodingException e) {
            logger.error("Error when executing request", e);
            return null;
        }
    }

    private static JsonNode executeRequest(CloseableHttpClient httpClient, HttpRequestBase request, boolean allow404Response) {
        try {
            CloseableHttpResponse response = httpClient.execute(request);

            return extractResponse(response, allow404Response);
        } catch (IOException e) {
            logger.error("Error when executing request", e);
            return null;
        }
    }

    private static JsonNode extractResponse(CloseableHttpResponse response, boolean allow404Response) {
        if (response != null) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (((statusCode >= 400) && !allow404Response) || (allow404Response && (statusCode  >= 400 && statusCode != 404 ))) {
                EntityUtils.consumeQuietly(response.getEntity());
                logger.error("Error when communicating with MailChimp server, response code was {} and response message was {}", statusCode, response.getStatusLine().getReasonPhrase());
                return null;
            }

            String responseString;
            if (statusCode == 204) {
                // The response is build for the delete member, because the Api return no content
                responseString = ("{ \"response\": \" " + response.getStatusLine().toString() + "\" }");
            } else {
                // Other request
                try {
                    responseString = EntityUtils.toString(response.getEntity());
                } catch (IOException e) {
                    EntityUtils.consumeQuietly(response.getEntity());
                    logger.error("Error when parsing entity response", e);
                    return null;
                }
            }

            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode jsonNode = objectMapper.readTree(responseString);

                EntityUtils.consumeQuietly(response.getEntity());

                return jsonNode;
            } catch (IOException e) {
                EntityUtils.consumeQuietly(response.getEntity());
                logger.error("Error when parsing response with ObjectMapper", e);
                return null;
            }
        }
        return null;
    }

    private static void addHeaders(Map<String, String> headers, HttpRequestBase httpRequestBase) {
        for (String key : headers.keySet()) {
            httpRequestBase.addHeader(key, headers.get(key));
        }
    }
}





