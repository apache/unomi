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

import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TenantQuotaServiceTest {

    @Mock
    private PersistenceService persistenceService;

    private TenantQuotaService tenantQuotaService;

    @BeforeEach
    public void setUp() throws Exception {
        tenantQuotaService = new TenantQuotaService();
        tenantQuotaService.setPersistenceService(persistenceService);

        Field usageCacheField = TenantQuotaService.class.getDeclaredField("usageCache");
        usageCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, TenantUsage> usageCache = (Map<String, TenantUsage>) usageCacheField.get(tenantQuotaService);
        usageCache.put("tenant-a", new TenantUsage());
        usageCache.put("tenant-b", new TenantUsage());
    }

    @Test
    public void updateUsageStatisticsUsesPerTenantCounts() throws Exception {
        when(persistenceService.getAllItemsCount(eq("profile"), eq("tenant-a"))).thenReturn(10L);
        when(persistenceService.getAllItemsCount(eq("event"), eq("tenant-a"))).thenReturn(20L);
        when(persistenceService.getAllItemsCount(eq("profile"), eq("tenant-b"))).thenReturn(100L);
        when(persistenceService.getAllItemsCount(eq("event"), eq("tenant-b"))).thenReturn(200L);

        Method updateMethod = TenantQuotaService.class.getDeclaredMethod("updateUsageStatistics");
        updateMethod.setAccessible(true);
        updateMethod.invoke(tenantQuotaService);

        Field usageCacheField = TenantQuotaService.class.getDeclaredField("usageCache");
        usageCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, TenantUsage> usageCache = (Map<String, TenantUsage>) usageCacheField.get(tenantQuotaService);

        assertEquals(10L, usageCache.get("tenant-a").getProfileCount());
        assertEquals(20L, usageCache.get("tenant-a").getEventCount());
        assertEquals(100L, usageCache.get("tenant-b").getProfileCount());
        assertEquals(200L, usageCache.get("tenant-b").getEventCount());

        verify(persistenceService).getAllItemsCount("profile", "tenant-a");
        verify(persistenceService).getAllItemsCount("event", "tenant-a");
        verify(persistenceService).getAllItemsCount("profile", "tenant-b");
        verify(persistenceService).getAllItemsCount("event", "tenant-b");
        verify(persistenceService, never()).getAllItemsCount("profile");
        verify(persistenceService, never()).getAllItemsCount("event");
    }
}
