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

import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.ResourceQuota;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TenantQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(TenantQuotaService.class);

    private PersistenceService persistenceService;
    private TenantService tenantService;
    private ExecutionContextManager contextManager;

    private Map<String, TenantUsage> usageCache = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;
    private volatile boolean shutdownNow = false;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void activate() {
        shutdownNow = false; // Reset shutdown flag
        // Start usage monitoring
        startUsageMonitoring();
    }

    public void deactivate() {
        shutdownNow = true; // Set shutdown flag before stopping
        stopUsageMonitoring();
    }

    private ResourceQuota getTenantQuota(String tenantId) {
        Tenant tenant = persistenceService.load(tenantId, Tenant.class);
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
        if (shutdownNow || persistenceService == null) {
            return; // Skip if shutting down or persistence service is unavailable
        }
        
        try {
            for (String tenantId : usageCache.keySet()) {
                if (shutdownNow) return; // Check shutdown flag during iteration
                
                TenantUsage usage = usageCache.get(tenantId);
                usage.setProfileCount(persistenceService.getAllItemsCount("profile"));
                usage.setEventCount(persistenceService.getAllItemsCount("event"));
                // Note: Storage size calculation would require additional implementation
            }
        } catch (Exception e) {
            logger.error("Error updating tenant usage statistics", e);
        }
    }

    private void startUsageMonitoring() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Tenant-Usage-Monitor");
            t.setDaemon(true); // Make it daemon so it doesn't prevent JVM shutdown
            return t;
        });
        
        executor.scheduleAtFixedRate(() -> {
            try {
                if (shutdownNow) {
                    return; // Skip execution if shutting down
                }
                
                if (contextManager == null) {
                    logger.warn("Context manager not available, skipping usage statistics update");
                    return;
                }
                
                contextManager.executeAsSystem(() -> {
                    try {
                        if (!shutdownNow && persistenceService != null) {
                            updateUsageStatistics();
                        }
                    } catch (Exception e) {
                        logger.error("Error updating usage statistics", e);
                    }
                });
            } catch (Exception e) {
                logger.error("Error executing usage statistics update as system subject", e);
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    private void stopUsageMonitoring() {
        if (executor != null) {
            try {
                // Use shutdownNow instead of shutdown for immediate interruption
                executor.shutdownNow();
                // Reduce wait time to avoid blocking OSGi shutdown
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    logger.warn("Executor did not terminate in time, some tasks may have been canceled");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while shutting down the monitoring executor");
            } finally {
                executor = null;
            }
        }
    }
}
