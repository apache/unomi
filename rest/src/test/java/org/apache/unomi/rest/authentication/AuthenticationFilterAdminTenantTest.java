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

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        filter = new AuthenticationFilter(
                restAuthenticationConfig,
                tenantService,
                securityService,
                executionContextManager,
                mock(JAASAuthenticationFilter.class));
        requestContext = mock(ContainerRequestContext.class);
    }

    @Test
    void noHeaderUsesAdminDefaultTenant() {
        Tenant tenant = tenant("default");
        when(tenantService.getTenant("default")).thenReturn(tenant);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn(null);
        ExecutionContext context = new ExecutionContext("default", null, null);
        when(executionContextManager.createContext("default")).thenReturn(context);

        assertTrue(filter.applyAdminTenantContext(requestContext));
        verify(executionContextManager).setCurrentContext(context);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void missingDefaultTenantIsBadRequest() {
        when(tenantService.getTenant("default")).thenReturn(null);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn(null);

        assertFalse(filter.applyAdminTenantContext(requestContext));
        assertAborted(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void namedTenantIsUsed() {
        Tenant tenant = tenant("acme");
        when(tenantService.getTenant("acme")).thenReturn(tenant);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("acme");
        ExecutionContext context = new ExecutionContext("acme", null, null);
        when(executionContextManager.createContext("acme")).thenReturn(context);

        assertTrue(filter.applyAdminTenantContext(requestContext));
        verify(executionContextManager).setCurrentContext(context);
    }

    @Test
    void unknownTenantIsBadRequest() {
        when(tenantService.getTenant("missing")).thenReturn(null);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("missing");

        assertFalse(filter.applyAdminTenantContext(requestContext));
        assertAborted(Response.Status.BAD_REQUEST.getStatusCode());
        verify(executionContextManager, never()).setCurrentContext(any());
    }

    @Test
    void systemHeaderWithoutFullAdminIsForbidden() {
        when(securityService.hasRole(UnomiRoles.ADMINISTRATOR)).thenReturn(false);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("system");

        assertFalse(filter.applyAdminTenantContext(requestContext));
        assertAborted(Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    void systemHeaderWithFullAdminUsesSystemContext() {
        when(securityService.hasRole(UnomiRoles.ADMINISTRATOR)).thenReturn(true);
        when(requestContext.getHeaderString(TENANT_HEADER)).thenReturn("system");

        assertTrue(filter.applyAdminTenantContext(requestContext));
        ArgumentCaptor<ExecutionContext> context = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(executionContextManager).setCurrentContext(context.capture());
        assertTrue(context.getValue().isSystem());
        verify(tenantService, never()).getTenant("system");
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
