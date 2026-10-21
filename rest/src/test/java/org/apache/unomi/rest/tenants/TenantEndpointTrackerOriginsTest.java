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
package org.apache.unomi.rest.tenants;

import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.rest.cors.PublicTrackerSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.WebApplicationException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A tenant's website origins are set through the tenant API when tenants are provisioned (UNOMI-1013).
 */
@ExtendWith(MockitoExtension.class)
class TenantEndpointTrackerOriginsTest {

    @Mock private TenantService tenantService;
    @Mock private PublicTrackerSettings publicTrackerSettings;

    private TenantEndpoint endpoint;

    @BeforeEach
    void setUp() throws Exception {
        endpoint = new TenantEndpoint();
        set("tenantService", tenantService);
        set("publicTrackerSettings", publicTrackerSettings);
    }

    @Test
    void creatingATenantWithItsOriginsAppliesThemAtOnce() {
        TenantRequest request = request(Map.of(PublicTrackerSettings.TENANT_ORIGINS_KEY, List.of("https://shop.example")));
        when(tenantService.createTenant(anyString(), any())).thenReturn(new Tenant());

        endpoint.createTenant(request);

        verify(publicTrackerSettings).tenantsChanged();
    }

    @Test
    void invalidOriginsAreRejectedBeforeTheTenantIsCreated() {
        TenantRequest request = request(Map.of(PublicTrackerSettings.TENANT_ORIGINS_KEY, "https://shop.example/path, *"));

        WebApplicationException error = assertThrows(WebApplicationException.class, () -> endpoint.createTenant(request));

        assertEquals(400, error.getResponse().getStatus());
        assertTrue(String.valueOf(error.getResponse().getEntity()).contains("'*'"));
        verify(tenantService, never()).createTenant(anyString(), any());
        verify(publicTrackerSettings, never()).tenantsChanged();
    }

    @Test
    void updatingOrDeletingATenantAppliesAtOnce() {
        Tenant tenant = new Tenant();
        tenant.setItemId("acme");
        tenant.setProperties(Map.of(PublicTrackerSettings.TENANT_ORIGINS_KEY, "https://blog.example"));
        when(tenantService.getTenant("acme")).thenReturn(tenant);

        endpoint.updateTenant("acme", tenant);
        endpoint.deleteTenant("acme");

        verify(tenantService).saveTenant(tenant);
        verify(tenantService).deleteTenant("acme");
        verify(publicTrackerSettings, org.mockito.Mockito.times(2)).tenantsChanged();
    }

    @Test
    void invalidOriginsAreRejectedOnUpdate() {
        Tenant tenant = new Tenant();
        tenant.setItemId("acme");
        tenant.setProperties(Map.of(PublicTrackerSettings.TENANT_ORIGINS_KEY, 42));
        when(tenantService.getTenant("acme")).thenReturn(tenant);

        WebApplicationException error = assertThrows(WebApplicationException.class, () -> endpoint.updateTenant("acme", tenant));

        assertEquals(400, error.getResponse().getStatus());
        verify(tenantService, never()).saveTenant(any());
    }

    private static TenantRequest request(Map<String, Object> properties) {
        TenantRequest request = new TenantRequest();
        request.setRequestedId("acme");
        request.setProperties(properties);
        return request;
    }

    private void set(String name, Object value) throws Exception {
        Field field = TenantEndpoint.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(endpoint, value);
    }
}
