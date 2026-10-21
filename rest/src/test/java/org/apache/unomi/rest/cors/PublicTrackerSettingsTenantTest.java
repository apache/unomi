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
package org.apache.unomi.rest.cors;

import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tenants list their own website origins (UNOMI-1013): applied only to requests made with that tenant's key,
 * picked up without restarting or changing the operator configuration when tenants come and go.
 */
@ExtendWith(MockitoExtension.class)
class PublicTrackerSettingsTenantTest {

    private static final String SHOP = "https://shop.example";
    private static final String BLOG = "https://blog.example";

    @Mock private ConfigSharingService configSharingService;
    @Mock private TenantService tenantService;

    private final List<Tenant> tenants = new ArrayList<>();
    private final long[] now = {TimeUnit.HOURS.toNanos(1)};
    private PublicTrackerSettings settings;

    @BeforeEach
    void setUp() throws Exception {
        settings = new PublicTrackerSettings();
        set("configSharingService", configSharingService);
        set("tenantService", tenantService);
        settings.clock = () -> now[0];
        lenient().when(tenantService.getAllTenants()).thenAnswer(call -> new ArrayList<>(tenants));
        tenants.add(tenant("acme", SHOP));
        tenants.add(tenant("globex", null));
    }

    @Test
    void aTenantsOriginsApplyOnlyToThatTenant() {
        settings.activate();

        assertTrue(settings.policyForTenant("acme").allows(SHOP));
        assertFalse(settings.policyForTenant("globex").allows(SHOP));
        assertFalse(settings.policyForTenant(null).allows(SHOP));
        // A preflight carries no key: an origin any tenant lists passes, the actual request decides.
        assertTrue(settings.allowsPreflight(SHOP));
        assertFalse(settings.allowsPreflight("https://evil.example"));
    }

    @Test
    void theOperatorListStillAppliesToEveryTenant() {
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn(BLOG);
        settings.activate();

        assertTrue(settings.policyForTenant("acme").allows(BLOG));
        assertTrue(settings.policyForTenant("acme").allows(SHOP));
        assertTrue(settings.policyForTenant("globex").allows(BLOG));
        assertTrue(settings.allowsPreflight(BLOG));
    }

    @Test
    void theOperatorCanTurnTenantListsOff() {
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.TENANT_LISTS_PROPERTY)).thenReturn("false");
        settings.activate();

        assertFalse(settings.policyForTenant("acme").allows(SHOP));
        assertFalse(settings.allowsPreflight(SHOP));
    }

    @Test
    void aNewTenantWorksAtOnceWithoutRestart() {
        settings.activate();
        now[0] += TimeUnit.SECONDS.toNanos(PublicTrackerSettings.TENANT_RELOAD_MIN_SECONDS);
        // Created on another node: this node has not seen it yet.
        tenants.add(tenant("initech", BLOG));

        assertTrue(settings.allowsPreflight(BLOG));
        assertTrue(settings.policyForTenant("initech").allows(BLOG));
    }

    @Test
    void unknownOriginsCannotForceReloads() {
        settings.activate();
        clearInvocations(tenantService);
        now[0] += TimeUnit.SECONDS.toNanos(PublicTrackerSettings.TENANT_RELOAD_MIN_SECONDS);

        assertFalse(settings.allowsPreflight("https://random-1.example"));
        assertFalse(settings.allowsPreflight("https://random-2.example"));
        assertFalse(settings.policyForTenant("unknown-tenant").allows(SHOP));

        verify(tenantService, times(1)).getAllTenants();
    }

    @Test
    void theSnapshotIsRefreshedPeriodically() {
        settings.activate();
        tenants.clear();
        tenants.add(tenant("acme", null));

        // Still within the refresh interval: the known tenant keeps its origins for now.
        assertTrue(settings.policyForTenant("acme").allows(SHOP));

        now[0] += TimeUnit.SECONDS.toNanos(PublicTrackerSettings.TENANT_REFRESH_SECONDS);
        assertFalse(settings.policyForTenant("acme").allows(SHOP));
    }

    @Test
    void changesThroughThisNodeApplyImmediately() {
        settings.activate();
        tenants.clear();

        settings.tenantsChanged();
        assertFalse(settings.policyForTenant("acme").allows(SHOP));

        tenants.add(tenant("acme", SHOP));
        settings.onTenantRemoved("globex");
        assertTrue(settings.policyForTenant("acme").allows(SHOP));
    }

    @Test
    void aFailedLoadKeepsThePreviousOrigins() {
        settings.activate();
        when(tenantService.getAllTenants()).thenThrow(new IllegalStateException("search engine unavailable"));

        settings.tenantsChanged();

        assertTrue(settings.policyForTenant("acme").allows(SHOP));
    }

    @Test
    void sameSiteNoneIsAvailableOnceTenantsMayListOrigins() {
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.SAME_SITE_PROPERTY)).thenReturn("None");
        settings.activate();

        assertTrue(settings.profileCookie("p1", true).contains("SameSite=None; Secure"));
        assertTrue(settings.configurationWarnings().isEmpty(), String.valueOf(settings.configurationWarnings()));
    }

    @Test
    void anUnrecognisedSwitchValueKeepsTenantListsOnAndSaysSo() {
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.TENANT_LISTS_PROPERTY)).thenReturn("off");
        settings.activate();

        assertTrue(settings.policyForTenant("acme").allows(SHOP));
        assertTrue(settings.configurationWarnings().stream()
                .anyMatch(warning -> warning.contains("Unrecognised value 'off' for org.apache.unomi.public.cors.tenantOrigins")));
    }

    @Test
    void tenantsAreNotLoadedForEachRequest() {
        settings.activate();
        clearInvocations(tenantService);

        for (int i = 0; i < 100; i++) {
            settings.policyForTenant("acme");
            settings.allowsPreflight(SHOP);
        }

        verify(tenantService, never()).getAllTenants();
    }

    @Test
    void theTenantIsFoundFromTheResponseMessageToo() {
        // The authentication filter stores the security context on the request; a response filter's current
        // message is the response, which only reaches the request through the exchange.
        javax.security.auth.Subject subject = new javax.security.auth.Subject();
        subject.getPrincipals().add(new org.apache.unomi.api.security.TenantPrincipal("acme"));
        org.apache.cxf.message.Message request = new org.apache.cxf.message.MessageImpl();
        request.put(org.apache.cxf.security.SecurityContext.class,
                new org.apache.cxf.interceptor.security.RolePrefixSecurityContextImpl(subject, "ROLE_", "prefix"));
        org.apache.cxf.message.Message response = new org.apache.cxf.message.MessageImpl();
        org.apache.cxf.message.Exchange exchange = new org.apache.cxf.message.ExchangeImpl();
        exchange.setInMessage(request);
        exchange.setOutMessage(response);
        request.setExchange(exchange);
        response.setExchange(exchange);

        org.junit.jupiter.api.Assertions.assertEquals("acme", PublicTrackerSettings.tenantIdOf(request));
        org.junit.jupiter.api.Assertions.assertEquals("acme", PublicTrackerSettings.tenantIdOf(response));
        org.junit.jupiter.api.Assertions.assertNull(PublicTrackerSettings.tenantIdOf(new org.apache.cxf.message.MessageImpl()));
        org.junit.jupiter.api.Assertions.assertNull(PublicTrackerSettings.tenantIdOf(null));
    }

    private static Tenant tenant(String id, Object origins) {
        Tenant tenant = new Tenant();
        tenant.setItemId(id);
        Map<String, Object> properties = new HashMap<>();
        if (origins != null) {
            properties.put(PublicTrackerSettings.TENANT_ORIGINS_KEY, origins);
        }
        tenant.setProperties(properties);
        return tenant;
    }

    private void set(String name, Object value) throws Exception {
        Field field = PublicTrackerSettings.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(settings, value);
    }
}
