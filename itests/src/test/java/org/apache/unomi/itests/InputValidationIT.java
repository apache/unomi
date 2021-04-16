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
 * limitations under the License
 */
package org.apache.unomi.itests;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class InputValidationIT extends BaseIT {
    private final static String EVENT_COLLECTOR_URL = "/eventcollector";
    private final static String CONTEXT_JS_URL = "/context.js";
    private final static String CONTEXT_JSON_URL = "/context.json";

    @Test
    public void test_param_EventsCollectorRequestNotNull() throws IOException {
        doPOSTRequestTest(URL + EVENT_COLLECTOR_URL, null, null, 400);
        doGETRequestTest(URL + EVENT_COLLECTOR_URL, null, null, 400);
    }

    @Test
    public void test_param_EventsNotEmpty() throws IOException {
        doPOSTRequestTest(URL + EVENT_COLLECTOR_URL, null, "/validation/eventcollector_emptyEvents.json", 400);
        doGETRequestTest(URL + EVENT_COLLECTOR_URL, null, "/validation/eventcollector_emptyEvents.json", 400);
    }

    @Test
    public void test_param_SessionIDPattern() throws IOException {
        doPOSTRequestTest(URL + EVENT_COLLECTOR_URL, null, "/validation/eventcollector_invalidSessionId.json", 400);
        doGETRequestTest(URL + EVENT_COLLECTOR_URL, null, "/validation/eventcollector_invalidSessionId.json", 400);
    }

    @Test
    public void test_eventCollector_valid() throws IOException {
        doPOSTRequestTest(URL + EVENT_COLLECTOR_URL, null, "/validation/eventcollector_valid.json", 200);
        doGETRequestTest(URL + EVENT_COLLECTOR_URL, null, "/validation/eventcollector_valid.json", 200);
    }

    @Test
    public void test_contextRequest_SessionIDPattern() throws IOException {
        doPOSTRequestTest(URL + CONTEXT_JSON_URL, null, "/validation/contextRequest_invalidSessionId.json", 400);
        doPOSTRequestTest(URL + CONTEXT_JS_URL, null, "/validation/contextRequest_invalidSessionId.json", 400);
        doGETRequestTest(URL + CONTEXT_JSON_URL, null, "/validation/contextRequest_invalidSessionId.json", 400);
        doGETRequestTest(URL + CONTEXT_JS_URL, null, "/validation/contextRequest_invalidSessionId.json", 400);
    }

    @Test
    public void test_contextRequest_ProfileIDPattern() throws IOException {
        doPOSTRequestTest(URL + CONTEXT_JSON_URL, null, "/validation/contextRequest_invalidProfileId.json", 400);
        doPOSTRequestTest(URL + CONTEXT_JS_URL, null, "/validation/contextRequest_invalidProfileId.json", 400);
        doGETRequestTest(URL + CONTEXT_JSON_URL, null, "/validation/contextRequest_invalidProfileId.json", 400);
        doGETRequestTest(URL + CONTEXT_JS_URL, null, "/validation/contextRequest_invalidProfileId.json", 400);
    }

    @Test
    public void test_contextRequest_valid() throws IOException {
        doPOSTRequestTest(URL + CONTEXT_JSON_URL, null, "/validation/contextRequest_valid.json", 200);
        doPOSTRequestTest(URL + CONTEXT_JS_URL, null, "/validation/contextRequest_valid.json", 200);
        doGETRequestTest(URL + CONTEXT_JSON_URL, null, "/validation/contextRequest_valid.json", 200);
        doGETRequestTest(URL + CONTEXT_JS_URL, null, "/validation/contextRequest_valid.json", 200);
    }

    @Test
    public void test_contextJSON_SessionIDPattern() throws IOException {
        String baseUrl = URL + CONTEXT_JS_URL;
        String queryString = "?sessionId=" + URLEncoder.encode("<script>alert();</script>", StandardCharsets.UTF_8.toString());
        doPOSTRequestTest(baseUrl + queryString, null, null, 400);
        doGETRequestTest(baseUrl + queryString, null, null, 400);

        baseUrl = URL + CONTEXT_JSON_URL;
        doPOSTRequestTest(baseUrl + queryString, null, null, 400);
        doGETRequestTest(baseUrl + queryString, null, null, 400);

        baseUrl = URL + CONTEXT_JS_URL;
        queryString = "?sessionId=" + URLEncoder.encode("dummy-session-id", StandardCharsets.UTF_8.toString());
        doPOSTRequestTest(baseUrl + queryString, null, null, 200);
        doGETRequestTest(baseUrl + queryString, null, null, 200);

        baseUrl = URL + CONTEXT_JSON_URL;
        doPOSTRequestTest(baseUrl + queryString, null, null, 200);
        doGETRequestTest(baseUrl + queryString, null, null, 200);
    }

    @Test
    public void test_contextJSON_PersonaIdPattern() throws IOException {
        String baseUrl = URL + CONTEXT_JS_URL;
        String queryString = "?personaId=" + URLEncoder.encode("<script>alert();</script>", StandardCharsets.UTF_8.toString());
        doPOSTRequestTest(baseUrl + queryString, null, null, 400);
        doGETRequestTest(baseUrl + queryString, null, null, 400);

        baseUrl = URL + CONTEXT_JSON_URL;
        doPOSTRequestTest(baseUrl + queryString, null, null, 400);
        doGETRequestTest(baseUrl + queryString, null, null, 400);

        baseUrl = URL + CONTEXT_JS_URL;
        queryString = "?personaId=" + URLEncoder.encode("dummy-persona-id", StandardCharsets.UTF_8.toString());
        doPOSTRequestTest(baseUrl + queryString, null, null, 200);
        doGETRequestTest(baseUrl + queryString, null, null, 200);

        baseUrl = URL + CONTEXT_JSON_URL;
        doPOSTRequestTest(baseUrl + queryString, null,null, 200);
        doGETRequestTest(baseUrl + queryString, null, null, 200);
    }

    @Test
    public void test_cookie_profileIdPattern() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", "context-profile-id=<script>alert();</script>");
        doPOSTRequestTest(URL + CONTEXT_JSON_URL, headers, null, 400);
        doPOSTRequestTest(URL + CONTEXT_JS_URL, headers, null, 400);
        doGETRequestTest(URL + CONTEXT_JSON_URL, headers, null, 400);
        doGETRequestTest(URL + CONTEXT_JS_URL, headers, null, 400);

        headers.put("Cookie", "context-profile-id=dummy-profile-id");
        doPOSTRequestTest(URL + CONTEXT_JSON_URL, headers, null, 200);
        doPOSTRequestTest(URL + CONTEXT_JS_URL, headers, null, 200);
        doGETRequestTest(URL + CONTEXT_JSON_URL, headers, null, 200);
        doGETRequestTest(URL + CONTEXT_JS_URL, headers, null, 200);
    }

    private void doGETRequestTest(String url, Map<String, String> headers, String entityResourcePath, int expectedHTTPStatusCode) throws IOException {
        if (entityResourcePath != null) {
            String payload = getValidatedBundleJSON(entityResourcePath, new HashMap<>());
            url += (url.contains("?") ? "&" : "?") + "payload=" + URLEncoder.encode(payload, StandardCharsets.UTF_8.toString());
        }
        performRequest(new HttpGet(url), headers, expectedHTTPStatusCode);
    }

    private void doPOSTRequestTest(String url, Map<String, String> headers, String entityResourcePath, int expectedHTTPStatusCode) throws IOException {
        HttpPost request = new HttpPost(url);
        if (entityResourcePath != null) {
            request.setEntity(new StringEntity(getValidatedBundleJSON(entityResourcePath, new HashMap<>()), ContentType.create("application/json")));
        }
        performRequest(request, headers, expectedHTTPStatusCode);
    }

    private void performRequest(HttpUriRequest request, Map<String, String> headers, int expectedHTTPStatusCode) {
        CloseableHttpResponse response;
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                request.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
        try {
            response = HttpClientThatWaitsForUnomi.doRequest(request, expectedHTTPStatusCode);
        } catch (Exception e) {
            fail("Something went wrong with the request to Unomi that is unexpected: " + e.getMessage());
            return;
        }

        assertEquals("Invalid response code", expectedHTTPStatusCode, response.getStatusLine().getStatusCode());
    }
}
