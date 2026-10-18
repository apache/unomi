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
package org.apache.unomi.itests;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Drives the public tracker endpoints through the real filter chain (CXF CORS filter, authentication,
 * {@code PublicTrackerCorsFilter}). {@link BaseIT} lists {@link #LISTED_ORIGIN} in
 * {@code org.apache.unomi.public.cors.allowedOrigins}.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class PublicTrackerCorsIT extends BaseIT {

    static final String LISTED_ORIGIN = "https://allowed-site.example";
    private static final String UNLISTED_ORIGIN = "https://other-site.example";
    private static final String CONTEXT_JSON = "/cxs/context.json";
    private static final String SESSION_ID = "public-tracker-cors-it-session";

    @Test
    public void preflightFromListedOriginAllowsCredentials() throws Exception {
        try (CloseableHttpResponse response = execute(preflight(CONTEXT_JSON, LISTED_ORIGIN), false)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals(LISTED_ORIGIN, header(response, "Access-Control-Allow-Origin"));
            assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
            assertTrue(header(response, "Vary").contains("Origin"));
            String allowedHeaders = header(response, "Access-Control-Allow-Headers").toLowerCase();
            assertTrue(allowedHeaders, allowedHeaders.contains("x-unomi-api-key"));
            assertTrue(allowedHeaders, allowedHeaders.contains("x-unomi-tenant-id"));
        }
    }

    @Test
    public void preflightFromUnlistedOriginAllowsNoCredentials() throws Exception {
        try (CloseableHttpResponse response = execute(preflight(CONTEXT_JSON, UNLISTED_ORIGIN), false)) {
            assertEquals("*", header(response, "Access-Control-Allow-Origin"));
            assertNull(header(response, "Access-Control-Allow-Credentials"));
        }
    }

    @Test
    public void listedOriginKeepsTheVisitorAndGetsCredentials() throws Exception {
        String[] visitor = newVisitor();
        HttpPost request = contextRequest();
        request.setHeader("Origin", LISTED_ORIGIN);
        request.setHeader("Sec-Fetch-Site", "cross-site");
        request.setHeader("Cookie", visitor[1]);
        try (CloseableHttpResponse response = execute(request, true)) {
            assertEquals(LISTED_ORIGIN, header(response, "Access-Control-Allow-Origin"));
            assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
            assertEquals(visitor[0], profileId(response));
        }
    }

    @Test
    public void unlistedCrossSiteOriginDoesNotUseTheVisitorCookie() throws Exception {
        String[] visitor = newVisitor();
        HttpPost request = contextRequest();
        request.setHeader("Origin", UNLISTED_ORIGIN);
        request.setHeader("Sec-Fetch-Site", "cross-site");
        request.setHeader("Cookie", visitor[1]);
        try (CloseableHttpResponse response = execute(request, true)) {
            assertEquals("*", header(response, "Access-Control-Allow-Origin"));
            assertNull(header(response, "Access-Control-Allow-Credentials"));
            assertNotEquals(visitor[0], profileId(response));
        }
    }

    @Test
    public void sameOriginPostKeepsTheVisitor() throws Exception {
        // Browsers send Origin on same-origin POSTs, for example behind a reverse proxy serving /cxs.
        String[] visitor = newVisitor();
        HttpPost request = contextRequest();
        request.setHeader("Origin", BASE_URL + ":" + getHttpPort());
        request.setHeader("Sec-Fetch-Site", "same-origin");
        request.setHeader("Cookie", visitor[1]);
        try (CloseableHttpResponse response = execute(request, true)) {
            assertEquals(visitor[0], profileId(response));
        }
    }

    @Test
    public void contextJsFromListedOriginGetsCredentials() throws Exception {
        HttpGet request = new HttpGet(getFullUrl("/cxs/context.js?sessionId=" + SESSION_ID));
        request.setHeader("Origin", LISTED_ORIGIN);
        try (CloseableHttpResponse response = execute(request, true)) {
            assertEquals(LISTED_ORIGIN, header(response, "Access-Control-Allow-Origin"));
            assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
        }
    }

    /** Returns the profile id and the cookie of a visitor created without any Origin header. */
    private String[] newVisitor() throws Exception {
        try (CloseableHttpResponse response = execute(contextRequest(), true)) {
            Header setCookie = response.getFirstHeader("Set-Cookie");
            assertNotNull("A new visitor gets a profile cookie", setCookie);
            return new String[]{profileId(response), setCookie.getValue().split(";")[0]};
        }
    }

    private HttpPost contextRequest() throws Exception {
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_JSON));
        request.setEntity(new StringEntity("{\"sessionId\":\"" + SESSION_ID + "\"}", ContentType.APPLICATION_JSON));
        return request;
    }

    private HttpOptions preflight(String path, String origin) throws Exception {
        HttpOptions request = new HttpOptions(getFullUrl(path));
        request.setHeader("Origin", origin);
        request.setHeader("Access-Control-Request-Method", "POST");
        request.setHeader("Access-Control-Request-Headers", "content-type,x-unomi-api-key,x-unomi-tenant-id");
        return request;
    }

    private static CloseableHttpResponse execute(HttpUriRequest request, boolean withPublicKey) throws IOException {
        return HttpClientThatWaitsForUnomi.doRequest(request, -1, withPublicKey, false);
    }

    private String profileId(CloseableHttpResponse response) throws IOException {
        assertEquals(200, response.getStatusLine().getStatusCode());
        return getObjectMapper().readTree(EntityUtils.toString(response.getEntity())).get("profileId").asText();
    }

    private static String header(CloseableHttpResponse response, String name) {
        Header header = response.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }
}
