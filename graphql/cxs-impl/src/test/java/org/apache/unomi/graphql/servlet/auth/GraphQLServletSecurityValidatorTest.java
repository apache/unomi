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

package org.apache.unomi.graphql.servlet.auth;

import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the JAAS-authenticated tenant resolution branch of {@link GraphQLServletSecurityValidator},
 * WebSocket upgrade auth (subscriptions are never public), and malformed Basic handling.
 */
@ExtendWith(MockitoExtension.class)
class GraphQLServletSecurityValidatorTest {

    private static final String TENANT_HEADER = "X-Unomi-Tenant-Id";
    private static final String BASIC_AUTH = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());

    private Configuration previousConfiguration;

    @Mock
    private TenantService tenantService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ExecutionContextManager executionContextManager;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private GraphQLServletSecurityValidator validator;

    @BeforeEach
    void setUp() {
        previousConfiguration = Configuration.getConfiguration();
        Configuration.setConfiguration(new AlwaysSucceedingKarafConfiguration());
        validator = new GraphQLServletSecurityValidator(tenantService, securityService, executionContextManager);
    }

    @AfterEach
    void tearDown() {
        Configuration.setConfiguration(previousConfiguration);
    }

    /** Grants the administrator role the JAAS branch now requires before it will authorize anything. */
    private void givenAdministratorRole() {
        lenient().when(securityService.hasRole(UnomiRoles.ADMINISTRATOR)).thenReturn(true);
    }

    @Test
    void validate_withInvalidTenantHeader_isRejected() throws IOException {
        // A tenant header that names no known tenant is refused; it must not fall back to the system
        // context, which is inherited by every tenant.
        givenAdministratorRole();
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(request.getHeader(TENANT_HEADER)).thenReturn("not-a-real-tenant");
        when(tenantService.getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PRIVATE))).thenReturn(null);
        when(tenantService.getTenant("not-a-real-tenant")).thenReturn(null);

        boolean authenticated = validator.validate(null, null, request, response);

        assertFalse(authenticated);
        verify(executionContextManager, never()).setCurrentContext(refEq(ExecutionContext.systemContext()));
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void validate_withoutUnomiRole_isRejected() throws IOException {
        // A realm account that carries no Unomi role (the shipped health-check account, for one) must
        // not obtain access, even though the realm login itself succeeds.
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(tenantService.getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PRIVATE))).thenReturn(null);

        boolean authenticated = validator.validate(null, null, request, response);

        assertFalse(authenticated);
        verify(executionContextManager, never()).setCurrentContext(any());
        verify(securityService).clearCurrentSubject();
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void validate_withTenantHeaderButNoAuthorityOverIt_isRejected() throws IOException {
        Tenant tenant = new Tenant();
        tenant.setItemId("someone-elses-tenant");

        givenAdministratorRole();
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(request.getHeader(TENANT_HEADER)).thenReturn("someone-elses-tenant");
        when(tenantService.getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PRIVATE))).thenReturn(null);
        when(tenantService.getTenant("someone-elses-tenant")).thenReturn(tenant);
        when(securityService.hasSystemAccess()).thenReturn(false);
        when(securityService.hasTenantAccess("someone-elses-tenant")).thenReturn(false);

        boolean authenticated = validator.validate(null, null, request, response);

        assertFalse(authenticated);
        verify(executionContextManager, never()).createContext("someone-elses-tenant");
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void validate_withoutTenantHeaderAndNoSystemAccess_isRejected() throws IOException {
        // The system context is not the default for an authenticated caller.
        givenAdministratorRole();
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(request.getHeader(TENANT_HEADER)).thenReturn(null);
        when(tenantService.getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PRIVATE))).thenReturn(null);
        when(securityService.hasSystemAccess()).thenReturn(false);

        boolean authenticated = validator.validate(null, null, request, response);

        assertFalse(authenticated);
        verify(executionContextManager, never()).setCurrentContext(refEq(ExecutionContext.systemContext()));
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void validate_withValidTenantHeader_createsTenantContext() throws IOException {
        Tenant tenant = new Tenant();
        tenant.setItemId("known-tenant");

        givenAdministratorRole();
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(request.getHeader(TENANT_HEADER)).thenReturn("known-tenant");
        when(tenantService.getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PRIVATE))).thenReturn(null);
        when(tenantService.getTenant("known-tenant")).thenReturn(tenant);
        when(securityService.hasTenantAccess("known-tenant")).thenReturn(true);
        ExecutionContext tenantContext = new ExecutionContext("known-tenant", null, null);
        when(executionContextManager.createContext("known-tenant")).thenReturn(tenantContext);

        boolean authenticated = validator.validate(null, null, request, response);

        assertTrue(authenticated);
        verify(executionContextManager).createContext("known-tenant");
        verify(executionContextManager).setCurrentContext(tenantContext);
        verify(executionContextManager, never()).setCurrentContext(refEq(ExecutionContext.systemContext()));
    }

    @Test
    void validate_withSystemAccessAndNoTenantHeader_usesSystemContext() throws IOException {
        givenAdministratorRole();
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(request.getHeader(TENANT_HEADER)).thenReturn(null);
        when(tenantService.getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PRIVATE))).thenReturn(null);
        when(securityService.hasSystemAccess()).thenReturn(true);

        boolean authenticated = validator.validate(null, null, request, response);

        assertTrue(authenticated);
        verify(executionContextManager).setCurrentContext(refEq(ExecutionContext.systemContext()));
        verify(response, never()).sendError(any(Integer.class));
    }

    @Test
    void validate_withoutAuthorizationHeader_isRejected() throws IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean authenticated = validator.validate(null, null, request, response);

        assertFalse(authenticated);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void validateWebSocketUpgrade_withoutAuthorization_isRejected() throws IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean authenticated = validator.validateWebSocketUpgrade(request, response);

        assertFalse(authenticated);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(securityService, never()).setCurrentSubject(any());
    }

    @Test
    void validateWebSocketUpgrade_withBasicAuth_isAccepted() throws IOException {
        givenAdministratorRole();
        when(securityService.hasSystemAccess()).thenReturn(true);
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(tenantService.getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PRIVATE))).thenReturn(null);

        boolean authenticated = validator.validateWebSocketUpgrade(request, response);

        assertTrue(authenticated);
        verify(securityService).setCurrentSubject(any(Subject.class));
        verify(response, never()).sendError(any(Integer.class));
    }

    @Test
    void validateWebSocketUpgrade_rejectsPublicApiKeyOnly() throws IOException {
        // No Authorization header — public API key alone must not open subscriptions.
        // validateWebSocketUpgrade never reads X-Unomi-Api-Key; stub documents the scenario.
        when(request.getHeader("Authorization")).thenReturn(null);
        lenient().when(request.getHeader("X-Unomi-Api-Key")).thenReturn("public-api-key");

        boolean authenticated = validator.validateWebSocketUpgrade(request, response);

        assertFalse(authenticated);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void validateWebSocketUpgrade_withMalformedBasic_isRejected() throws IOException {
        when(request.getHeader("Authorization")).thenReturn("Basic !!!");

        boolean authenticated = validator.validateWebSocketUpgrade(request, response);

        assertFalse(authenticated);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(securityService, never()).setCurrentSubject(any());
    }

    @Test
    void validate_subscriptionQuery_withoutAuthorization_isRejected() throws IOException {
        // HTTP contrast: subscriptions are never public — same invariant the WS upgrade must enforce.
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean authenticated = validator.validate(
                "subscription { eventListener { id } }", null, request, response);

        assertFalse(authenticated);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(securityService, never()).setCurrentSubject(any());
    }

    @Test
    void validate_subscriptionQuery_rejectsPublicApiKeyOnly() throws IOException {
        // Subscriptions are never public: public API key must not authenticate a subscription query.
        when(request.getHeader("Authorization")).thenReturn(null);
        lenient().when(request.getHeader("X-Unomi-Api-Key")).thenReturn("public-api-key");

        boolean authenticated = validator.validate(
                "subscription { eventListener { id } }", null, request, response);

        assertFalse(authenticated);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(securityService, never()).setCurrentSubject(any());
    }

    @Test
    void validate_withMalformedBasic_isRejected() throws IOException {
        when(request.getHeader("Authorization")).thenReturn("Basic not-valid-base64");

        boolean authenticated = validator.validate(null, null, request, response);

        assertFalse(authenticated);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(securityService, never()).setCurrentSubject(any());
    }

    @Test
    void validate_operationAuthorizationBypasses_areRejectedForPublicApiKey() throws IOException {
        // Even holding a valid public API key, none of these documents may run: each is a way the
        // public-operation gate could be tricked into classifying a privileged or ambiguous document as
        // public. They must all fail closed - no public branch, and with no Authorization header, 401.
        lenient().when(request.getHeader("X-Unomi-Api-Key")).thenReturn("public-api-key");

        // A privileged mutation smuggled as a second operation and selected by operationName.
        assertNotPublic("query GetProfile { cdp { getProfile(profileID:{id:\"x\"}) { id } } } "
                + "mutation Pwn { cdp { deleteAllPersonalData(profileID:{id:\"v\"}) } }", "Pwn");
        // A privileged query whose operation is merely NAMED IntrospectionQuery.
        assertNotPublic("query IntrospectionQuery { cdp { findProfiles(first:1000) { edges { node { properties } } } } }", null);
        // A leading fragment definition in front of a privileged operation.
        assertNotPublic("fragment f on Query { __typename } query Q { cdp { deleteProfile(profileID:{id:\"v\"}) } }", "Q");
        // A privileged field hidden inside a fragment spread.
        assertNotPublic("query Q { cdp { ...priv } } fragment priv on CDP_Query { findProfiles(first:10) { edges { node { id } } } }", "Q");
        // An extra non-cdp root field alongside an allowed one.
        assertNotPublic("query Q { cdp { getProfile(profileID:{id:\"x\"}) { id } } segments { edges { node { id } } } }", "Q");
        // Multiple operations with no operationName: the executed operation is ambiguous.
        assertNotPublic("query A { cdp { getProfile(profileID:{id:\"x\"}) { id } } } "
                + "query B { cdp { findProfiles(first:1) { edges { node { id } } } } }", null);
        // A syntactically invalid document.
        assertNotPublic("query { cdp { getProfile ", null);

        verify(securityService, never()).setCurrentSubject(any());
    }

    @Test
    void validate_legitimatePublicOperation_isAuthenticatedWithPublicApiKey() throws IOException {
        Tenant tenant = new Tenant();
        tenant.setItemId("pub-tenant");
        when(request.getHeader("X-Unomi-Api-Key")).thenReturn("public-api-key");
        when(tenantService.getTenantByApiKey("public-api-key", ApiKey.ApiKeyType.PUBLIC)).thenReturn(tenant);
        when(securityService.createSubject("pub-tenant", false)).thenReturn(new Subject());
        ExecutionContext context = new ExecutionContext("pub-tenant", null, null);
        when(executionContextManager.createContext("pub-tenant")).thenReturn(context);

        boolean authenticated = validator.validate(
                "query { cdp { getProfile(profileID:{id:\"x\"}) { id } } }", null, request, response);

        assertTrue(authenticated);
        verify(executionContextManager).setCurrentContext(context);
    }

    private void assertNotPublic(String query, String operationName) throws IOException {
        assertFalse(validator.validate(query, operationName, request, response),
                "Expected document to be denied for a public API key: " + query);
    }

    /**
     * An unset {@code org.apache.unomi.security.root.password} resolves to the empty string, which
     * {@code PropertiesLoginModule} accepts as the shipped administrator's password (UNOMI-974).
     * This servlet logs in against the karaf realm directly, outside the REST
     * {@code AuthenticationFilter}, so it carries its own refusal.
     * <p>
     * The realm stubbed here accepts <em>any</em> credential, so this only passes if the empty
     * password is refused before JAAS is ever consulted — asserting on the 401 alone would prove
     * nothing, since a rejecting realm answers 401 too.
     */
    @Test
    void validate_withBlankPassword_isRejectedBeforeReachingJaas() throws IOException {
        when(request.getHeader("Authorization"))
                .thenReturn("Basic " + Base64.getEncoder().encodeToString("karaf:".getBytes()));

        boolean authenticated = validator.validate(null, null, request, response);

        assertFalse(authenticated);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(securityService, never()).setCurrentSubject(any());
        verify(executionContextManager, never()).setCurrentContext(any());
    }

    /** Control: a non-blank credential still reaches the realm and is accepted by it. */
    @Test
    void validate_withNonBlankPassword_reachesJaas() throws IOException {
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(tenantService.getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PRIVATE))).thenReturn(null);

        boolean authenticated = validator.validate(null, null, request, response);

        assertTrue(authenticated);
        verify(response, never()).sendError(any(Integer.class));
    }

    /**
     * Minimal JAAS configuration that makes {@code new LoginContext("karaf", ...)} succeed
     * without requiring a real Karaf realm, so the post-login branches under test can run
     * as a plain unit test.
     */
    private static class AlwaysSucceedingKarafConfiguration extends Configuration {
        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            if (!"karaf".equals(name)) {
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
        private Subject subject;
        private CallbackHandler callbackHandler;

        @Override
        public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
            this.subject = subject;
            this.callbackHandler = callbackHandler;
        }

        @Override
        public boolean login() throws LoginException {
            try {
                NameCallback nameCallback = new NameCallback("name");
                PasswordCallback passwordCallback = new PasswordCallback("password", false);
                callbackHandler.handle(new Callback[]{nameCallback, passwordCallback});
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
