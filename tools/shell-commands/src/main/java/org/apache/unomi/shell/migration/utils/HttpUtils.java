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

import org.apache.http.HttpEntity;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * @author dgaillard
 */
public class HttpUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtils.class);

    public static CloseableHttpClient initHttpClient(boolean trustAllCertificates, CredentialsProvider credentialsProvider) throws IOException {
        long requestStartTime = System.currentTimeMillis();

        HttpClientBuilder httpClientBuilder = HttpClients.custom().useSystemProperties();
        if (credentialsProvider != null) {
            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }
        if (trustAllCertificates) {
            try {
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs,
                                                   String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs,
                                                   String authType) {
                    }
                }}, new SecureRandom());

                Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER))
                        .build();

                httpClientBuilder.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
                        .setConnectionManager(new PoolingHttpClientConnectionManager(socketFactoryRegistry));

            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                LOGGER.error("Error creating SSL Context", e);
            }
        } else {
            httpClientBuilder.setConnectionManager(new PoolingHttpClientConnectionManager());
        }

        RequestConfig requestConfig = RequestConfig.custom().build();
        httpClientBuilder.setDefaultRequestConfig(requestConfig);

        LOGGER.debug("Init HttpClient executed in {}ms", (System.currentTimeMillis() - requestStartTime));

        return httpClientBuilder.build();
    }

    public static String executeGetRequest(CloseableHttpClient httpClient, String url, Map<String, String> headers) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("accept", "application/json");

        return getResponse(httpClient, url, headers, httpGet);
    }

    public static String executeHeadRequest(CloseableHttpClient httpClient, String url, Map<String, String> headers) throws IOException {
        HttpHead httpHead = new HttpHead(url);
        httpHead.addHeader("accept", "application/json");

        return getResponse(httpClient, url, headers, httpHead);
    }

    public static String executeDeleteRequest(CloseableHttpClient httpClient, String url, Map<String, String> headers) throws IOException {
        HttpDelete httpDelete = new HttpDelete(url);
        httpDelete.addHeader("accept", "application/json");

        return getResponse(httpClient, url, headers, httpDelete);
    }

    public static String executePostRequest(CloseableHttpClient httpClient, String url, String jsonData, Map<String, String> headers) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("accept", "application/json");

        if (jsonData != null) {
            StringEntity input = new StringEntity(jsonData);
            input.setContentType("application/json");
            httpPost.setEntity(input);
        }

        return getResponse(httpClient, url, headers, httpPost);
    }

    public static String executePutRequest(CloseableHttpClient httpClient, String url, String jsonData, Map<String, String> headers) throws IOException {
        HttpPut httpPut = new HttpPut(url);
        httpPut.addHeader("accept", "application/json");

        if (jsonData != null) {
            StringEntity input = new StringEntity(jsonData);
            input.setContentType("application/json");
            httpPut.setEntity(input);
        }

        return getResponse(httpClient, url, headers, httpPut);
    }

    private static String getResponse(CloseableHttpClient httpClient, String url, Map<String, String> headers, HttpRequestBase httpRequestBase) throws IOException {
        long requestStartTime = System.currentTimeMillis();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpRequestBase.setHeader(entry.getKey(), entry.getValue());
            }
        }

        CloseableHttpResponse response = httpClient.execute(httpRequestBase);
        final int statusCode = response.getStatusLine().getStatusCode();
        HttpEntity entity = response.getEntity();
        if (statusCode >= 400) {
            throw new HttpRequestException("Couldn't execute " + httpRequestBase + " response: " + ((entity != null) ? EntityUtils.toString(entity) : "n/a"), statusCode);
        }

        LOGGER.debug("Request {} executed with code: {} and message: {}", httpRequestBase, statusCode, (entity!=null?EntityUtils.toString(new BufferedHttpEntity(response.getEntity())):null));
        LOGGER.debug("Request to Apache Unomi url: {} executed in {}ms", url, (System.currentTimeMillis() - requestStartTime));

        if (entity == null) {
            return null;
        }

        String stringResponse = EntityUtils.toString(entity);
        EntityUtils.consumeQuietly(entity);
        return stringResponse;
    }
}
