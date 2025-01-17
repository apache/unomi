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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.services.impl.events.EventServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class EventServiceImplTest {

    private EventServiceImpl eventService;

    @Mock
    private TenantService tenantService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        eventService = new EventServiceImpl();
        eventService.setTenantService(tenantService);
        eventService.setRestrictedEventTypeIds(new HashSet<>(Arrays.asList("test1", "test2", "test3", "test4")));
    }

    @Test
    public void testEventAllowed() {
        String tenantId = "test_tenant";
        String sourceIP = "127.0.0.1";

        // Create test tenant with permissions
        Tenant tenant = new Tenant();
        tenant.setItemId(tenantId);
        Set<String> permissions = new HashSet<>(Arrays.asList("test1", "test2", "test4"));
        tenant.setRestrictedEventPermissions(permissions);
        Set<String> ips = new HashSet<>(Arrays.asList(sourceIP));
        tenant.setAuthorizedIPs(ips);
        when(tenantService.getTenant(tenantId)).thenReturn(tenant);

        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null), tenantId, sourceIP));
        assertTrue(eventService.isEventAllowedForTenant(new Event("test2", null, new Profile(), null, null, null, null), tenantId, sourceIP));
        assertTrue(eventService.isEventAllowedForTenant(new Event("test4", null, new Profile(), null, null, null, null), tenantId, sourceIP));

        // Test unauthorized event type
        assertFalse(eventService.isEventAllowedForTenant(new Event("test3", null, new Profile(), null, null, null, null), tenantId, sourceIP));

        // Test unauthorized IP
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null), tenantId, "192.168.1.1"));

        // Test invalid tenant
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null), "invalid_tenant", sourceIP));
    }

    @Test
    public void testIPv6EventAllowed() {
        String tenantId = "test_tenant";

        // Create test tenant with IPv6 permissions
        Tenant tenant = new Tenant();
        tenant.setItemId(tenantId);
        Set<String> permissions = new HashSet<>(Arrays.asList("test1", "test2", "test4"));
        tenant.setRestrictedEventPermissions(permissions);
        Set<String> ips = new HashSet<>(Arrays.asList(
            "2001:db8::/32",                  // IPv6 CIDR block
            "::1",                            // IPv6 localhost
            "2001:db8::1",                    // Single IPv6 address
            "2001:db8:3:4:5:6:7:8"           // Full IPv6 address
        ));
        tenant.setAuthorizedIPs(ips);
        when(tenantService.getTenant(tenantId)).thenReturn(tenant);

        // Test IPv6 addresses with square brackets (as returned by HttpServletRequest.getRemoteAddr)
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "[2001:db8::1]"));
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "[2001:db8:1:2:3:4:5:6]"));
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "[::1]"));

        // Test IPv6 addresses without square brackets
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "2001:db8::1"));
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "2001:db8:3:4:5:6:7:8"));
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "::1"));

        // Test unauthorized IPv6 addresses
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "[2001:db9::1]"));  // Different prefix
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "2001:db9::1"));    // Different prefix without brackets
    }

    @Test
    public void testMixedIPv4AndIPv6EventAllowed() {
        String tenantId = "test_tenant";

        // Create test tenant with mixed IPv4 and IPv6 permissions
        Tenant tenant = new Tenant();
        tenant.setItemId(tenantId);
        Set<String> permissions = new HashSet<>(Arrays.asList("test1"));
        tenant.setRestrictedEventPermissions(permissions);
        Set<String> ips = new HashSet<>(Arrays.asList(
            "127.0.0.1",                      // IPv4 localhost
            "192.168.1.0/24",                 // IPv4 CIDR block
            "2001:db8::/32",                  // IPv6 CIDR block
            "::1"                             // IPv6 localhost
        ));
        tenant.setAuthorizedIPs(ips);
        when(tenantService.getTenant(tenantId)).thenReturn(tenant);

        // Test IPv4 addresses
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "127.0.0.1"));
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "192.168.1.100"));

        // Test IPv6 addresses with and without brackets
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "[::1]"));
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "::1"));
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "[2001:db8::1]"));
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "2001:db8::1"));

        // Test unauthorized IPs
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "192.168.2.1"));        // Outside IPv4 range
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null),
            tenantId, "[2001:db9::1]"));      // Outside IPv6 range
    }

    @Test
    public void testEventAllowedAfterConfigurationChange() {
        String tenantId = "test_tenant";
        String sourceIP = "127.0.0.1";

        // Create test tenant with initial permissions
        Tenant tenant = new Tenant();
        tenant.setItemId(tenantId);
        Set<String> permissions = new HashSet<>(Arrays.asList("test4"));
        tenant.setRestrictedEventPermissions(permissions);
        Set<String> ips = new HashSet<>(Arrays.asList(sourceIP));
        tenant.setAuthorizedIPs(ips);
        when(tenantService.getTenant(tenantId)).thenReturn(tenant);

        assertTrue(eventService.isEventAllowedForTenant(new Event("test4", null, new Profile(), null, null, null, null), tenantId, sourceIP));
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, null), tenantId, sourceIP));
        assertFalse(eventService.isEventAllowedForTenant(new Event("test2", null, new Profile(), null, null, null, null), tenantId, sourceIP));
        assertFalse(eventService.isEventAllowedForTenant(new Event("test3", null, new Profile(), null, null, null, null), tenantId, sourceIP));
    }
}
