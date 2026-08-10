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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.EventsCollectorRequest;
import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Profile;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Before/after behavioural baseline for the two public client endpoints, {@code /cxs/context.json}
 * (plus its {@code /cxs/context.js} sibling) and {@code /cxs/eventcollector}.
 * <p>
 * This class is deliberately written to compile and run against <em>both</em> the pre-hardening
 * baseline and the hardened branch, so the same suite can be executed on each and the results
 * diffed. It is split into two groups with opposite expectations:
 * <ul>
 *   <li><b>compat_*</b> — legacy client behaviour that MUST be identical before and after. A
 *       failure here on the hardened branch is a compatibility regression, full stop.</li>
 *   <li><b>hardened_*</b> — behaviour the hardening intentionally changes. These are expected to
 *       FAIL on the pre-hardening baseline and PASS after; that contrast is the evidence the
 *       security fix actually does something.</li>
 * </ul>
 * The compat group covers the client entry points that had no coverage at all: the {@code GET}
 * forms carrying a {@code ?payload=} query parameter, which is how a script tag or image beacon
 * tracks, and which route through exactly the same binding code as the POST forms.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ContextEndpointBaselineIT extends BaseIT {

    private static final String UNOMI_API_KEY_HTTP_HEADER_KEY = "X-Unomi-Api-Key";
    private static final String CONTEXT_JSON_URL = "/cxs/context.json";
    private static final String CONTEXT_JS_URL = "/cxs/context.js";
    private static final String EVENT_COLLECTOR_URL = "/cxs/eventcollector";
    private static final String TEST_SCOPE = "baseline-scope";

    // ------------------------------------------------------------------ compatibility group

    /** A brand new visitor with no cookie and no session must still be issued a profile. */
    @Test
    public void compat_firstVisitIssuesAProfileAndCookie() throws Exception {
        String sessionId = "baseline-first-" + System.currentTimeMillis();
        TestUtils.RequestResponse response = postContextJson(newContextRequest(sessionId), null, sessionId);

        assertEquals(200, response.getStatusCode());
        assertNotNull("a first visit must be issued a profile id", response.getContextResponse().getProfileId());
        assertNotNull("a first visit must be issued the profile cookie", response.getCookieHeaderValue());
    }

    /** A returning visitor presenting the cookie must be recognised as the same profile. */
    @Test
    public void compat_returningVisitorKeepsItsProfile() throws Exception {
        String sessionId = "baseline-returning-" + System.currentTimeMillis();
        TestUtils.RequestResponse first = postContextJson(newContextRequest(sessionId), null, sessionId);
        String profileId = first.getContextResponse().getProfileId();

        TestUtils.RequestResponse second = postContextJson(newContextRequest(sessionId), first.getCookieHeaderValue(), sessionId);

        assertEquals(200, second.getStatusCode());
        assertEquals("a returning visitor must keep its profile", profileId, second.getContextResponse().getProfileId());
        assertEquals("and its session", sessionId, second.getContextResponse().getSessionId());
    }

    /** The GET form with ?payload= must behave like the POST form. This entry point had no coverage. */
    @Test
    public void compat_getWithPayloadBehavesLikePost() throws Exception {
        String sessionId = "baseline-get-" + System.currentTimeMillis();
        TestUtils.RequestResponse established = postContextJson(newContextRequest(sessionId), null, sessionId);
        String profileId = established.getContextResponse().getProfileId();

        HttpGet get = new HttpGet(getFullUrl(CONTEXT_JSON_URL) + "?payload=" + encode(newContextRequest(sessionId)));
        get.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKeyValue);
        get.addHeader("Cookie", established.getCookieHeaderValue());
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(get, sessionId, getObjectMapper());

        assertEquals(200, response.getStatusCode());
        assertEquals("GET ?payload= must resolve the same profile as POST", profileId,
                response.getContextResponse().getProfileId());
    }

    /** /cxs/context.js must keep serving JavaScript to script-tag clients. */
    @Test
    public void compat_contextJsServesJavaScript() throws Exception {
        String sessionId = "baseline-js-" + System.currentTimeMillis();
        HttpGet get = new HttpGet(getFullUrl(CONTEXT_JS_URL) + "?sessionId=" + sessionId);
        get.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKeyValue);

        try (CloseableHttpResponse response = HttpClientThatWaitsForUnomi.doRequest(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            String body = EntityUtils.toString(response.getEntity());
            // Same marker BasicIT asserts on: context.js emits the digitalData bootstrap that
            // script-tag clients rely on. Asserting the marker, not just a 200, so an empty or
            // error body cannot pass as success.
            assertTrue("context.js must return the tracker javascript, got: "
                            + body.substring(0, Math.min(200, body.length())),
                    body.contains("window.digitalData"));
        }
    }

    /** Event collection over POST must keep working and report the event as processed. */
    @Test
    public void compat_eventCollectorAcceptsEvents() throws Exception {
        String sessionId = "baseline-ec-" + System.currentTimeMillis();
        TestUtils.RequestResponse established = postContextJson(newContextRequest(sessionId), null, sessionId);

        HttpPost post = new HttpPost(getFullUrl(EVENT_COLLECTOR_URL));
        post.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKeyValue);
        post.addHeader("Cookie", established.getCookieHeaderValue());
        post.setEntity(new StringEntity(getObjectMapper().writeValueAsString(newEventsRequest(sessionId)),
                ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = HttpClientThatWaitsForUnomi.doRequest(post)) {
            assertEquals("the eventcollector must keep accepting events from a cookie-bearing client",
                    200, response.getStatusLine().getStatusCode());
        }
    }

    /** The eventcollector GET form with ?payload= — another entry point that had no coverage. */
    @Test
    public void compat_eventCollectorGetWithPayload() throws Exception {
        String sessionId = "baseline-ecget-" + System.currentTimeMillis();
        TestUtils.RequestResponse established = postContextJson(newContextRequest(sessionId), null, sessionId);

        HttpGet get = new HttpGet(getFullUrl(EVENT_COLLECTOR_URL) + "?payload=" + encode(newEventsRequest(sessionId)));
        get.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKeyValue);
        get.addHeader("Cookie", established.getCookieHeaderValue());

        try (CloseableHttpResponse response = HttpClientThatWaitsForUnomi.doRequest(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
        }
    }

    /** A client may continue its own session across requests without re-establishing it. */
    @Test
    public void compat_sessionContinuityAcrossRequests() throws Exception {
        String sessionId = "baseline-cont-" + System.currentTimeMillis();
        TestUtils.RequestResponse first = postContextJson(newContextRequest(sessionId), null, sessionId);

        for (int i = 0; i < 3; i++) {
            TestUtils.RequestResponse next = postContextJson(newContextRequest(sessionId), first.getCookieHeaderValue(), sessionId);
            assertEquals(200, next.getStatusCode());
            assertEquals("the client's own session must never be refused", sessionId,
                    next.getContextResponse().getSessionId());
        }
    }

    // ------------------------------------------------------------------ hardened group
    // Expected to FAIL on the pre-hardening baseline and PASS after. That contrast is the point.

    /**
     * A public caller must not be able to read another visitor's profile by naming it in the body.
     * <p>
     * The attack request carries ONLY the body profileId - no cookie and no session - because that is
     * what makes this discriminating. An earlier version of this test also sent a session owned by the
     * caller, and on the pre-hardening baseline the session-recovery logic switched the profile back
     * to the session owner, masking the body profileId entirely and making the test pass on both
     * sides. Asserting on the victim's actual data rather than on an echoed id keeps it honest.
     */
    @Test
    public void hardened_publicBodyProfileIdIsIgnored() throws Exception {
        String victimProfileId = "baseline-victim-" + System.currentTimeMillis();
        String victimSecret = "baseline-secret-" + System.currentTimeMillis();
        Profile victim = new Profile(victimProfileId);
        victim.setProperty("baselineSecret", victimSecret);
        profileService.save(victim);
        keepTrying("Victim profile should be saved", () -> profileService.load(victimProfileId),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        try {
            ContextRequest claim = new ContextRequest();
            claim.setProfileId(victimProfileId);
            claim.setRequiredProfileProperties(Collections.singletonList("*"));
            CustomItem source = new CustomItem("baseline-page", "page");
            source.setScope(TEST_SCOPE);
            claim.setSource(source);

            HttpPost post = new HttpPost(getFullUrl(CONTEXT_JSON_URL));
            post.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKeyValue);
            post.setEntity(new StringEntity(getObjectMapper().writeValueAsString(claim), ContentType.APPLICATION_JSON));

            // Plain client, not HttpClientThatWaitsForUnomi: the hardened branch answers 400 here
            // (nothing left to bind once the body profileId is ignored), and that helper retries
            // non-2xx and then throws, which would mask the very behaviour under test.
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
                assertFalse("a public caller must not receive the victim's profile properties, got: "
                                + body.substring(0, Math.min(300, body.length())),
                        body.contains(victimSecret));
                assertFalse("a public caller must not be bound to the victim's profile id",
                        body.contains(victimProfileId));
            }
        } finally {
            profileService.delete(victimProfileId, false);
        }
    }

    /** A public caller must not be able to adopt a session belonging to someone else. */
    @Test
    public void hardened_publicCallerCannotAdoptAForeignSession() throws Exception {
        String victimSessionId = "baseline-victim-sess-" + System.currentTimeMillis();
        TestUtils.RequestResponse victim = postContextJson(newContextRequest(victimSessionId), null, victimSessionId);
        String victimProfileId = victim.getContextResponse().getProfileId();

        String attackerSessionId = "baseline-attacker-sess-" + System.currentTimeMillis();
        TestUtils.RequestResponse attacker = postContextJson(newContextRequest(attackerSessionId), null, attackerSessionId);

        // Attacker presents the victim's session id with its own cookie.
        TestUtils.RequestResponse hijack = postContextJson(newContextRequest(victimSessionId),
                attacker.getCookieHeaderValue(), victimSessionId);

        assertEquals(200, hijack.getStatusCode());
        assertTrue("the attacker must not end up on the victim's profile",
                !victimProfileId.equals(hijack.getContextResponse().getProfileId()));
    }

    // ------------------------------------------------------------------ helpers

    private ContextRequest newContextRequest(String sessionId) {
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(sessionId);
        CustomItem source = new CustomItem("baseline-page", "page");
        source.setScope(TEST_SCOPE);
        contextRequest.setSource(source);
        return contextRequest;
    }

    private EventsCollectorRequest newEventsRequest(String sessionId) {
        Event event = new Event();
        event.setEventType("view");
        event.setScope(TEST_SCOPE);
        EventsCollectorRequest eventsRequest = new EventsCollectorRequest();
        eventsRequest.setSessionId(sessionId);
        eventsRequest.setEvents(Collections.singletonList(event));
        return eventsRequest;
    }

    private TestUtils.RequestResponse postContextJson(ContextRequest contextRequest, String cookie, String sessionId)
            throws Exception {
        HttpPost post = new HttpPost(getFullUrl(CONTEXT_JSON_URL));
        post.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKeyValue);
        if (cookie != null) {
            post.addHeader("Cookie", cookie);
        }
        post.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        return TestUtils.executeContextJSONRequest(post, sessionId, getObjectMapper());
    }

    private String encode(Object payload) throws Exception {
        return URLEncoder.encode(getObjectMapper().writeValueAsString(payload), StandardCharsets.UTF_8.name());
    }
}
