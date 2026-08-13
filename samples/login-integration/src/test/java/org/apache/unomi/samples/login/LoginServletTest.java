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
package org.apache.unomi.samples.login;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the security-relevant helpers of {@link LoginServlet}.
 * <p>
 * The methods under test are package-private (rather than private) purely so these tests can call
 * them directly instead of reaching through reflection; they are not part of any public API.
 */
class LoginServletTest {

    // ------------------------------------------------------------------------------------------
    // resolveSessionId — the trust boundary. A client-supplied session id must never be honoured.
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("regression guard: a client-supplied sessionId parameter is ignored entirely")
    void clientSuppliedSessionIdParameterIsIgnored() {
        String publicCallerSuppliedId = "other-session-id-we-want-to-reuse";
        HttpSession session = statefulSession();
        HttpServletRequest req = requestWithSession(session);
        // Simulate every channel an untrusted caller controls: query/form parameters and headers.
        when(req.getParameter(anyString())).thenReturn(publicCallerSuppliedId);
        when(req.getHeader(anyString())).thenReturn(publicCallerSuppliedId);

        String resolved = LoginServlet.resolveSessionId(req);

        assertNotEquals(publicCallerSuppliedId, resolved,
                "the servlet must not adopt a session id supplied by the caller");
        // Stronger than comparing values: prove the request's untrusted surface is never
        // even consulted, so no future refactor can quietly reintroduce the vulnerability.
        verify(req, never()).getParameter(anyString());
        verify(req, never()).getParameterValues(anyString());
        verify(req, never()).getHeader(anyString());
        verify(req, never()).getCookies();
    }

    @Test
    @DisplayName("the generated session id is a server-side random UUID stored on the container session")
    void generatedSessionIdIsARandomUuidStoredOnTheSession() {
        HttpSession session = statefulSession();

        String resolved = LoginServlet.resolveSessionId(requestWithSession(session));

        assertNotNull(resolved);
        assertDoesNotThrow(() -> UUID.fromString(resolved), "expected a random UUID, got: " + resolved);
        assertEquals(resolved, session.getAttribute("org.apache.unomi.samples.login.unomiSessionId"),
                "the resolved id must be the one persisted on the container session");
    }

    @Test
    @DisplayName("the same browser session yields a stable session id across calls")
    void sameSessionYieldsStableSessionId() {
        HttpSession session = statefulSession();

        String first = LoginServlet.resolveSessionId(requestWithSession(session));
        String second = LoginServlet.resolveSessionId(requestWithSession(session));
        String third = LoginServlet.resolveSessionId(requestWithSession(session));

        assertEquals(first, second);
        assertEquals(first, third);
    }

    @Test
    @DisplayName("two different browser sessions yield different session ids")
    void differentSessionsYieldDifferentSessionIds() {
        String first = LoginServlet.resolveSessionId(requestWithSession(statefulSession()));
        String second = LoginServlet.resolveSessionId(requestWithSession(statefulSession()));

        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("sessions created by this servlet get a short idle timeout so they cannot accumulate")
    void createdSessionsAreGivenAShortIdleTimeout() {
        HttpSession session = statefulSession();

        LoginServlet.resolveSessionId(requestWithSession(session));

        verify(session).setMaxInactiveInterval(intThatIsAShortTimeout());
    }

    @Test
    @DisplayName("the idle timeout is applied only when the id is first created, not on every request")
    void idleTimeoutIsAppliedOnlyOnFirstUse() {
        HttpSession session = statefulSession();

        LoginServlet.resolveSessionId(requestWithSession(session));
        LoginServlet.resolveSessionId(requestWithSession(session));
        LoginServlet.resolveSessionId(requestWithSession(session));

        verify(session, atMostOnce()).setMaxInactiveInterval(anyInt());
    }

    @Test
    @DisplayName("an existing container session is reused rather than replaced")
    void existingSessionIsReused() {
        HttpSession session = statefulSession();
        HttpServletRequest req = requestWithSession(session);

        LoginServlet.resolveSessionId(req);

        // getSession(true) is correct: the servlet needs a session to exist. What must not happen is
        // the servlet inventing a second identity source.
        verify(req, times(1)).getSession(anyBoolean());
    }

    // ------------------------------------------------------------------------------------------
    // isSameOrigin — lightweight CSRF defence.
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a matching origin is accepted")
    void sameOriginIsAccepted() {
        assertTrue(LoginServlet.isSameOrigin(
                requestWithOrigin("http://example.com:8181", "http", "example.com", 8181)));
    }

    @Test
    @DisplayName("an http origin with no explicit port matches port 80")
    void defaultHttpPortIsAccepted() {
        assertTrue(LoginServlet.isSameOrigin(
                requestWithOrigin("http://example.com", "http", "example.com", 80)));
    }

    @Test
    @DisplayName("an https origin with no explicit port matches port 443")
    void defaultHttpsPortIsAccepted() {
        assertTrue(LoginServlet.isSameOrigin(
                requestWithOrigin("https://example.com", "https", "example.com", 443)));
    }

    @Test
    @DisplayName("origin comparison is case-insensitive on scheme and host")
    void originComparisonIsCaseInsensitive() {
        assertTrue(LoginServlet.isSameOrigin(
                requestWithOrigin("HTTP://Example.COM:8181", "http", "example.com", 8181)));
    }

    @Test
    @DisplayName("a different host is rejected")
    void differentHostIsRejected() {
        assertFalse(LoginServlet.isSameOrigin(
                requestWithOrigin("http://evil.example.net:8181", "http", "example.com", 8181)));
    }

    @Test
    @DisplayName("a different port is rejected")
    void differentPortIsRejected() {
        assertFalse(LoginServlet.isSameOrigin(
                requestWithOrigin("http://example.com:9090", "http", "example.com", 8181)));
    }

    @Test
    @DisplayName("an implicit default port that does not match the served port is rejected")
    void implicitDefaultPortMismatchIsRejected() {
        assertFalse(LoginServlet.isSameOrigin(
                requestWithOrigin("http://example.com", "http", "example.com", 8181)));
    }

    @Test
    @DisplayName("a different scheme is rejected")
    void differentSchemeIsRejected() {
        assertFalse(LoginServlet.isSameOrigin(
                requestWithOrigin("https://example.com:8181", "http", "example.com", 8181)));
    }

    @Test
    @DisplayName("a missing Origin header is tolerated")
    void missingOriginIsTolerated() {
        assertTrue(LoginServlet.isSameOrigin(
                requestWithOrigin(null, "http", "example.com", 8181)));
    }

    @Test
    @DisplayName("a blank Origin header is tolerated")
    void blankOriginIsTolerated() {
        assertTrue(LoginServlet.isSameOrigin(
                requestWithOrigin("   ", "http", "example.com", 8181)));
    }

    @Test
    @DisplayName("the opaque \"null\" origin sent by sandboxed frames is rejected")
    void opaqueNullOriginIsRejected() {
        assertFalse(LoginServlet.isSameOrigin(
                requestWithOrigin("null", "http", "example.com", 8181)),
                "the literal string \"null\" is an opaque origin, not a missing header");
    }

    @Test
    @DisplayName("an unparsable Origin header is rejected")
    void unparsableOriginIsRejected() {
        assertFalse(LoginServlet.isSameOrigin(
                requestWithOrigin("http://exa mple.com", "http", "example.com", 8181)));
    }

    @Test
    @DisplayName("a syntactically valid but host-less Origin is rejected")
    void hostlessOriginIsRejected() {
        assertFalse(LoginServlet.isSameOrigin(
                requestWithOrigin("file:///etc/passwd", "http", "example.com", 8181)));
    }

    // ------------------------------------------------------------------------------------------
    // headerValues — multi-value, case-insensitive Set-Cookie forwarding.
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("headerValues returns every value of a repeated header")
    void headerValuesReturnsAllValues() throws Exception {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Set-Cookie", Arrays.asList("context-profile-id=p1; Path=/", "context-session-id=s1; Path=/"));

        List<String> values = LoginServlet.headerValues(connectionWithHeaders(headers), "Set-Cookie");

        assertEquals(Arrays.asList("context-profile-id=p1; Path=/", "context-session-id=s1; Path=/"), values);
    }

    @Test
    @DisplayName("headerValues matches the header name case-insensitively")
    void headerValuesMatchesNameCaseInsensitively() throws Exception {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        // Servers are free to use any casing; HttpURLConnection preserves what came off the wire.
        headers.put("set-cookie", new ArrayList<>(Arrays.asList("a=1", "b=2")));

        List<String> values = LoginServlet.headerValues(connectionWithHeaders(headers), "Set-Cookie");

        assertEquals(Arrays.asList("a=1", "b=2"), values);
    }

    @Test
    @DisplayName("headerValues tolerates the null status-line key and returns null for an absent header")
    void headerValuesReturnsNullWhenAbsent() throws Exception {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        // HttpURLConnection.getHeaderFields() maps the HTTP status line under a null key.
        headers.put(null, Arrays.asList("HTTP/1.1 200 OK"));
        headers.put("Content-Type", Arrays.asList("application/json"));

        assertNull(LoginServlet.headerValues(connectionWithHeaders(headers), "Set-Cookie"));
    }

    // ------------------------------------------------------------------------------------------
    // Fakes / helpers
    // ------------------------------------------------------------------------------------------

    /** A mock {@link HttpSession} with real attribute storage, so id stability can be observed. */
    private static HttpSession statefulSession() {
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttribute(anyString())).thenAnswer(inv -> attributes.get(inv.<String>getArgument(0)));
        doAnswer(inv -> {
            attributes.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), any());
        return session;
    }

    private static HttpServletRequest requestWithSession(HttpSession session) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getSession(anyBoolean())).thenReturn(session);
        return req;
    }

    private static HttpServletRequest requestWithOrigin(String origin, String scheme, String serverName, int port) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Origin")).thenReturn(origin);
        when(req.getScheme()).thenReturn(scheme);
        when(req.getServerName()).thenReturn(serverName);
        when(req.getServerPort()).thenReturn(port);
        return req;
    }

    private static HttpURLConnection connectionWithHeaders(Map<String, List<String>> headers) throws Exception {
        return new HttpURLConnection(new URL("http://localhost:8181/cxs/context.json")) {
            @Override
            public Map<String, List<String>> getHeaderFields() {
                return headers;
            }

            @Override
            public void connect() {
                // never actually connects
            }

            @Override
            public void disconnect() {
                // nothing to release
            }

            @Override
            public boolean usingProxy() {
                return false;
            }
        };
    }

    /**
     * Matches any timeout that is positive and no longer than ten minutes: the exact value is a
     * tuning detail, but "short and bounded" is the security property we care about.
     */
    private static int intThatIsAShortTimeout() {
        return org.mockito.ArgumentMatchers.intThat(seconds -> seconds > 0 && seconds <= 600);
    }
}
