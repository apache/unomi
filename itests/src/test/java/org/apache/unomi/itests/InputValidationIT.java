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
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
import org.junit.*;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class InputValidationIT extends BaseIT {
    private final static String EVENT_COLLECTOR_URL = "/eventcollector";
    private final static String CONTEXT_JS_URL = "/context.js";
    private final static String CONTEXT_JSON_URL = "/context.json";
    private final static String DUMMY_EVENT_TYPE_SCHEMA = "dummy-event-type.json";

    private final static String ERROR_MESSAGE_REQUEST_SIZE_LIMIT_EXCEEDED = "Request rejected by the server because: Request size exceed the limit";
    private final static String ERROR_MESSAGE_INVALID_DATA_RECEIVED = "Request rejected by the server because: Invalid received data";
    public static final String DUMMY_SCOPE = "dummy_scope";

    @Inject
    @Filter(timeout = 600000)
    protected SchemaService schemaService;

    @Inject
    @Filter(timeout = 600000)
    protected ScopeService scopeService;

    @Before
    public void setUp() throws InterruptedException {
        TestUtils.createScope(DUMMY_SCOPE, "Dummy scope", scopeService);
        keepTrying("Scope "+ DUMMY_SCOPE +" not found in the required time", () -> scopeService.getScope(DUMMY_SCOPE),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @After
    public void tearDown() throws InterruptedException {
        removeItems(Scope.class);
    }

    @Test
    public void test_param_EventsCollectorRequestNotNull() throws IOException {
        doPOSTRequestTest(EVENT_COLLECTOR_URL, null, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(EVENT_COLLECTOR_URL, null, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
    }

    @Test
    public void test_param_EventsNotEmpty() throws IOException {
        doPOSTRequestTest(EVENT_COLLECTOR_URL, null, "/validation/eventcollector_emptyEvents.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(EVENT_COLLECTOR_URL, null, "/validation/eventcollector_emptyEvents.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
    }

    @Test
    public void test_param_SessionIDPattern() throws IOException {
        doPOSTRequestTest(EVENT_COLLECTOR_URL, null, "/validation/eventcollector_invalidSessionId.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(EVENT_COLLECTOR_URL, null, "/validation/eventcollector_invalidSessionId.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
    }

    @Test
    public void test_eventCollector_valid() throws IOException, InterruptedException {
        // needed schema for event to be valid during tests
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties.json"));
        keepTrying("Event should be valid",
                () -> schemaService.isEventValid(resourceAsString("schemas/event-dummy-valid.json"), "dummy"),
                isValid -> isValid,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        doPOSTRequestTest(EVENT_COLLECTOR_URL, null, "/validation/eventcollector_valid.json", 200, null);
        doGETRequestTest(EVENT_COLLECTOR_URL, null, "/validation/eventcollector_valid.json", 200, null);

        // remove schemas
        schemaService.deleteSchema("https://vendor.test.com/schemas/json/events/dummy/1-0-0");
        schemaService.deleteSchema("https://vendor.test.com/schemas/json/events/dummy/properties/1-0-0");
        keepTrying("Event should be invalid",
                () -> schemaService.isEventValid(resourceAsString("schemas/event-dummy-valid.json"), "dummy"),
                isValid -> !isValid,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void test_contextRequest_SessionIDPattern() throws IOException {
        doPOSTRequestTest(CONTEXT_JSON_URL, null, "/validation/contextRequest_invalidSessionId.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doPOSTRequestTest(CONTEXT_JS_URL, null, "/validation/contextRequest_invalidSessionId.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(CONTEXT_JSON_URL, null, "/validation/contextRequest_invalidSessionId.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(CONTEXT_JS_URL, null, "/validation/contextRequest_invalidSessionId.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
    }

    @Test
    public void test_contextRequest_ProfileIDPattern() throws IOException {
        doPOSTRequestTest(CONTEXT_JSON_URL, null, "/validation/contextRequest_invalidProfileId.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doPOSTRequestTest(CONTEXT_JS_URL, null, "/validation/contextRequest_invalidProfileId.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(CONTEXT_JSON_URL, null, "/validation/contextRequest_invalidProfileId.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(CONTEXT_JS_URL, null, "/validation/contextRequest_invalidProfileId.json", 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
    }

    @Test
    public void test_contextRequest_valid() throws IOException {
        doPOSTRequestTest(CONTEXT_JSON_URL, null, "/validation/contextRequest_valid.json", 200, null);
        doPOSTRequestTest(CONTEXT_JS_URL, null, "/validation/contextRequest_valid.json", 200, null);
        doGETRequestTest(CONTEXT_JSON_URL, null, "/validation/contextRequest_valid.json", 200, null);
        doGETRequestTest(CONTEXT_JS_URL, null, "/validation/contextRequest_valid.json", 200, null);
    }

    @Test
    public void test_eventCollector_request_size_exceed_limit() throws IOException, InterruptedException {
        // needed schema for event to be valid during tests
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties.json"));
        keepTrying("Event should be valid",
                () -> schemaService.isEventValid(resourceAsString("schemas/event-dummy-valid.json"), "dummy"),
                isValid -> isValid,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        doPOSTRequestTest(EVENT_COLLECTOR_URL, null, "/validation/eventcollector_request_size_invalid.json", 400, ERROR_MESSAGE_REQUEST_SIZE_LIMIT_EXCEEDED);
        doPOSTRequestTest(EVENT_COLLECTOR_URL, null, "/validation/eventcollector_request_size_valid.json", 200, null);

        // remove schemas
        schemaService.deleteSchema("https://vendor.test.com/schemas/json/events/dummy/1-0-0");
        schemaService.deleteSchema("https://vendor.test.com/schemas/json/events/dummy/properties/1-0-0");
        keepTrying("Event should be invalid",
                () -> schemaService.isEventValid(resourceAsString("schemas/event-dummy-valid.json"), "dummy"),
                isValid -> !isValid,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void test_contextJSON_SessionIDPattern() throws IOException {
        String baseUrl = CONTEXT_JS_URL;
        String queryString = "?sessionId=" + URLEncoder.encode("<script>alert();</script>", StandardCharsets.UTF_8.toString());
        doPOSTRequestTest(baseUrl + queryString, null, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(baseUrl + queryString, null, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);

        baseUrl = CONTEXT_JSON_URL;
        doPOSTRequestTest(baseUrl + queryString, null, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(baseUrl + queryString, null, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);

        baseUrl = CONTEXT_JS_URL;
        queryString = "?sessionId=" + URLEncoder.encode("dummy-session-id", StandardCharsets.UTF_8.toString());
        doPOSTRequestTest(baseUrl + queryString, null, null, 200, null);
        doGETRequestTest(baseUrl + queryString, null, null, 200, null);

        baseUrl = CONTEXT_JSON_URL;
        doPOSTRequestTest(baseUrl + queryString, null, null, 200, null);
        doGETRequestTest(baseUrl + queryString, null, null, 200, null);
    }

    @Test
    public void test_contextJSON_PersonaIdPattern() throws IOException {
        String baseUrl = CONTEXT_JS_URL;
        String queryString = "?personaId=" + URLEncoder.encode("<script>alert();</script>", StandardCharsets.UTF_8.toString());
        doPOSTRequestTest(baseUrl + queryString, null, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(baseUrl + queryString, null, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);

        baseUrl = CONTEXT_JSON_URL;
        doPOSTRequestTest(baseUrl + queryString, null, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(baseUrl + queryString, null, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);

        baseUrl = CONTEXT_JS_URL;
        queryString = "?personaId=" + URLEncoder.encode("dummy-persona-id", StandardCharsets.UTF_8.toString());
        doPOSTRequestTest(baseUrl + queryString, null, null, 200, null);
        doGETRequestTest(baseUrl + queryString, null, null, 200, null);

        baseUrl = CONTEXT_JSON_URL;
        doPOSTRequestTest(baseUrl + queryString, null,null, 200, null);
        doGETRequestTest(baseUrl + queryString, null, null, 200, null);
    }

    @Test
    public void test_cookie_profileIdPattern() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", "context-profile-id=<script>alert();</script>");
        doPOSTRequestTest(CONTEXT_JSON_URL, headers, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doPOSTRequestTest(CONTEXT_JS_URL, headers, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(CONTEXT_JSON_URL, headers, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);
        doGETRequestTest(CONTEXT_JS_URL, headers, null, 400, ERROR_MESSAGE_INVALID_DATA_RECEIVED);

        headers.put("Cookie", "context-profile-id=dummy-profile-id");
        doPOSTRequestTest(CONTEXT_JSON_URL, headers, null, 200, null);
        doPOSTRequestTest(CONTEXT_JS_URL, headers, null, 200, null);
        doGETRequestTest(CONTEXT_JSON_URL, headers, null, 200, null);
        doGETRequestTest(CONTEXT_JS_URL, headers, null, 200, null);
    }

    private void doGETRequestTest(String uri, Map<String, String> headers, String entityResourcePath, int expectedHTTPStatusCode, String expectedErrorMessage) throws IOException {
        // test old servlets
        performGETRequestTest(URL + uri, headers, entityResourcePath, expectedHTTPStatusCode, expectedErrorMessage);
        // test directly CXS endpoints
        performGETRequestTest(URL + "/cxs" + uri, headers, entityResourcePath, expectedHTTPStatusCode, expectedErrorMessage);
    }

    private void performGETRequestTest(String url, Map<String, String> headers, String entityResourcePath, int expectedHTTPStatusCode, String expectedErrorMessage) throws IOException {
        if (entityResourcePath != null) {
            String payload = getValidatedBundleJSON(entityResourcePath, new HashMap<>());
            url += (url.contains("?") ? "&" : "?") + "payload=" + URLEncoder.encode(payload, StandardCharsets.UTF_8.toString());
        }
        performRequest(new HttpGet(url), headers, expectedHTTPStatusCode, expectedErrorMessage);
    }

    private void doPOSTRequestTest(String uri, Map<String, String> headers, String entityResourcePath, int expectedHTTPStatusCode, String expectedErrorMessage) throws IOException {
        // test old servlets
        performPOSTRequestTest(URL + uri, headers, entityResourcePath, expectedHTTPStatusCode, expectedErrorMessage);
        // test directly CXS endpoints
        performPOSTRequestTest(URL + "/cxs" + uri, headers, entityResourcePath, expectedHTTPStatusCode, expectedErrorMessage);
    }

    private void performPOSTRequestTest(String url, Map<String, String> headers, String entityResourcePath, int expectedHTTPStatusCode, String expectedErrorMessage) throws IOException {
        HttpPost request = new HttpPost(url);
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put("Content-Type", "application/json");
        if (entityResourcePath != null) {
            request.setEntity(new StringEntity(getValidatedBundleJSON(entityResourcePath, new HashMap<>()), ContentType.create("application/json")));
        }
        performRequest(request, headers, expectedHTTPStatusCode, expectedErrorMessage);
    }

    private void performRequest(HttpUriRequest request, Map<String, String> headers, int expectedHTTPStatusCode, String expectedErrorMessage) throws IOException {
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
        if (expectedErrorMessage != null) {
            String responseMessage = EntityUtils.toString(response.getEntity());
            assertEquals("Invalid response message", expectedErrorMessage, responseMessage);
        }
    }
}
