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
import static org.junit.Assert.assertNull;
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
     * The probe request carries ONLY the body profileId - no cookie and no session - because that is
     * what makes this discriminating. An earlier version of this test also sent a session owned by the
     * caller, and on the pre-hardening baseline the session-recovery logic switched the profile back
     * to the session owner, masking the body profileId entirely and making the test pass on both
     * sides. Asserting on the other's actual data rather than on an echoed id keeps it honest.
     */
    @Test
    public void hardened_publicBodyProfileIdIsIgnored() throws Exception {
        String otherProfileId = "baseline-other-" + System.currentTimeMillis();
        String otherSecret = "baseline-secret-" + System.currentTimeMillis();
        Profile other = new Profile(otherProfileId);
        other.setProperty("baselineSecret", otherSecret);
        profileService.save(other);
        keepTrying("Other profile should be saved", () -> profileService.load(otherProfileId),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        try {
            ContextRequest claim = new ContextRequest();
            claim.setProfileId(otherProfileId);
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
                assertFalse("a public caller must not receive the other's profile properties, got: "
                                + body.substring(0, Math.min(300, body.length())),
                        body.contains(otherSecret));
                assertFalse("a public caller must not be bound to the other's profile id",
                        body.contains(otherProfileId));
            }
        } finally {
            profileService.delete(otherProfileId, false);
        }
    }

    /**
     * A public caller must not be able to adopt a session belonging to someone else.
     * <p>
     * Three things have to hold, not just the first: the caller must not end up on the other's
     * profile, the refused id must not be echoed back (or the client keeps replaying it), and the
     * other visitor must still hold the session afterwards - refusing by handing the session to the
     * caller anyway would satisfy the first assertion alone.
     */
    @Test
    public void hardened_publicCallerCannotAdoptAForeignSession() throws Exception {
        String otherSessionId = "baseline-other-sess-" + System.currentTimeMillis();
        TestUtils.RequestResponse other = postContextJson(newContextRequest(otherSessionId), null, otherSessionId);
        String otherProfileId = other.getContextResponse().getProfileId();

        String publicCallerSessionId = "baseline-public-sess-" + System.currentTimeMillis();
        TestUtils.RequestResponse publicCaller = postContextJson(newContextRequest(publicCallerSessionId), null, publicCallerSessionId);

        // Untrusted caller presents the other's session id with its own cookie.
        TestUtils.RequestResponse attempt = postContextJson(newContextRequest(otherSessionId),
                publicCaller.getCookieHeaderValue(), otherSessionId);

        assertEquals(200, attempt.getStatusCode());
        assertTrue("the untrusted caller must not end up on the other's profile",
                !otherProfileId.equals(attempt.getContextResponse().getProfileId()));
        assertNull("a refused session id must not be echoed back to the caller",
                attempt.getContextResponse().getSessionId());

        // The rightful owner comes back: the session must still be theirs and still be accepted.
        TestUtils.RequestResponse ownerAgain = postContextJson(newContextRequest(otherSessionId),
                other.getCookieHeaderValue(), otherSessionId);
        assertEquals("the rightful owner must keep its profile", otherProfileId,
                ownerAgain.getContextResponse().getProfileId());
        assertEquals("and must not have lost the session to the caller that was refused",
                otherSessionId, ownerAgain.getContextResponse().getSessionId());
    }

    /**
     * The same takeover, aimed at an <em>anonymous</em> session.
     * <p>
     * An anonymous session records no owner at all - {@code PrivacyService#getAnonymousProfile}
     * returns a profile with no id, so the session's profileId is null - which left the ownership
     * rule with nothing to compare the cookie against. Presenting the id was therefore enough to have
     * the session rebound to the presenter's own profile and saved. The session must stay anonymous.
     * <p>
     * The check is made through the rightful owner's next request rather than by reading the session
     * back: if the takeover had happened the session would now carry a real, foreign profile, and the
     * owner would be refused by the ownership rule that covers named sessions.
     */
    @Test
    public void hardened_publicCallerCannotTakeOverAnAnonymousSession() throws Exception {
        String victimSessionId = "baseline-anon-sess-" + System.currentTimeMillis();
        TestUtils.RequestResponse victim = postContextJson(newContextRequest(victimSessionId), null, victimSessionId);
        String victimProfileId = victim.getContextResponse().getProfileId();

        try {
            // The visitor asks for anonymous browsing, then makes one request so the session picks the
            // anonymous profile up.
            privacyService.setRequireAnonymousBrowsing(victimProfileId, true, TEST_SCOPE);
            keepTrying("Profile should require anonymous browsing",
                    () -> privacyService.isRequireAnonymousBrowsing(victimProfileId),
                    Boolean.TRUE::equals, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            postContextJson(newContextRequest(victimSessionId), victim.getCookieHeaderValue(), victimSessionId);
            keepTrying("Session should have become anonymous",
                    () -> profileService.loadSession(victimSessionId),
                    session -> session != null && session.getProfileId() == null,
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

            // Someone else presents that session id with their own cookie.
            String attackerSessionId = "baseline-anon-attacker-" + System.currentTimeMillis();
            TestUtils.RequestResponse attacker = postContextJson(newContextRequest(attackerSessionId), null, attackerSessionId);
            String attackerProfileId = attacker.getContextResponse().getProfileId();
            postContextJson(newContextRequest(victimSessionId), attacker.getCookieHeaderValue(), victimSessionId);

            keepTrying("The anonymous session must not be reassigned to the caller",
                    () -> profileService.loadSession(victimSessionId),
                    session -> session != null && !attackerProfileId.equals(session.getProfileId()),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

            // And the rightful owner is still served by it.
            TestUtils.RequestResponse ownerAgain = postContextJson(newContextRequest(victimSessionId),
                    victim.getCookieHeaderValue(), victimSessionId);
            assertEquals("the anonymous visitor must keep its own profile", victimProfileId,
                    ownerAgain.getContextResponse().getProfileId());
            assertEquals("and must keep its session", victimSessionId,
                    ownerAgain.getContextResponse().getSessionId());
        } finally {
            privacyService.setRequireAnonymousBrowsing(victimProfileId, false, TEST_SCOPE);
        }
    }

    /**
     * The same body-profileId claim, aimed at {@code /eventcollector}.
     * <p>
     * Both endpoints share {@code initEventsRequest}, so this passes today - which is exactly why it
     * is worth pinning. The collector reads its {@code sessionId}/{@code profileId} from a different
     * request model and even falls back to a query parameter, so a change on that side could route
     * around the binding rule without any context.json test noticing.
     * <p>
     * The collector's response body carries no profile id, so the assertion is on the profile cookie
     * the request is answered with: that is the profile the server decided the caller is.
     */
    @Test
    public void hardened_eventCollectorIgnoresPublicBodyProfileId() throws Exception {
        String otherProfileId = "baseline-ec-other-" + System.currentTimeMillis();
        Profile other = new Profile(otherProfileId);
        profileService.save(other);
        keepTrying("Other profile should be saved", () -> profileService.load(otherProfileId),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        try {
            String sessionId = "baseline-ec-claim-" + System.currentTimeMillis();
            EventsCollectorRequest claim = newEventsRequest(sessionId);
            claim.setProfileId(otherProfileId);

            HttpPost post = new HttpPost(getFullUrl(EVENT_COLLECTOR_URL));
            post.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKeyValue);
            post.setEntity(new StringEntity(getObjectMapper().writeValueAsString(claim), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                String setCookie = response.getFirstHeader("Set-Cookie") == null
                        ? "" : response.getFirstHeader("Set-Cookie").getValue();
                assertFalse("the eventcollector must not bind a public caller to a body profileId, got: " + setCookie,
                        setCookie.contains(otherProfileId));
            }
        } finally {
            profileService.delete(otherProfileId, false);
        }
    }

    /** And the session half of the same rule, again through {@code /eventcollector}. */
    @Test
    public void hardened_eventCollectorCannotAdoptAForeignSession() throws Exception {
        String otherSessionId = "baseline-ec-sess-" + System.currentTimeMillis();
        TestUtils.RequestResponse other = postContextJson(newContextRequest(otherSessionId), null, otherSessionId);
        String otherProfileId = other.getContextResponse().getProfileId();

        String callerSessionId = "baseline-ec-caller-" + System.currentTimeMillis();
        TestUtils.RequestResponse caller = postContextJson(newContextRequest(callerSessionId), null, callerSessionId);

        HttpPost post = new HttpPost(getFullUrl(EVENT_COLLECTOR_URL));
        post.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKeyValue);
        post.addHeader("Cookie", caller.getCookieHeaderValue());
        post.setEntity(new StringEntity(getObjectMapper().writeValueAsString(newEventsRequest(otherSessionId)),
                ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            String setCookie = response.getFirstHeader("Set-Cookie") == null
                    ? "" : response.getFirstHeader("Set-Cookie").getValue();
            assertFalse("presenting a foreign session id must not move the caller onto its owner's profile, got: "
                    + setCookie, setCookie.contains(otherProfileId));
        }

        // The owner still has the session.
        TestUtils.RequestResponse ownerAgain = postContextJson(newContextRequest(otherSessionId),
                other.getCookieHeaderValue(), otherSessionId);
        assertEquals("the rightful owner must keep its profile", otherProfileId,
                ownerAgain.getContextResponse().getProfileId());
        assertEquals("and its session", otherSessionId, ownerAgain.getContextResponse().getSessionId());
    }

    /**
     * The profile cookie must actually be issued {@code HttpOnly} by a running server.
     * <p>
     * The shipped defaults are checked separately as configuration text; this asserts the value that
     * survives the whole path from that default through {@code WebConfig} and
     * {@code ConfigSharingService} into the {@code Set-Cookie} header. Binding a public caller to the
     * profile its cookie names only means anything while page script cannot read that cookie, so the
     * flag is part of the security model rather than a preference.
     */
    @Test
    public void hardened_profileCookieIsHttpOnly() throws Exception {
        String sessionId = "baseline-httponly-" + System.currentTimeMillis();
        TestUtils.RequestResponse response = postContextJson(newContextRequest(sessionId), null, sessionId);

        String setCookie = response.getCookieHeaderValue();
        assertNotNull("a first visit must be issued the profile cookie", setCookie);
        assertTrue("the profile cookie must be HttpOnly, got: " + setCookie,
                setCookie.toLowerCase().contains("httponly"));
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
