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
package org.apache.unomi.services.impl.tenants;

import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.tenants.TenantUsage;
import org.apache.unomi.api.tenants.TenantUsageService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantUsageServiceImplTest {

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private DefinitionsService definitionsService;

    @Mock
    private TenantService tenantService;

    private TenantUsageServiceImpl tenantUsageService;

    @BeforeEach
    void setUp() {
        tenantUsageService = new TenantUsageServiceImpl();
        tenantUsageService.setPersistenceService(persistenceService);
        tenantUsageService.setDefinitionsService(definitionsService);
        tenantUsageService.setTenantService(tenantService);
    }

    @Test
    void getUsageReturnsNullForMissingTenant() {
        when(tenantService.getTenant("missing")).thenReturn(null);

        assertNull(tenantUsageService.getUsage("missing", TenantUsageService.DEFAULT_PERIOD));
    }

    @Test
    void getUsageRejectsUnsupportedPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> tenantUsageService.getUsage("tenant-a", "7d"));
    }

    @Test
    void getUsageRefreshesOnDemandWhenCacheEmpty() {
        Tenant tenant = new Tenant();
        tenant.setItemId("tenant-a");
        when(tenantService.getTenant("tenant-a")).thenReturn(tenant);

        ConditionType profileConditionType = new ConditionType();
        profileConditionType.setItemId("profilePropertyCondition");
        ConditionType eventConditionType = new ConditionType();
        eventConditionType.setItemId("eventPropertyCondition");
        when(definitionsService.getConditionType("profilePropertyCondition")).thenReturn(profileConditionType);
        when(definitionsService.getConditionType("eventPropertyCondition")).thenReturn(eventConditionType);
        when(persistenceService.queryCount(any(), eq("profile"))).thenReturn(12L);
        when(persistenceService.queryCount(any(), eq("event"))).thenReturn(34L);
        when(persistenceService.getAllItemsCount(eq("segment"), eq("tenant-a"))).thenReturn(3L);
        when(persistenceService.getAllItemsCount(eq("rule"), eq("tenant-a"))).thenReturn(5L);
        when(persistenceService.calculateStorageSize("tenant-a")).thenReturn(99L);

        TenantUsage usage = tenantUsageService.getUsage("tenant-a", TenantUsageService.DEFAULT_PERIOD);

        assertNotNull(usage);
        assertEquals("tenant-a", usage.getTenantId());
        assertEquals(TenantUsageService.DEFAULT_PERIOD, usage.getPeriod());
        assertEquals(12L, usage.getProfileCount());
        assertEquals(34L, usage.getEventCount());
        assertEquals(3L, usage.getSegmentCount());
        assertEquals(5L, usage.getRuleCount());
        assertEquals(99L, usage.getStorageDocumentCount());
    }

    @Test
    void recordRestRequestIncrementsCounter() {
        tenantUsageService.recordRestRequest("tenant-a");
        tenantUsageService.recordRestRequest("tenant-a");

        Tenant tenant = new Tenant();
        tenant.setItemId("tenant-a");
        when(tenantService.getTenant("tenant-a")).thenReturn(tenant);

        ConditionType profileConditionType = new ConditionType();
        profileConditionType.setItemId("profilePropertyCondition");
        ConditionType eventConditionType = new ConditionType();
        eventConditionType.setItemId("eventPropertyCondition");
        when(definitionsService.getConditionType("profilePropertyCondition")).thenReturn(profileConditionType);
        when(definitionsService.getConditionType("eventPropertyCondition")).thenReturn(eventConditionType);
        when(persistenceService.queryCount(any(), eq("profile"))).thenReturn(0L);
        when(persistenceService.queryCount(any(), eq("event"))).thenReturn(0L);
        when(persistenceService.getAllItemsCount(eq("segment"), eq("tenant-a"))).thenReturn(0L);
        when(persistenceService.getAllItemsCount(eq("rule"), eq("tenant-a"))).thenReturn(0L);
        when(persistenceService.calculateStorageSize("tenant-a")).thenReturn(0L);

        TenantUsage usage = tenantUsageService.getUsage("tenant-a", TenantUsageService.DEFAULT_PERIOD);

        assertEquals(2L, usage.getRestRequestCount());
    }

    @Test
    void refreshTenantUsageUsesTenantScopedCounts() throws Exception {
        ConditionType profileConditionType = new ConditionType();
        profileConditionType.setItemId("profilePropertyCondition");
        ConditionType eventConditionType = new ConditionType();
        eventConditionType.setItemId("eventPropertyCondition");

        when(persistenceService.queryCount(any(), eq("profile"))).thenReturn(10L);
        when(persistenceService.queryCount(any(), eq("event"))).thenReturn(20L);
        when(persistenceService.getAllItemsCount(eq("segment"), eq("tenant-a"))).thenReturn(1L);
        when(persistenceService.getAllItemsCount(eq("rule"), eq("tenant-a"))).thenReturn(2L);
        when(persistenceService.calculateStorageSize("tenant-a")).thenReturn(50L);

        Method refreshMethod = TenantUsageServiceImpl.class.getDeclaredMethod(
                "refreshTenantUsage", String.class, ConditionType.class, ConditionType.class);
        refreshMethod.setAccessible(true);
        refreshMethod.invoke(tenantUsageService, "tenant-a", profileConditionType, eventConditionType);

        Tenant tenant = new Tenant();
        tenant.setItemId("tenant-a");
        when(tenantService.getTenant("tenant-a")).thenReturn(tenant);

        TenantUsage usage = tenantUsageService.getUsage("tenant-a", TenantUsageService.DEFAULT_PERIOD);

        assertEquals(10L, usage.getProfileCount());
        assertEquals(20L, usage.getEventCount());
        assertEquals(1L, usage.getSegmentCount());
        assertEquals(2L, usage.getRuleCount());
        assertEquals(50L, usage.getStorageDocumentCount());
    }
}
