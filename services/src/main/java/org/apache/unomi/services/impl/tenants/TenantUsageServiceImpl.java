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
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.tenants.TenantUsage;
import org.apache.unomi.api.tenants.TenantUsageService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TenantUsageServiceImpl implements TenantUsageService {

    private static final Logger logger = LoggerFactory.getLogger(TenantUsageServiceImpl.class);

    private PersistenceService persistenceService;
    private DefinitionsService definitionsService;
    private TenantService tenantService;
    private ExecutionContextManager contextManager;

    private final Map<String, UsageSnapshot> usageCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> restRequestCounts = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;
    private volatile boolean shutdownNow = false;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void activate() {
        shutdownNow = false;
        startMetricsCollection();
    }

    public void deactivate() {
        shutdownNow = true;
        stopMetricsCollection();
    }

    @Override
    public TenantUsage getUsage(String tenantId, String period) {
        String effectivePeriod = period == null || period.isEmpty() ? DEFAULT_PERIOD : period;
        if (!DEFAULT_PERIOD.equals(effectivePeriod)) {
            throw new IllegalArgumentException("Unsupported usage period: " + effectivePeriod);
        }
        if (tenantService.getTenant(tenantId) == null) {
            return null;
        }
        UsageSnapshot snapshot = usageCache.get(tenantId);
        if (snapshot == null) {
            refreshTenantUsage(tenantId);
            snapshot = usageCache.get(tenantId);
        }
        if (snapshot == null) {
            return null;
        }
        return toDto(tenantId, effectivePeriod, snapshot);
    }

    @Override
    public void recordRestRequest(String tenantId) {
        if (tenantId != null && !tenantId.isEmpty()) {
            restRequestCounts.computeIfAbsent(tenantId, id -> new AtomicLong()).incrementAndGet();
        }
    }

    private void startMetricsCollection() {
        executor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "Tenant-Usage-Collector");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleAtFixedRate(() -> {
            try {
                if (shutdownNow) {
                    return;
                }

                if (contextManager == null) {
                    logger.warn("Context manager not available, skipping usage collection");
                    return;
                }

                contextManager.executeAsSystem(() -> {
                    try {
                        if (!shutdownNow && tenantService != null && persistenceService != null) {
                            refreshAllTenants();
                        }
                    } catch (Exception e) {
                        logger.error("Error updating tenant usage", e);
                    }
                });
            } catch (Exception e) {
                logger.error("Error executing tenant usage update as system subject", e);
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    private void refreshAllTenants() {
        if (shutdownNow || definitionsService == null) {
            return;
        }

        ConditionType profilePropertyConditionType = definitionsService.getConditionType("profilePropertyCondition");
        ConditionType eventPropertyConditionType = definitionsService.getConditionType("eventPropertyCondition");

        if (profilePropertyConditionType == null || eventPropertyConditionType == null) {
            logger.debug("Required condition types not available, skipping usage update");
            return;
        }

        try {
            List<Tenant> tenants = tenantService.getAllTenants();
            for (Tenant tenant : tenants) {
                if (shutdownNow) {
                    return;
                }
                refreshTenantUsage(tenant.getItemId(), profilePropertyConditionType, eventPropertyConditionType);
            }
        } catch (Exception e) {
            logger.error("Error refreshing tenant usage", e);
        }
    }

    private void refreshTenantUsage(String tenantId) {
        if (definitionsService == null) {
            return;
        }
        ConditionType profilePropertyConditionType = definitionsService.getConditionType("profilePropertyCondition");
        ConditionType eventPropertyConditionType = definitionsService.getConditionType("eventPropertyCondition");
        if (profilePropertyConditionType == null || eventPropertyConditionType == null) {
            return;
        }
        refreshTenantUsage(tenantId, profilePropertyConditionType, eventPropertyConditionType);
    }

    private void refreshTenantUsage(String tenantId, ConditionType profilePropertyConditionType,
                                    ConditionType eventPropertyConditionType) {
        if (shutdownNow || persistenceService == null) {
            return;
        }

        UsageSnapshot snapshot = new UsageSnapshot();
        snapshot.profileCount = countByTenantProperty(tenantId, profilePropertyConditionType, Profile.ITEM_TYPE);
        snapshot.eventCount = countByTenantProperty(tenantId, eventPropertyConditionType, Event.ITEM_TYPE);
        snapshot.segmentCount = persistenceService.getAllItemsCount(Segment.ITEM_TYPE, tenantId);
        snapshot.ruleCount = persistenceService.getAllItemsCount(Rule.ITEM_TYPE, tenantId);
        snapshot.storageDocumentCount = persistenceService.calculateStorageSize(tenantId);
        snapshot.collectedAt = System.currentTimeMillis();
        usageCache.put(tenantId, snapshot);
    }

    private long countByTenantProperty(String tenantId, ConditionType conditionType, String itemType) {
        Condition condition = new Condition();
        condition.setConditionTypeId(conditionType.getItemId());
        condition.setConditionType(conditionType);
        condition.setParameter("propertyName", "tenantId");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", tenantId);
        return persistenceService.queryCount(condition, itemType);
    }

    private long currentRestRequestCount(String tenantId) {
        AtomicLong counter = restRequestCounts.get(tenantId);
        return counter != null ? counter.get() : 0L;
    }

    private TenantUsage toDto(String tenantId, String period, UsageSnapshot snapshot) {
        TenantUsage usage = new TenantUsage();
        usage.setTenantId(tenantId);
        usage.setPeriod(period);
        usage.setProfileCount(snapshot.profileCount);
        usage.setEventCount(snapshot.eventCount);
        usage.setSegmentCount(snapshot.segmentCount);
        usage.setRuleCount(snapshot.ruleCount);
        usage.setStorageDocumentCount(snapshot.storageDocumentCount);
        usage.setRestRequestCount(currentRestRequestCount(tenantId));
        usage.setCollectedAt(snapshot.collectedAt);
        return usage;
    }

    private void stopMetricsCollection() {
        if (executor != null) {
            try {
                executor.shutdownNow();
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    logger.warn("Tenant usage executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while shutting down the tenant usage executor");
            } finally {
                executor = null;
            }
        }
    }

    private static final class UsageSnapshot {
        private long profileCount;
        private long eventCount;
        private long segmentCount;
        private long ruleCount;
        private long storageDocumentCount;
        private long collectedAt;
    }
}
