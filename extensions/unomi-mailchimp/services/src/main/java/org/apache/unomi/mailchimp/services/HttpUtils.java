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
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
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
    public static JsonNode doPostHttp(CloseableHttpClient request, String url, Map<String, String> headers,
                                      JSONObject body) {
        AbstractHttpEntity entity;
        JsonNode jsonNode = null;
        HttpPost httpPost = new HttpPost(url);

        try {
            entity = new ByteArrayEntity(body.toString().getBytes("UTF8"));
            CloseableHttpResponse response = null;
            entity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));

            try {
                httpPost.setEntity(entity);
                try {
                    for (String key : headers.keySet()) {
                        httpPost.addHeader(key, headers.get(key));
                    }
                    response = request.execute(httpPost);
                    HttpEntity entityResponse = response.getEntity();
                    String responseString;
                    try {
                        responseString = EntityUtils.toString(entityResponse);
                        ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            jsonNode = objectMapper.readTree(responseString);
                        } catch (IOException e) {
                            logger.info(e.getMessage(), e);
                        }
                    } catch (IOException e) {
                        logger.info(e.getMessage(), e);
                    }
                } catch (IOException e) {
                    logger.info(e.getMessage(), e);
                }

            } finally {
                try {
                    if (response != null) {
                        response.close();
                    }
                } catch (IOException e) {
                    logger.info(e.getMessage(), e);
                }
            }

        } catch (UnsupportedEncodingException e) {
            logger.info(e.getMessage(), e);
            return null;
        }
        return jsonNode;
    }


    public static JsonNode doGetHttp(CloseableHttpClient request, String
            url, Map<String, String> headers) {
        HttpGet httpGet = new HttpGet(url);

        for (String key : headers.keySet()) {
            httpGet.addHeader(key, headers.get(key));
        }
        JsonNode jsonNode = null;
        CloseableHttpResponse response = null;
        try {
            response = request.execute(httpGet);
            if (response != null) {
                HttpEntity entity = response.getEntity();
                String responseString;
                if (entity != null) {
                    try {
                        responseString = EntityUtils.toString(entity);
                        ObjectMapper objectMapper = new ObjectMapper();
                        jsonNode = objectMapper.readTree(responseString);
                    } catch (IOException e) {
                        logger.info("Error : With the API json response.", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.info("Error : With the Http Request execution. Wrong parameters given", e
                    .getMessage());
        } finally {
            if (response != null) {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }
        return jsonNode;
    }

    public static CloseableHttpClient initHttpClient() {
        return HttpClients.createDefault();
    }
}





