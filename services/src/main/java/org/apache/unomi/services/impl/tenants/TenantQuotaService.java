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

import org.apache.unomi.api.tenants.ResourceQuota;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class TenantQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(TenantQuotaService.class);

    @Reference
    private PersistenceService persistenceService;

    @Reference
    private TenantService tenantService;

    private Map<String, TenantUsage> usageCache = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        // Start usage monitoring
        startUsageMonitoring();
    }

    private ResourceQuota getTenantQuota(String tenantId) {
        org.apache.unomi.api.tenants.Tenant tenant = persistenceService.load(tenantId, org.apache.unomi.api.tenants.Tenant.class);
        return tenant != null ? tenant.getResourceQuota() : null;
    }

    private TenantUsage getUsage(String tenantId) {
        return usageCache.computeIfAbsent(tenantId, k -> new TenantUsage());
    }

    public boolean checkQuota(String tenantId, String quotaType, long increment) {
        ResourceQuota quota = getTenantQuota(tenantId);
        TenantUsage usage = getUsage(tenantId);

        switch (quotaType) {
            case "profiles":
                return (usage.getProfileCount() + increment) <= quota.getMaxProfiles();
            case "events":
                return (usage.getEventCount() + increment) <= quota.getMaxEvents();
            case "storage":
                return (usage.getStorageSize() + increment) <= quota.getMaxStorageSize();
            default:
                if (quota.getCustomQuotas().containsKey(quotaType)) {
                    return (usage.getCustomUsage(quotaType) + increment) <=
                        quota.getCustomQuotas().get(quotaType);
                }
                return true;
        }
    }

    private void updateUsageStatistics() {
        for (String tenantId : usageCache.keySet()) {
            TenantUsage usage = usageCache.get(tenantId);
            usage.setProfileCount(persistenceService.getAllItemsCount("profile"));
            usage.setEventCount(persistenceService.getAllItemsCount("event"));
            // Note: Storage size calculation would require additional implementation
        }
    }

    private void startUsageMonitoring() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                updateUsageStatistics();
            } catch (Exception e) {
                logger.error("Error updating usage statistics", e);
            }
        }, 0, 1, TimeUnit.HOURS);
    }
}
