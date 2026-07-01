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
import org.apache.unomi.itests.tools.LogChecker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

/**
 * Integration tests for REST create endpoints returning HTTP 400 on invalid payloads
 * (UNOMI-934 rules, UNOMI-935 segments).
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
                .addIgnoredSubstring("RuntimeExceptionMapper")
                .build();
    }

    @Test
    public void testCreateRuleWithInvalidCondition_returnsBadRequest() throws Exception {
        doPostJsonTest(RULES_URL, "/validation/rule_invalidCondition.json", 400);
    }

    @Test
    public void testCreateRuleWithMalformedJson_returnsBadRequest() throws Exception {
        doPostRawBodyTest(RULES_URL, "foo", 400);
    }

    @Test
    public void testCreateSegmentWithInvalidCondition_returnsBadRequest() throws Exception {
        doPostJsonTest(SEGMENTS_URL, "/validation/segment_invalidCondition.json", 400);
    }

    @Test
    public void testCreateSegmentWithMalformedJson_returnsBadRequest() throws Exception {
        doPostRawBodyTest(SEGMENTS_URL, "foo", 400);
    }

    private void doPostJsonTest(String uri, String entityResourcePath, int expectedStatusCode) throws Exception {
        doPostRawBodyTest(uri, getValidatedBundleJSON(entityResourcePath, new HashMap<>()), expectedStatusCode);
    }

    private void doPostRawBodyTest(String uri, String body, int expectedStatusCode) throws Exception {
        performRestPostTest(getFullUrl(uri), body, expectedStatusCode);
    }

    private void performRestPostTest(String url, String body, int expectedStatusCode) throws IOException {
        HttpPost request = new HttpPost(url);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = executeHttpRequest(request, AuthType.AUTO)) {
            assertEquals("Unexpected HTTP status for POST " + url, expectedStatusCode, response.getStatusLine().getStatusCode());
            if (expectedStatusCode == 400) {
                String responseBody = EntityUtils.toString(response.getEntity());
                JsonNode json = objectMapper.readTree(responseBody);
                assertEquals("badRequest", json.get("errorMessage").asText());
            }
        }
    }
}
