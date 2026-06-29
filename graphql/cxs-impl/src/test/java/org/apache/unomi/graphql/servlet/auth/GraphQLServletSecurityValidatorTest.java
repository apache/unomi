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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the JAAS-authenticated tenant resolution branch of {@link GraphQLServletSecurityValidator},
 * in particular the fallback to {@link ExecutionContext#systemContext()} when the
 * {@code X-Unomi-Tenant-Id} header does not resolve to a known tenant (UNOMI-884).
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

    @Test
    void validate_withInvalidTenantHeader_fallsBackToSystemContext() throws IOException {
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(request.getHeader(TENANT_HEADER)).thenReturn("not-a-real-tenant");
        when(tenantService.getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PRIVATE))).thenReturn(null);
        when(tenantService.getTenant("not-a-real-tenant")).thenReturn(null);

        boolean authenticated = validator.validate(null, null, request, response);

        assertTrue(authenticated);
        verify(executionContextManager).setCurrentContext(refEq(ExecutionContext.systemContext()));
        verify(response, never()).sendError(any(Integer.class));
    }

    @Test
    void validate_withValidTenantHeader_createsTenantContext() throws IOException {
        Tenant tenant = new Tenant();
        tenant.setItemId("known-tenant");

        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(request.getHeader(TENANT_HEADER)).thenReturn("known-tenant");
        when(tenantService.getTenantByApiKey(any(), eq(ApiKey.ApiKeyType.PRIVATE))).thenReturn(null);
        when(tenantService.getTenant("known-tenant")).thenReturn(tenant);
        ExecutionContext tenantContext = new ExecutionContext("known-tenant", null, null);
        when(executionContextManager.createContext("known-tenant")).thenReturn(tenantContext);

        boolean authenticated = validator.validate(null, null, request, response);

        assertTrue(authenticated);
        verify(executionContextManager).createContext("known-tenant");
        verify(executionContextManager).setCurrentContext(tenantContext);
        verify(executionContextManager, never()).setCurrentContext(refEq(ExecutionContext.systemContext()));
    }

    @Test
    void validate_withoutAuthorizationHeader_isRejected() throws IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean authenticated = validator.validate(null, null, request, response);

        assertFalse(authenticated);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
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
