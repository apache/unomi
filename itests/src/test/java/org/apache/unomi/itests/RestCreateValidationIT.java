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

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.itests.tools.LogChecker;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Integration tests for REST create endpoints returning HTTP 400 on invalid payloads
 * (UNOMI-934 rules, UNOMI-935 segments). Verifies both the HTTP status code and the
 * JSON response contract: {@code {"errorMessage":"badRequest"}}.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class RestCreateValidationIT extends BaseIT {

    private static final String RULES_URL = "/cxs/rules";
    private static final String SEGMENTS_URL = "/cxs/segments";

    @Override
    protected LogChecker createLogChecker() {
        return LogChecker.builder()
                .addIgnoredSubstring("Response status code: 400")
                .addIgnoredSubstring("Invalid rule condition")
                .addIgnoredSubstring("BadSegmentConditionException")
                .addIgnoredSubstring("IllegalArgumentExceptionMapper")
                .addIgnoredSubstring("BadSegmentConditionExceptionMapper")
                .addIgnoredSubstring("JsonMappingExceptionMapper")
                // RuntimeExceptionMapper logs "Bad request on ..." at WARN for client errors
                .addIgnoredSubstring("Bad request on")
                .build();
    }

    @After
    public void tearDown() throws InterruptedException {
        removeItems(Rule.class, Segment.class);
    }

    @Test
    public void testCreateValidRule_returns204() throws Exception {
        doPostRawBodyTest(RULES_URL, getValidatedBundleJSON("/validation/rule_valid.json", new HashMap<>()), 204);
    }

    @Test
    public void testCreateValidSegment_returns204() throws Exception {
        doPostRawBodyTest(SEGMENTS_URL, getValidatedBundleJSON("/validation/segment_valid.json", new HashMap<>()), 204);
    }

    @Test
    public void testCreateRuleWithInvalidCondition_returnsBadRequest() throws Exception {
        doPostRawBodyTest(RULES_URL, getValidatedBundleJSON("/validation/rule_invalidCondition.json", new HashMap<>()), 400);
    }

    @Test
    public void testCreateRuleWithMalformedJson_returnsBadRequest() throws Exception {
        doPostRawBodyTest(RULES_URL, "foo", 400);
    }

    @Test
    public void testCreateSegmentWithInvalidCondition_returnsBadRequest() throws Exception {
        doPostRawBodyTest(SEGMENTS_URL, getValidatedBundleJSON("/validation/segment_invalidCondition.json", new HashMap<>()), 400);
    }

    @Test
    public void testCreateSegmentWithMalformedJson_returnsBadRequest() throws Exception {
        doPostRawBodyTest(SEGMENTS_URL, "foo", 400);
    }

    private void doPostRawBodyTest(String uri, String body, int expectedStatusCode) throws Exception {
        performRestPostTest(getFullUrl(uri), body, expectedStatusCode);
    }

    private void performRestPostTest(String url, String body, int expectedStatusCode) throws IOException {
        HttpPost request = new HttpPost(url);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        CloseableHttpResponse response;
        try {
            response = HttpClientThatWaitsForUnomi.doRequest(request, expectedStatusCode);
        } catch (Exception e) {
            fail("Request to " + url + " failed unexpectedly: " + e.getMessage());
            return;
        }
        assertEquals("Unexpected HTTP status for POST " + url, expectedStatusCode, response.getStatusLine().getStatusCode());
        if (expectedStatusCode == 400) {
            assertBadRequestResponse(url, EntityUtils.toString(response.getEntity()));
        }
    }

    private void assertBadRequestResponse(String url, String responseBody) throws IOException {
        assertNotNull("Expected a response body for 400 on POST " + url, responseBody);
        JsonNode json;
        try {
            json = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            fail("Expected JSON in 400 response for POST " + url + " but got: " + responseBody);
            return;
        }
        JsonNode errorNode = json.get("errorMessage");
        assertNotNull("Response JSON missing 'errorMessage' field for POST " + url + ". Body: " + responseBody, errorNode);
        assertEquals("badRequest", errorNode.asText());
    }
}
