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
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.AdminTenantConfig;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.security.auth.Subject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UNOMI-1008: after JAAS succeeds, the filter picks the tenant from {@code X-Unomi-Tenant-Id}
 * or from the configured admin default, and refuses unknown or forbidden selections.
 */
class AuthenticationFilterAdminTenantTest {

    private static final String TENANT_HEADER = "X-Unomi-Tenant-Id";

    private RestAuthenticationConfig restAuthenticationConfig;
    private TenantService tenantService;
    private SecurityService securityService;
    private ExecutionContextManager executionContextManager;
    private JAASAuthenticationFilter jaasAuthenticationFilter;
    /** Subject the stubbed JAAS login yields; {@code null} means JAAS refused the credential. */
    private Subject jaasSubject;
    private AuthenticationFilter filter;
    private ContainerRequestContext requestContext;

    @BeforeEach
    void setUp() {
        restAuthenticationConfig = mock(RestAuthenticationConfig.class);
        tenantService = mock(TenantService.class);
        securityService = mock(SecurityService.class);
        executionContextManager = mock(ExecutionContextManager.class);
        when(restAuthenticationConfig.getAdminDefaultTenantId())
                .thenReturn(AdminTenantConfig.DEFAULT_ADMIN_TENANT_ID);
        // JAAS administrators hold ROLE_UNOMI_ADMIN or ROLE_UNOMI_TENANT_ADMIN.
        when(securityService.hasSystemAccess()).thenReturn(true);
        jaasAuthenticationFilter = mock(JAASAuthenticationFilter.class);
        jaasSubject = new Subject();
        filter = new AuthenticationFilter(
                restAuthenticationConfig,
                tenantService,
                securityService,
                executionContextManager,
                jaasAuthenticationFilter) {
            @Override
            Subject authenticatedJaasSubject() {
                return jaasSubject;
            }
        };
        requestContext = mock(ContainerRequestContext.class);
    }

    @Test
    void noHeaderUsesAdminDefaultTenant() {
        Tenant tenant = tenant("default");
        when(tenantService.getTenant("default")).thenReturn(tenant);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn(null);
        ExecutionContext context = new ExecutionContext("default", null, null);
        when(executionContextManager.createContext("default")).thenReturn(context);

        assertTrue(filter.applyAdminTenantContext(requestContext, false));
        verify(executionContextManager).setCurrentContext(context);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void missingDefaultTenantIsBadRequest() {
        when(tenantService.getTenant("default")).thenReturn(null);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn(null);

        assertFalse(filter.applyAdminTenantContext(requestContext, false));
        assertAborted(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void namedTenantIsUsed() {
        Tenant tenant = tenant("acme");
        when(tenantService.getTenant("acme")).thenReturn(tenant);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("acme");
        ExecutionContext context = new ExecutionContext("acme", null, null);
        when(executionContextManager.createContext("acme")).thenReturn(context);

        assertTrue(filter.applyAdminTenantContext(requestContext, false));
        verify(executionContextManager).setCurrentContext(context);
    }

    @Test
    void unknownTenantIsBadRequest() {
        when(tenantService.getTenant("missing")).thenReturn(null);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("missing");

        assertFalse(filter.applyAdminTenantContext(requestContext, false));
        assertAborted(Response.Status.BAD_REQUEST.getStatusCode());
        verify(executionContextManager, never()).setCurrentContext(any());
    }

    @Test
    void unknownTenantWithoutAdministratorRoleIsForbiddenLikeAnExistingOne() {
        // A realm account without a Unomi administrator role must not learn which tenants exist.
        when(securityService.hasSystemAccess()).thenReturn(false);
        when(tenantService.getTenant("missing")).thenReturn(null);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("missing");

        assertFalse(filter.applyAdminTenantContext(requestContext, false));
        assertAborted(Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    void errorBodyIsPlainText() {
        when(tenantService.getTenant("<b>x</b>")).thenReturn(null);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("<b>x</b>");

        assertFalse(filter.applyAdminTenantContext(requestContext, false));
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, captor.getValue().getMediaType());
    }

    @Test
    void systemHeaderWithoutFullAdminIsForbidden() {
        when(securityService.hasRole(UnomiRoles.ADMINISTRATOR)).thenReturn(false);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("system");

        assertFalse(filter.applyAdminTenantContext(requestContext, false));
        assertAborted(Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    void systemHeaderWithFullAdminUsesSystemContext() {
        when(securityService.hasRole(UnomiRoles.ADMINISTRATOR)).thenReturn(true);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("system");

        assertTrue(filter.applyAdminTenantContext(requestContext, false));
        ArgumentCaptor<ExecutionContext> context = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(executionContextManager).setCurrentContext(context.capture());
        assertTrue(context.getValue().isSystem());
        verify(tenantService, never()).getTenant("system");
    }

    @Test
    void tenantAdministrationWithoutHeaderUsesSystemContextBeforeAnyTenantExists() {
        // Fresh install: /cxs/tenants must work so the first tenant can be created.
        when(securityService.hasRole(UnomiRoles.ADMINISTRATOR)).thenReturn(true);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn(null);

        assertTrue(filter.applyAdminTenantContext(requestContext, true));
        ArgumentCaptor<ExecutionContext> context = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(executionContextManager).setCurrentContext(context.capture());
        assertTrue(context.getValue().isSystem());
        verify(tenantService, never()).getTenant(any());
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void tenantAdministrationWithoutFullAdminFollowsTenantRules() {
        when(securityService.hasRole(UnomiRoles.ADMINISTRATOR)).thenReturn(false);
        when(tenantService.getTenant("default")).thenReturn(null);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn(null);

        assertFalse(filter.applyAdminTenantContext(requestContext, true));
        assertAborted(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void tenantAdministrationHonoursExplicitHeader() {
        when(securityService.hasRole(UnomiRoles.ADMINISTRATOR)).thenReturn(true);
        when(tenantService.getTenant("acme")).thenReturn(tenant("acme"));
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("acme");
        ExecutionContext context = new ExecutionContext("acme", null, null);
        when(executionContextManager.createContext("acme")).thenReturn(context);

        assertTrue(filter.applyAdminTenantContext(requestContext, true));
        verify(executionContextManager).setCurrentContext(context);
    }

    // ------------------------------------------------------------------ filter() wiring

    /** Fresh install: tenant administration must reach the system context before any tenant exists. */
    @Test
    void filterGivesTenantAdministrationTheSystemContextWithoutAnyTenant() throws IOException {
        when(securityService.hasRole(UnomiRoles.ADMINISTRATOR)).thenReturn(true);
        givenJaasRequest("tenants");

        filter.filter(requestContext);

        ArgumentCaptor<ExecutionContext> context = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(executionContextManager).setCurrentContext(context.capture());
        assertTrue(context.getValue().isSystem());
        verify(requestContext, never()).abortWith(any());
    }

    /** Other private paths use the admin default tenant, never the system context, without a header. */
    @Test
    void filterRefusesPrivatePathWhenAdminDefaultTenantIsMissing() throws IOException {
        when(securityService.hasRole(UnomiRoles.ADMINISTRATOR)).thenReturn(true);
        when(restAuthenticationConfig.getPublicPathPatterns()).thenReturn(Collections.emptyList());
        when(tenantService.getTenant("default")).thenReturn(null);
        givenJaasRequest("profiles");

        filter.filter(requestContext);

        assertAborted(Response.Status.BAD_REQUEST.getStatusCode());
        verify(executionContextManager, never()).setCurrentContext(any());
    }

    /** Wrong credentials answer 401 before any tenant is looked up, whatever the tenant header says. */
    @Test
    void filterAnswersWrongCredentialsWith401WithoutLookingUpTheTenant() throws IOException {
        // CXF's JAAS filter refuses a credential by aborting with 401 and returning normally.
        jaasSubject = null;
        doAnswer(invocation -> {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            return null;
        }).when(jaasAuthenticationFilter).filter(requestContext);
        when(restAuthenticationConfig.getPublicPathPatterns()).thenReturn(Collections.emptyList());
        givenJaasRequest("profiles");
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("missing");

        filter.filter(requestContext);

        assertAborted(Response.Status.UNAUTHORIZED.getStatusCode());
        verify(tenantService, never()).getTenant(any());
    }

    private void givenJaasRequest(String path) {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("GET");
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Basic "
                + Base64.getEncoder().encodeToString("karaf:a-strong-password".getBytes(StandardCharsets.UTF_8)));
    }

    private Tenant tenant(String id) {
        Tenant tenant = new Tenant();
        tenant.setItemId(id);
        return tenant;
    }

    private void assertAborted(int status) {
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(status, captor.getValue().getStatus());
    }
}
