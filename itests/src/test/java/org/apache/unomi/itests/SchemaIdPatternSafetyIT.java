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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

/**
 * Identifiers arriving on the public tracking endpoints are matched against a shipped validation
 * pattern before anything else happens, without administrator credentials. If that match were
 * unbounded, one small request would occupy a request thread indefinitely.
 * <p>
 * These tests assert the property that matters operationally — the request <em>returns</em> — rather
 * than any particular status code. The inputs used are ones that took exponential time before the fix
 * (seconds at 26 characters, and far beyond that at the lengths used here), so a regression does not
 * produce a wrong answer, it produces a request that never comes back. Each is therefore bounded by a
 * timeout: if the boundary regresses, the test fails by timing out rather than by assertion.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SchemaIdPatternSafetyIT extends BaseIT {

    private final static String CONTEXT_URL = "/cxs/context.json";

    private final static String UNOMI_API_KEY_HTTP_HEADER_KEY = "X-Unomi-Api-Key";

    /** Generous next to the sub-millisecond a bounded evaluation needs, decisive against an unbounded one. */
    private final static int RESPONSE_TIMEOUT_MS = 30_000;

    /**
     * An identifier built to defeat the shipped id-validation pattern: a run of characters that the
     * pattern's alternatives both accept, followed by one it rejects. Before the fix this forced the
     * matcher to explore every division of the run.
     */
    @Test(timeout = RESPONSE_TIMEOUT_MS)
    public void testMaliciousProfileIdIsValidatedInBoundedTime() throws Exception {
        final StringBuilder maliciousId = new StringBuilder();
        for (int i = 0; i < 55; i++) {
            maliciousId.append('_');
        }
        maliciousId.append('!');

        final String payload = "{\"profileId\":\"" + maliciousId + "\",\"sessionId\":\"regex-safety-session\","
                + "\"requiredProfileProperties\":[\"*\"],\"events\":[]}";

        assertReturnsPromptly(payload);
    }

    /** The same shape supplied as a sessionId, which is validated by the same pattern. */
    @Test(timeout = RESPONSE_TIMEOUT_MS)
    public void testMaliciousSessionIdIsValidatedInBoundedTime() throws Exception {
        final StringBuilder maliciousId = new StringBuilder();
        for (int i = 0; i < 55; i++) {
            maliciousId.append('_');
        }
        maliciousId.append('!');

        final String payload = "{\"sessionId\":\"" + maliciousId + "\",\"requiredProfileProperties\":[\"*\"],"
                + "\"events\":[]}";

        assertReturnsPromptly(payload);
    }

    /**
     * Sends the payload to the public context endpoint and requires an answer. Any HTTP status is
     * acceptable — rejecting the input and processing it are both fine outcomes. What is not acceptable
     * is the request not completing, which is exactly what an unbounded evaluation causes.
     */
    private void assertReturnsPromptly(final String payload) throws Exception {
        final HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKeyValue);
        request.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));

        final long startedAt = System.currentTimeMillis();
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            final long elapsed = System.currentTimeMillis() - startedAt;
            Assert.assertNotNull("The public endpoint must answer rather than hang", response);
            Assert.assertTrue("The public endpoint answered, but took " + elapsed
                    + "ms, which suggests the evaluation is no longer bounded", elapsed < RESPONSE_TIMEOUT_MS);
        }
    }
}
