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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.tenants.TenantEventPurgeResult;
import org.apache.unomi.api.tenants.TenantUsage;
import org.apache.unomi.api.tenants.TenantUsageService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.BaseAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantUsageServiceImplTest {

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private DefinitionsService definitionsService;

    @Mock
    private TenantService tenantService;

    @Mock
    private ExecutionContextManager contextManager;

    private TenantUsageServiceImpl tenantUsageService;

    private ConditionType profilePropertyConditionType;
    private ConditionType eventPropertyConditionType;
    private ConditionType booleanConditionType;

    @BeforeEach
    void setUp() {
        tenantUsageService = new TenantUsageServiceImpl();
        tenantUsageService.setPersistenceService(persistenceService);
        tenantUsageService.setDefinitionsService(definitionsService);
        tenantUsageService.setTenantService(tenantService);
        tenantUsageService.setContextManager(contextManager);

        profilePropertyConditionType = new ConditionType();
        profilePropertyConditionType.setItemId("profilePropertyCondition");
        eventPropertyConditionType = new ConditionType();
        eventPropertyConditionType.setItemId("eventPropertyCondition");
        booleanConditionType = new ConditionType();
        booleanConditionType.setItemId("booleanCondition");
    }

    @Test
    void resolvePeriodAcceptsCurrentMonthAndLegacyAlias() {
        TenantUsageServiceImpl.UsagePeriod current = TenantUsageServiceImpl.resolvePeriod("current-month");
        assertEquals(YearMonth.now(ZoneOffset.UTC).toString(), current.getLabel());
        assertTrue(current.getEndMillis() > current.getStartMillis());

        TenantUsageServiceImpl.UsagePeriod legacy = TenantUsageServiceImpl.resolvePeriod("24h");
        assertEquals(current.getLabel(), legacy.getLabel());
    }

    @Test
    void resolvePeriodAcceptsYearMonth() {
        TenantUsageServiceImpl.UsagePeriod period = TenantUsageServiceImpl.resolvePeriod("2026-03");
        assertEquals("2026-03", period.getLabel());
    }

    @Test
    void resolvePeriodRejectsUnsupportedValue() {
        assertThrows(IllegalArgumentException.class, () -> TenantUsageServiceImpl.resolvePeriod("7d"));
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
        Tenant tenant = tenantWithId("tenant-a");
        when(tenantService.getTenant("tenant-a")).thenReturn(tenant);
        stubConditionTypes();
        stubRefreshCounts("tenant-a", 12L, 34L, 2L, 3L, 5L, 99L);

        TenantUsage usage = tenantUsageService.getUsage("tenant-a", TenantUsageService.DEFAULT_PERIOD);

        assertNotNull(usage);
        assertEquals("tenant-a", usage.getTenantId());
        assertEquals(YearMonth.now(ZoneOffset.UTC).toString(), usage.getPeriod());
        assertTrue(usage.getPeriodEnd() > usage.getPeriodStart());
        assertEquals(12L, usage.getProfileCount());
        assertEquals(34L, usage.getEventCount());
        assertEquals(2L, usage.getScopeCount());
        assertEquals(3L, usage.getSegmentCount());
        assertEquals(5L, usage.getRuleCount());
        assertEquals(99L, usage.getStorageDocumentCount());
        assertEquals(2L, usage.getActiveApiKeyCount());
        assertNotNull(usage.getScopeUsages());
    }

    @Test
    void recordRestRequestIncrementsCounter() {
        tenantUsageService.recordRestRequest("tenant-a");
        tenantUsageService.recordRestRequest("tenant-a");

        Tenant tenant = tenantWithId("tenant-a");
        when(tenantService.getTenant("tenant-a")).thenReturn(tenant);
        stubConditionTypes();
        stubRefreshCounts("tenant-a", 0L, 0L, 0L, 0L, 0L, 0L);

        TenantUsage usage = tenantUsageService.getUsage("tenant-a", TenantUsageService.DEFAULT_PERIOD);

        assertEquals(2L, usage.getRestRequestCount());
    }

    @Test
    void purgeEventsOlderThanReturnsNullForMissingTenant() {
        when(tenantService.getTenant("missing")).thenReturn(null);

        assertNull(tenantUsageService.purgeEventsOlderThan("missing", 30));
    }

    @Test
    void purgeEventsOlderThanRejectsRetentionBelowMinimum() {
        when(tenantService.getTenant("tenant-a")).thenReturn(tenantWithId("tenant-a"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tenantUsageService.purgeEventsOlderThan("tenant-a", 3));
        assertTrue(ex.getMessage().contains(String.valueOf(TenantUsageService.MIN_EVENT_RETENTION_DAYS)));
    }

    @Test
    void purgeEventsOlderThanCountsAndDeletesUnderTenantContext() {
        when(tenantService.getTenant("tenant-a")).thenReturn(tenantWithId("tenant-a"));
        when(definitionsService.getConditionType("eventPropertyCondition")).thenReturn(eventPropertyConditionType);
        when(contextManager.executeAsSystem(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(contextManager.executeAsTenant(eq("tenant-a"), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        when(persistenceService.queryCount(any(), eq(Event.ITEM_TYPE))).thenReturn(42L);
        when(persistenceService.removeByQuery(any(), eq(Event.class))).thenReturn(true);

        TenantEventPurgeResult result = tenantUsageService.purgeEventsOlderThan("tenant-a", 90);

        assertNotNull(result);
        assertEquals("tenant-a", result.getTenantId());
        assertEquals(90, result.getRetentionDays());
        assertEquals(42L, result.getEventsMatched());
        assertTrue(result.isPurgeRequested());
        verify(persistenceService).removeByQuery(any(), eq(Event.class));
    }

    @Test
    void purgeEventsOlderThanReturnsFalseWhenDeleteNotAccepted() {
        when(tenantService.getTenant("tenant-a")).thenReturn(tenantWithId("tenant-a"));
        when(definitionsService.getConditionType("eventPropertyCondition")).thenReturn(eventPropertyConditionType);
        when(contextManager.executeAsSystem(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(contextManager.executeAsTenant(eq("tenant-a"), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        when(persistenceService.queryCount(any(), eq(Event.ITEM_TYPE))).thenReturn(0L);
        when(persistenceService.removeByQuery(any(), eq(Event.class))).thenReturn(false);

        TenantEventPurgeResult result = tenantUsageService.purgeEventsOlderThan("tenant-a", 30);

        assertNotNull(result);
        assertFalse(result.isPurgeRequested());
    }


    @Test
    void resolvePeriodNullAndBlankDefaultToCurrentMonth() {
        TenantUsageServiceImpl.UsagePeriod fromNull = TenantUsageServiceImpl.resolvePeriod(null);
        TenantUsageServiceImpl.UsagePeriod fromBlank = TenantUsageServiceImpl.resolvePeriod("   ");
        assertEquals(YearMonth.now(ZoneOffset.UTC).toString(), fromNull.getLabel());
        assertEquals(fromNull.getLabel(), fromBlank.getLabel());
    }

    @Test
    void resolvePeriodYearMonthComputesUtcBounds() {
        TenantUsageServiceImpl.UsagePeriod period = TenantUsageServiceImpl.resolvePeriod("2026-03");
        assertEquals("2026-03", period.getLabel());
        assertEquals(YearMonth.of(2026, 3).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                period.getStartMillis());
        assertEquals(YearMonth.of(2026, 4).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                period.getEndMillis());
    }

    @Test
    void getUsageWithHistoricalMonthUsesRequestedPeriodLabel() {
        Tenant tenant = tenantWithId("tenant-a");
        when(tenantService.getTenant("tenant-a")).thenReturn(tenant);
        stubConditionTypes();
        stubRefreshCounts("tenant-a", 1L, 2L, 1L, 1L, 1L, 1L);

        TenantUsage usage = tenantUsageService.getUsage("tenant-a", "2025-11");

        assertNotNull(usage);
        assertEquals("2025-11", usage.getPeriod());
    }

    @Test
    void getUsageUsesCacheForSamePeriod() {
        Tenant tenant = tenantWithId("tenant-a");
        when(tenantService.getTenant("tenant-a")).thenReturn(tenant);
        stubConditionTypes();
        stubRefreshCounts("tenant-a", 1L, 2L, 1L, 1L, 1L, 1L);

        tenantUsageService.getUsage("tenant-a", "2025-11");
        tenantUsageService.getUsage("tenant-a", "2025-11");

        org.mockito.Mockito.verify(persistenceService, org.mockito.Mockito.times(1))
                .getAllItemsCount(eq("segment"), eq("tenant-a"));
    }

    @Test
    void getUsageMergesScopeUsagesAndExcludesSystemScope() {
        Tenant tenant = tenantWithId("tenant-a");
        when(tenantService.getTenant("tenant-a")).thenReturn(tenant);
        stubConditionTypes();
        when(persistenceService.queryCount(any(), eq("profile"))).thenReturn(0L);
        when(persistenceService.queryCount(any(), eq("event"))).thenReturn(0L);
        when(persistenceService.getAllItemsCount(eq("scope"), eq("tenant-a"))).thenReturn(1L);
        when(persistenceService.getAllItemsCount(eq("segment"), eq("tenant-a"))).thenReturn(1L);
        when(persistenceService.getAllItemsCount(eq("rule"), eq("tenant-a"))).thenReturn(1L);
        when(persistenceService.calculateStorageSize("tenant-a")).thenReturn(0L);
        when(contextManager.executeAsTenant(eq("tenant-a"), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        when(persistenceService.aggregateWithOptimizedQuery(any(), any(BaseAggregate.class), eq("segment")))
                .thenReturn(java.util.Map.of("site-a", 2L, org.apache.unomi.api.Metadata.SYSTEM_SCOPE, 99L));
        when(persistenceService.aggregateWithOptimizedQuery(any(), any(BaseAggregate.class), eq("rule")))
                .thenReturn(java.util.Map.of("site-a", 3L));
        when(persistenceService.load(eq(org.apache.unomi.api.Metadata.SYSTEM_SCOPE), eq(org.apache.unomi.api.Scope.class)))
                .thenReturn(null);

        TenantUsage usage = tenantUsageService.getUsage("tenant-a", TenantUsageService.DEFAULT_PERIOD);

        assertEquals(1, usage.getScopeUsages().size());
        assertEquals("site-a", usage.getScopeUsages().get(0).getScopeId());
        assertEquals(2L, usage.getScopeUsages().get(0).getSegmentCount());
        assertEquals(3L, usage.getScopeUsages().get(0).getRuleCount());
    }

    @Test
    void getUsageSubtractsSystemScopeFromScopeCount() {
        Tenant tenant = tenantWithId("tenant-a");
        when(tenantService.getTenant("tenant-a")).thenReturn(tenant);
        stubConditionTypes();
        when(persistenceService.queryCount(any(), eq("profile"))).thenReturn(0L);
        when(persistenceService.queryCount(any(), eq("event"))).thenReturn(0L);
        when(persistenceService.getAllItemsCount(eq("scope"), eq("tenant-a"))).thenReturn(3L);
        when(persistenceService.getAllItemsCount(eq("segment"), eq("tenant-a"))).thenReturn(0L);
        when(persistenceService.getAllItemsCount(eq("rule"), eq("tenant-a"))).thenReturn(0L);
        when(persistenceService.calculateStorageSize("tenant-a")).thenReturn(0L);
        when(contextManager.executeAsTenant(eq("tenant-a"), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        when(persistenceService.aggregateWithOptimizedQuery(any(), any(BaseAggregate.class), eq("segment")))
                .thenReturn(Collections.emptyMap());
        when(persistenceService.aggregateWithOptimizedQuery(any(), any(BaseAggregate.class), eq("rule")))
                .thenReturn(Collections.emptyMap());
        org.apache.unomi.api.Scope systemScope = new org.apache.unomi.api.Scope();
        when(persistenceService.load(eq(org.apache.unomi.api.Metadata.SYSTEM_SCOPE), eq(org.apache.unomi.api.Scope.class)))
                .thenReturn(systemScope);

        TenantUsage usage = tenantUsageService.getUsage("tenant-a", TenantUsageService.DEFAULT_PERIOD);

        assertEquals(2L, usage.getScopeCount());
    }

    @Test
    void recordRestRequestIgnoresNullAndBlankTenantId() {
        tenantUsageService.recordRestRequest(null);
        tenantUsageService.recordRestRequest("");
        tenantUsageService.recordRestRequest("tenant-a");
        when(tenantService.getTenant("tenant-a")).thenReturn(tenantWithId("tenant-a"));
        stubConditionTypes();
        stubRefreshCounts("tenant-a", 0L, 0L, 0L, 0L, 0L, 0L);

        TenantUsage usage = tenantUsageService.getUsage("tenant-a", TenantUsageService.DEFAULT_PERIOD);

        assertEquals(1L, usage.getRestRequestCount());
    }

    @Test
    void purgeEventsOlderThanAcceptsMinimumRetentionDays() {
        when(tenantService.getTenant("tenant-a")).thenReturn(tenantWithId("tenant-a"));
        when(definitionsService.getConditionType("eventPropertyCondition")).thenReturn(eventPropertyConditionType);
        when(contextManager.executeAsSystem(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(contextManager.executeAsTenant(eq("tenant-a"), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        when(persistenceService.queryCount(any(), eq(Event.ITEM_TYPE))).thenReturn(0L);
        when(persistenceService.removeByQuery(any(), eq(Event.class))).thenReturn(true);

        TenantEventPurgeResult result = tenantUsageService.purgeEventsOlderThan("tenant-a",
                TenantUsageService.MIN_EVENT_RETENTION_DAYS);

        assertNotNull(result);
        assertEquals(TenantUsageService.MIN_EVENT_RETENTION_DAYS, result.getRetentionDays());
    }


    private Tenant tenantWithId(String tenantId) {
        Tenant tenant = new Tenant();
        tenant.setItemId(tenantId);
        org.apache.unomi.api.tenants.ApiKey publicKey = new org.apache.unomi.api.tenants.ApiKey();
        publicKey.setKeyType(org.apache.unomi.api.tenants.ApiKey.ApiKeyType.PUBLIC);
        publicKey.setRevoked(false);
        org.apache.unomi.api.tenants.ApiKey privateKey = new org.apache.unomi.api.tenants.ApiKey();
        privateKey.setKeyType(org.apache.unomi.api.tenants.ApiKey.ApiKeyType.PRIVATE);
        privateKey.setRevoked(false);
        tenant.setApiKeys(java.util.Arrays.asList(publicKey, privateKey));
        return tenant;
    }

    private void stubConditionTypes() {
        when(definitionsService.getConditionType("profilePropertyCondition")).thenReturn(profilePropertyConditionType);
        when(definitionsService.getConditionType("eventPropertyCondition")).thenReturn(eventPropertyConditionType);
        when(definitionsService.getConditionType("booleanCondition")).thenReturn(booleanConditionType);
    }

    private void stubRefreshCounts(String tenantId, long profiles, long events, long scopes, long segments,
                                   long rules, long storage) {
        when(persistenceService.queryCount(any(), eq("profile"))).thenReturn(profiles);
        when(persistenceService.queryCount(any(), eq("event"))).thenReturn(events);
        when(persistenceService.getAllItemsCount(eq("scope"), eq(tenantId))).thenReturn(scopes);
        when(persistenceService.getAllItemsCount(eq("segment"), eq(tenantId))).thenReturn(segments);
        when(persistenceService.getAllItemsCount(eq("rule"), eq(tenantId))).thenReturn(rules);
        when(persistenceService.calculateStorageSize(tenantId)).thenReturn(storage);
        when(contextManager.executeAsTenant(eq(tenantId), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        when(persistenceService.aggregateWithOptimizedQuery(any(), any(BaseAggregate.class), eq("segment")))
                .thenReturn(Collections.emptyMap());
        when(persistenceService.aggregateWithOptimizedQuery(any(), any(BaseAggregate.class), eq("rule")))
                .thenReturn(Collections.emptyMap());
        when(persistenceService.load(eq("systemscope"), eq(org.apache.unomi.api.Scope.class))).thenReturn(null);
    }
}
