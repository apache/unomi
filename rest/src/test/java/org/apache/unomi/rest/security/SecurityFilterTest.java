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
package org.apache.unomi.rest.security;

import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.tenants.TenantUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    @Mock
    private SecurityService securityService;

    @Mock
    private TenantUsageService tenantUsageService;

    @Mock
    private ResourceInfo resourceInfo;

    @Mock
    private UriInfo uriInfo;

    @Mock
    private ContainerRequestContext requestContext;

    private SecurityFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new SecurityFilter();
        setField(filter, "securityService", securityService);
        setField(filter, "tenantUsageService", tenantUsageService);
        setField(filter, "resourceInfo", resourceInfo);
        setField(filter, "uriInfo", uriInfo);
    }

    @Test
    void requiresTenantChecksPathTenantId() throws Exception {
        Method method = TenantScopedResource.class.getMethod("tenantOperation");
        when(resourceInfo.getResourceMethod()).thenReturn(method);
        MultivaluedHashMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.add(SecurityFilter.TENANT_PATH_PARAM, "requested-tenant");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(securityService.hasTenantAccess("requested-tenant")).thenReturn(false);

        filter.filter(requestContext);

        verify(securityService).hasTenantAccess("requested-tenant");
        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), responseCaptor.getValue().getStatus());
    }

    @Test
    void requiresTenantRejectsMissingPathTenantId() throws Exception {
        Method method = TenantScopedResource.class.getMethod("tenantOperation");
        when(resourceInfo.getResourceMethod()).thenReturn(method);
        when(uriInfo.getPathParameters()).thenReturn(new MultivaluedHashMap<>());

        filter.filter(requestContext);

        verify(securityService, never()).hasTenantAccess(any());
        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), responseCaptor.getValue().getStatus());
    }

    @Test
    void requiresRoleResolvesClassLevelAnnotation() throws Exception {
        Method method = ClassScopedRoleResource.class.getMethod("anyOperation");
        when(resourceInfo.getResourceMethod()).thenReturn(method);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        filter.filter(requestContext);

        verify(securityService).hasRole("ROLE_ADMIN");
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void resolveAnnotationPrefersMethodOverClass() throws Exception {
        Method method = ClassScopedRoleResource.class.getMethod("overriddenOperation");
        RequiresRole resolved = SecurityFilter.resolveAnnotation(method, RequiresRole.class);
        assertEquals("ROLE_TENANT", resolved.value()[0]);
    }

    @Test
    void recordsRequestAgainstPathTenantForTenantScopedEndpoint() throws Exception {
        Method method = TenantScopedResource.class.getMethod("tenantOperation");
        when(resourceInfo.getResourceMethod()).thenReturn(method);
        MultivaluedHashMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.add(SecurityFilter.TENANT_PATH_PARAM, "path-tenant");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(securityService.hasTenantAccess("path-tenant")).thenReturn(true);

        filter.filter(requestContext);

        verify(tenantUsageService).recordRestRequest("path-tenant");
        verify(securityService, never()).getCurrentSubjectTenantId();
    }

    @Test
    void recordsRequestAgainstSubjectTenantForNonTenantScopedEndpoint() throws Exception {
        Method method = UnscopedResource.class.getMethod("anyOperation");
        when(resourceInfo.getResourceMethod()).thenReturn(method);
        when(securityService.isOperatingOnSystemTenant()).thenReturn(false);
        when(securityService.getCurrentSubjectTenantId()).thenReturn("subject-tenant");

        filter.filter(requestContext);

        verify(tenantUsageService).recordRestRequest("subject-tenant");
    }

    @Test
    void doesNotRecordRequestWhenOperatingOnSystemTenant() throws Exception {
        Method method = UnscopedResource.class.getMethod("anyOperation");
        when(resourceInfo.getResourceMethod()).thenReturn(method);
        when(securityService.isOperatingOnSystemTenant()).thenReturn(true);

        filter.filter(requestContext);

        verify(securityService, never()).getCurrentSubjectTenantId();
        verify(tenantUsageService, never()).recordRestRequest(any());
    }

    static class TenantScopedResource {
        @RequiresTenant
        public void tenantOperation() {
        }
    }

    static class UnscopedResource {
        public void anyOperation() {
        }
    }

    @RequiresRole("ROLE_ADMIN")
    static class ClassScopedRoleResource {
        public void anyOperation() {
        }

        @RequiresRole("ROLE_TENANT")
        public void overriddenOperation() {
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
