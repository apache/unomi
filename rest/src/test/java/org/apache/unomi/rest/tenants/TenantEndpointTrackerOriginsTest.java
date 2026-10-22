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

import org.apache.unomi.api.tenants.ApiKey;
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
        TenantRequest request = request(Map.of(PublicTrackerSettings.TENANT_PROPERTY_ALLOWED_ORIGINS, List.of("https://shop.example")));
        when(tenantService.createTenant(anyString(), any())).thenReturn(new Tenant());

        endpoint.createTenant(request);

        verify(publicTrackerSettings).tenantSaved(any());
    }

    @Test
    void invalidOriginsAreRejectedBeforeTheTenantIsCreated() {
        TenantRequest request = request(Map.of(PublicTrackerSettings.TENANT_PROPERTY_ALLOWED_ORIGINS, "https://shop.example/path, *"));

        WebApplicationException error = assertThrows(WebApplicationException.class, () -> endpoint.createTenant(request));

        assertEquals(400, error.getResponse().getStatus());
        assertTrue(String.valueOf(error.getResponse().getEntity()).contains("'*'"));
        verify(tenantService, never()).createTenant(anyString(), any());
        verify(publicTrackerSettings, never()).tenantSaved(any());
    }

    @Test
    void updatingOrDeletingATenantAppliesAtOnce() {
        Tenant tenant = new Tenant();
        tenant.setItemId("acme");
        tenant.setProperties(Map.of(PublicTrackerSettings.TENANT_PROPERTY_ALLOWED_ORIGINS, "https://blog.example"));
        Tenant saved = new Tenant();
        saved.setItemId("acme");
        when(tenantService.updateTenantSettings(tenant)).thenReturn(saved);
        when(tenantService.getTenant("acme")).thenReturn(saved);

        assertEquals(saved, endpoint.updateTenant("acme", tenant));
        endpoint.deleteTenant("acme");

        verify(tenantService, never()).saveTenant(any());
        verify(publicTrackerSettings).tenantSaved(saved);
        verify(publicTrackerSettings).tenantRemoved("acme");
    }

    @Test
    void invalidOriginsAreRejectedOnUpdate() {
        Tenant tenant = new Tenant();
        tenant.setItemId("acme");
        tenant.setProperties(Map.of(PublicTrackerSettings.TENANT_PROPERTY_ALLOWED_ORIGINS, 42));

        WebApplicationException error = assertThrows(WebApplicationException.class, () -> endpoint.updateTenant("acme", tenant));

        assertEquals(400, error.getResponse().getStatus());
        verify(tenantService, never()).updateTenantSettings(any());
    }

    @Test
    void updatingGoesThroughTheKeyPreservingServiceMethod() {
        // The service applies the settings onto the stored tenant with compare-and-set, keeping its keys.
        Tenant body = new Tenant();
        body.setItemId("acme");
        body.setApiKeys(List.of(new ApiKey()));

        when(tenantService.updateTenantSettings(body)).thenReturn(null);
        WebApplicationException missing = assertThrows(WebApplicationException.class, () -> endpoint.updateTenant("acme", body));
        assertEquals(404, missing.getResponse().getStatus());

        when(tenantService.updateTenantSettings(body)).thenThrow(new java.util.ConcurrentModificationException());
        WebApplicationException busy = assertThrows(WebApplicationException.class, () -> endpoint.updateTenant("acme", body));
        assertEquals(409, busy.getResponse().getStatus());

        verify(tenantService, never()).saveTenant(any());
        verify(publicTrackerSettings, never()).tenantSaved(any());
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
