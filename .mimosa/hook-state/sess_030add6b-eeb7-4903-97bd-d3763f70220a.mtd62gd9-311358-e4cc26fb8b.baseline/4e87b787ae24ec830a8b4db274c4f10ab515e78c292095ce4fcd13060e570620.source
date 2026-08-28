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

package org.apache.unomi.healthcheck.servlet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An unset {@code org.apache.unomi.healthcheck.password} resolves to the empty string, which
 * {@code PropertiesLoginModule} accepts as this account's password (UNOMI-974). {@code /health/check}
 * authenticates against the karaf realm directly rather than through the REST
 * {@code AuthenticationFilter}, so it carries its own refusal — this covers it.
 * <p>
 * The realm stubbed here accepts <em>any</em> credential. That is the point: a test against a
 * rejecting realm would pass whether or not the guard exists, since both answer "not authenticated".
 * Only an always-succeeding realm can distinguish "refused before JAAS" from "JAAS said no".
 */
class HealthCheckHttpContextBlankPasswordTest {

    private static final String REALM = "karaf";

    private Configuration previousConfiguration;
    private HealthCheckHttpContext context;

    @BeforeEach
    void setUp() {
        previousConfiguration = Configuration.getConfiguration();
        Configuration.setConfiguration(new AlwaysSucceedingConfiguration());
        AlwaysSucceedingLoginModule.reset();
        context = new HealthCheckHttpContext(REALM);
    }

    @AfterEach
    void tearDown() {
        Configuration.setConfiguration(previousConfiguration);
    }

    @Test
    void blankPasswordIsRefused() {
        assertFalse(context.authenticated(requestWith("health:")));
    }

    @Test
    void blankUserAndPasswordIsRefused() {
        assertFalse(context.authenticated(requestWith(":")));
    }

    /**
     * Regression test for the real bypass, and the reason this asserts on the captured password
     * rather than on the return value: {@code split(":")} discards trailing empty strings, so
     * {@code "health::x"} decoded to {@code ["health", "", "x"]} and {@code parts[1]} was the
     * <em>empty</em> string, handed to JAAS as the password. Because the stubbed realm accepts
     * anything, that bypass still returned "authenticated" — only inspecting what JAAS was actually
     * given can tell the two apart. Bounding the split to two parts makes the password everything
     * after the first colon ({@code ":x"} here), per RFC 7617.
     */
    @Test
    void extraColonsDoNotCollapseIntoABlankPassword() {
        assertTrue(context.authenticated(requestWith("health::x")));

        assertEquals("health", AlwaysSucceedingLoginModule.lastUser);
        assertEquals(":x", AlwaysSucceedingLoginModule.lastPassword);
    }

    /** Control: an ordinary credential still reaches the realm, unaltered. */
    @Test
    void nonBlankPasswordReachesJaas() {
        assertTrue(context.authenticated(requestWith("health:a-strong-password")));

        assertEquals("health", AlwaysSucceedingLoginModule.lastUser);
        assertEquals("a-strong-password", AlwaysSucceedingLoginModule.lastPassword);
    }

    /** A password of spaces is a real (if terrible) password, not the blank-resolution failure. */
    @Test
    void whitespacePasswordIsNotTreatedAsBlank() {
        assertTrue(context.authenticated(requestWith("health: ")));

        assertEquals(" ", AlwaysSucceedingLoginModule.lastPassword);
    }

    /** The guard must refuse before JAAS is consulted at all, not rely on the realm to say no. */
    @Test
    void blankPasswordNeverReachesJaas() {
        assertFalse(context.authenticated(requestWith("health:")));

        assertNull(AlwaysSucceedingLoginModule.lastUser);
        assertNull(AlwaysSucceedingLoginModule.lastPassword);
    }

    @Test
    void malformedHeadersAreRefused() {
        assertFalse(context.authenticated(requestWith("no-colon-at-all")));
        assertFalse(context.authenticated(requestWithRawHeader("Basic not-base64!!")));
        assertFalse(context.authenticated(requestWithRawHeader("Bearer some-token")));
        assertFalse(context.authenticated(requestWithRawHeader(null)));

        assertNull(AlwaysSucceedingLoginModule.lastUser, "no malformed header may reach JAAS");
    }

    // ------------------------------------------------------- extractBasicCredentials, exhaustively

    /**
     * Every header shape that yields no usable credential. All must produce {@code null} rather than
     * throwing: this runs on unauthenticated, attacker-controlled input, and the original
     * implementation threw out of the servlet (a 500) on several of these.
     */
    @ParameterizedTest(name = "[{index}] rejected: {0}")
    @MethodSource("unusableHeaders")
    void extractBasicCredentials_returnsNullFor(String description, String header) {
        assertNull(context.extractBasicCredentials(header), description);
    }

    static Stream<Arguments> unusableHeaders() {
        return Stream.of(
                Arguments.of("null header", null),
                Arguments.of("empty header", ""),
                Arguments.of("shorter than the scheme", "Bas"),
                Arguments.of("scheme with no trailing space", "Basic"),
                Arguments.of("a different scheme", "Bearer some-token"),
                Arguments.of("scheme only, nothing to decode", "Basic "),
                Arguments.of("not valid base64", "Basic not-base64!!"),
                Arguments.of("valid base64, no colon separator", "Basic " + b64("nocolon")),
                Arguments.of("valid base64, empty payload", "Basic " + b64("")));
    }

    /**
     * Every header shape that yields a credential, and exactly what it decodes to. Pins the RFC 7617
     * rule (password is everything after the <em>first</em> colon) and the case-insensitive scheme
     * match of RFC 7235 §2.1 — the previous blind {@code substring(6)} accepted {@code "basic "},
     * so tightening the prefix check had to preserve that.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("usableHeaders")
    void extractBasicCredentials_decodes(String description, String header, String user, String password) {
        String[] parts = context.extractBasicCredentials(header);

        assertNotNull(parts, description);
        assertEquals(2, parts.length);
        assertEquals(user, parts[0], description);
        assertEquals(password, parts[1], description);
    }

    static Stream<Arguments> usableHeaders() {
        return Stream.of(
                Arguments.of("ordinary credential", "Basic " + b64("health:s3cret"), "health", "s3cret"),
                Arguments.of("empty password", "Basic " + b64("health:"), "health", ""),
                Arguments.of("empty user", "Basic " + b64(":s3cret"), "", "s3cret"),
                Arguments.of("both empty", "Basic " + b64(":"), "", ""),
                Arguments.of("password is a lone colon", "Basic " + b64("health::"), "health", ":"),
                Arguments.of("password starts with a colon", "Basic " + b64("health::x"), "health", ":x"),
                Arguments.of("password contains colons", "Basic " + b64("health:pa:ss:wd"), "health", "pa:ss:wd"),
                Arguments.of("password is whitespace", "Basic " + b64("health: "), "health", " "),
                Arguments.of("lowercase scheme", "basic " + b64("health:s3cret"), "health", "s3cret"),
                Arguments.of("mixed-case scheme", "BaSiC " + b64("health:s3cret"), "health", "s3cret"),
                Arguments.of("padded base64", "Basic   " + b64("health:s3cret") + "  ", "health", "s3cret"),
                Arguments.of("non-ASCII password", "Basic " + b64("health:pässwörd"), "health", "pässwörd"));
    }

    /**
     * Answers the question directly: can {@code parts[1]} be {@code null}, making the
     * {@code password.isEmpty()} guard throw? It cannot. {@link String#split(String, int)} only ever
     * produces non-null substrings, and any result that is not exactly two elements is rejected
     * before it is returned — so both elements are always non-null when a caller gets an array.
     */
    @ParameterizedTest
    @MethodSource("usableHeaders")
    void extractBasicCredentials_neverReturnsNullElements(String description, String header, String user,
            String password) {
        String[] parts = context.extractBasicCredentials(header);

        assertNotNull(parts[0], description);
        assertNotNull(parts[1], description);
    }

    private static String b64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private HttpServletRequest requestWith(String credentials) {
        return requestWithRawHeader("Basic " + b64(credentials));
    }

    private HttpServletRequest requestWithRawHeader(String authorization) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(authorization);
        return request;
    }

    /** Minimal JAAS setup so {@code new LoginContext(realm, ...)} succeeds without a real Karaf realm. */
    private static class AlwaysSucceedingConfiguration extends Configuration {
        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            if (!REALM.equals(name)) {
                return null;
            }
            return new AppConfigurationEntry[]{
                    new AppConfigurationEntry(
                            AlwaysSucceedingLoginModule.class.getName(),
                            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                            new HashMap<>())
            };
        }
    }

    public static class AlwaysSucceedingLoginModule implements LoginModule {

        /**
         * What the realm was actually handed, so a test can distinguish "refused before JAAS" from
         * "JAAS was called with a password the parser mangled". Static because JAAS instantiates the
         * module reflectively, leaving no handle on the instance; {@link #reset()} runs before each
         * test, and these tests are not parallelised.
         */
        static String lastUser;
        static String lastPassword;

        static void reset() {
            lastUser = null;
            lastPassword = null;
        }

        private Subject subject;
        private CallbackHandler callbackHandler;

        @Override
        public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState,
                Map<String, ?> options) {
            this.subject = subject;
            this.callbackHandler = callbackHandler;
        }

        @Override
        public boolean login() throws LoginException {
            try {
                NameCallback nameCallback = new NameCallback("name");
                PasswordCallback passwordCallback = new PasswordCallback("password", false);
                callbackHandler.handle(new Callback[]{nameCallback, passwordCallback});
                lastUser = nameCallback.getName();
                lastPassword = passwordCallback.getPassword() == null
                        ? null : new String(passwordCallback.getPassword());
            } catch (IOException | UnsupportedCallbackException e) {
                throw new LoginException(e.getMessage());
            }
            return true;
        }

        @Override
        public boolean commit() {
            return true;
        }

        @Override
        public boolean abort() {
            return true;
        }

        @Override
        public boolean logout() {
            subject.getPrincipals().clear();
            return true;
        }
    }
}
