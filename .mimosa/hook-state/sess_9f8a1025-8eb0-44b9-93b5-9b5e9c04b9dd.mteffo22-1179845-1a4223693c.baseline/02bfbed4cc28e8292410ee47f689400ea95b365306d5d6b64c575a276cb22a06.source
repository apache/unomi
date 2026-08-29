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
package org.apache.unomi.rest.authentication;

import org.apache.cxf.jaxrs.security.JAASAuthenticationFilter;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A blank {@code UNOMI_ROOT_PASSWORD} leaves the shipped JAAS administrator with an empty password
 * that {@code PropertiesLoginModule} accepts. {@code bin/karaf} and the Docker entrypoint refuse to
 * start in that state, but they cannot cover every launcher, so the REST layer must never accept an
 * empty credential either.
 * <p>
 * Covers both the predicate and its wiring into {@link AuthenticationFilter#filter}: the check is
 * applied where a Basic credential is consumed, and must NOT reject anonymous traffic on public
 * paths that ignores {@code Authorization} altogether.
 */
class AuthenticationFilterBlankPasswordTest {

    private RestAuthenticationConfig restAuthenticationConfig;
    private TenantService tenantService;
    private JAASAuthenticationFilter jaasAuthenticationFilter;
    private AuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        restAuthenticationConfig = mock(RestAuthenticationConfig.class);
        tenantService = mock(TenantService.class);
        // Stubbed so the tests below can assert the credential never reached JAAS. Every refusal
        // path in the filter answers 401, so the status alone cannot tell "refused for a blank
        // password" apart from "JAAS rejected it" — only this can.
        jaasAuthenticationFilter = mock(JAASAuthenticationFilter.class);
        filter = new AuthenticationFilter(
                restAuthenticationConfig,
                tenantService,
                mock(SecurityService.class),
                mock(ExecutionContextManager.class),
                jaasAuthenticationFilter);
    }

    @Test
    void emptyPasswordIsRejected() {
        assertTrue(filter.hasBlankBasicAuthPassword(basic("karaf:")));
    }

    @Test
    void emptyUserAndPasswordIsRejected() {
        assertTrue(filter.hasBlankBasicAuthPassword(basic(":")));
    }

    @Test
    void realPasswordIsAccepted() {
        assertFalse(filter.hasBlankBasicAuthPassword(basic("karaf:a-strong-password")));
    }

    /**
     * A password consisting of spaces is a real (if terrible) password, not the blank-resolution
     * failure this guard exists for — leave it to the realm.
     */
    @Test
    void whitespacePasswordIsNotTreatedAsBlank() {
        assertFalse(filter.hasBlankBasicAuthPassword(basic("karaf: ")));
    }

    @Test
    void missingOrNonBasicHeadersAreLeftToTheNormalPaths() {
        assertFalse(filter.hasBlankBasicAuthPassword(null));
        assertFalse(filter.hasBlankBasicAuthPassword("Bearer some-token"));
    }

    @Test
    void malformedHeaderIsLeftToTheNormalPaths() {
        assertFalse(filter.hasBlankBasicAuthPassword("Basic not-base64!!"));
        // No colon at all: cannot be split into user and password.
        assertFalse(filter.hasBlankBasicAuthPassword(basic("karaf")));
    }

    // ------------------------------------------------------------------ filter() wiring

    /**
     * The predicate being correct is not enough: if the call site were dropped or moved after an
     * earlier {@code return}, only this test would notice.
     */
    @Test
    void filterRejectsBlankPasswordOnAnAuthenticatedPath() throws IOException {
        ContainerRequestContext requestContext = request("tenants", basic("karaf:"));

        filter.filter(requestContext);

        assertUnauthorizedWithoutReachingJaas(requestContext);
    }

    /** Control: a non-blank credential on the same path must still be handed to JAAS to judge. */
    @Test
    void filterPassesNonBlankPasswordToJaasOnAnAuthenticatedPath() throws IOException {
        ContainerRequestContext requestContext = request("tenants", basic("karaf:a-strong-password"));

        filter.filter(requestContext);

        verify(jaasAuthenticationFilter).filter(requestContext);
    }

    /**
     * The ordinary V3 branch: not {@code tenants}, not a public path, V2 compatibility off. This is
     * the route most private REST calls take, and it consumes the Basic credential at its own call
     * site — with only the {@code tenants} and V2 tests above, deleting the guard here would leave
     * the whole suite green.
     */
    @Test
    void filterRejectsBlankPasswordOnAPrivatePath() throws IOException {
        when(restAuthenticationConfig.getPublicPathPatterns()).thenReturn(Collections.emptyList());
        ContainerRequestContext requestContext = request("profiles", basic("karaf:"));

        filter.filter(requestContext);

        assertUnauthorizedWithoutReachingJaas(requestContext);
    }

    /**
     * Control for the ordinary V3 branch: a non-blank credential must still be offered to the tenant
     * private-key check and then to JAAS.
     */
    @Test
    void filterPassesNonBlankPasswordToJaasOnAPrivatePath() throws IOException {
        when(restAuthenticationConfig.getPublicPathPatterns()).thenReturn(Collections.emptyList());
        ContainerRequestContext requestContext = request("profiles", basic("karaf:a-strong-password"));

        filter.filter(requestContext);

        verify(jaasAuthenticationFilter).filter(requestContext);
    }

    /**
     * V2 compatibility mode routes every request through {@link AuthenticationFilter}'s own
     * private-endpoint branch, which consumes the Basic credential at a third, separate call site.
     * Without this test that call site is unreachable from the suite: the other tests leave
     * {@code isV2CompatibilityModeEnabled()} at the unstubbed Mockito {@code false}, so deleting
     * the guard there would leave every test green.
     */
    @Test
    void filterRejectsBlankPasswordOnAPrivatePathInV2CompatibilityMode() throws IOException {
        when(restAuthenticationConfig.isV2CompatibilityModeEnabled()).thenReturn(true);
        when(restAuthenticationConfig.getPublicPathPatterns()).thenReturn(Collections.emptyList());
        ContainerRequestContext requestContext = request("profiles", basic("karaf:"));

        filter.filter(requestContext);

        assertUnauthorizedWithoutReachingJaas(requestContext);
    }

    /** Control for the V2 branch: a non-blank credential must still reach JAAS there too. */
    @Test
    void filterPassesNonBlankPasswordToJaasOnAPrivatePathInV2CompatibilityMode() throws IOException {
        when(restAuthenticationConfig.isV2CompatibilityModeEnabled()).thenReturn(true);
        when(restAuthenticationConfig.getPublicPathPatterns()).thenReturn(Collections.emptyList());
        ContainerRequestContext requestContext = request("profiles", basic("karaf:a-strong-password"));

        filter.filter(requestContext);

        verify(jaasAuthenticationFilter).filter(requestContext);
    }

    /**
     * A public path in V2 compatibility mode authenticates by default tenant, ignoring
     * {@code Authorization} entirely — so a stray blank Basic header must not turn it into a 401.
     */
    @Test
    void filterDoesNotRejectAStrayBlankBasicHeaderOnAPublicPathInV2CompatibilityMode() throws IOException {
        when(restAuthenticationConfig.isV2CompatibilityModeEnabled()).thenReturn(true);
        when(restAuthenticationConfig.getPublicPathPatterns())
                .thenReturn(Collections.singletonList(Pattern.compile("POST context\\.json")));
        when(restAuthenticationConfig.getV2CompatibilityDefaultTenantId()).thenReturn("default");
        ContainerRequestContext requestContext = request("context.json", basic("someone:"));

        filter.filter(requestContext);

        verify(tenantService).getTenant("default");
    }

    private void assertUnauthorizedWithoutReachingJaas(ContainerRequestContext requestContext) throws IOException {
        ArgumentCaptor<Response> aborted = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(aborted.capture());
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), aborted.getValue().getStatus());
        verify(jaasAuthenticationFilter, never()).filter(any());
    }

    /**
     * The check has to run where a Basic credential is consumed, not once at the top of
     * {@code filter()}. Anonymous traffic carrying a stray Basic header — a stale cached browser
     * credential, an injecting proxy — must still authenticate by API key on the public path.
     * <p>
     * Asserted by observing that the public-path branch is still reached (the API key is looked up)
     * rather than by asserting no abort: a public path whose API key does not resolve legitimately
     * falls through and is refused for that unrelated reason, and authenticating one successfully
     * needs a live CXF exchange, which is out of scope for a unit test.
     */
    @Test
    void filterConsultsThePublicPathBranchDespiteAStrayBlankBasicHeader() throws IOException {
        when(restAuthenticationConfig.getPublicPathPatterns())
                .thenReturn(Collections.singletonList(Pattern.compile("POST context\\.json")));
        ContainerRequestContext requestContext = request("context.json", basic("someone:"));

        filter.filter(requestContext);

        verify(tenantService).getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PUBLIC));
    }

    /** A public path with no Authorization header at all must equally reach the API-key lookup. */
    @Test
    void filterConsultsThePublicPathBranchForAnonymousRequests() throws IOException {
        when(restAuthenticationConfig.getPublicPathPatterns())
                .thenReturn(Collections.singletonList(Pattern.compile("POST context\\.json")));
        ContainerRequestContext requestContext = request("context.json", null);

        filter.filter(requestContext);

        verify(tenantService).getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PUBLIC));
    }

    private ContainerRequestContext request(String path, String authHeader) {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("POST");
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(authHeader);
        return requestContext;
    }

    private static String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
