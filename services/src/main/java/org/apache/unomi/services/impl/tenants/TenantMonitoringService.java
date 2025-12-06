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
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TenantMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(TenantMonitoringService.class);

    private PersistenceService persistenceService;
    private DefinitionsService definitionsService;
    private TenantService tenantService;
    private ExecutionContextManager contextManager;

    private final Map<String, TenantMetrics> metricsCache = new ConcurrentHashMap<>();
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

    public TenantMetrics getMetrics(String tenantId) {
        return metricsCache.get(tenantId);
    }

    private void startMetricsCollection() {
        executor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "Tenant-Metrics-Collector");
            t.setDaemon(true);
            return t;
        });
        
        executor.scheduleAtFixedRate(() -> {
            try {
                if (shutdownNow) {
                    return;
                }
                
                if (contextManager == null) {
                    logger.warn("Context manager not available, skipping metrics collection");
                    return;
                }
                
                contextManager.executeAsSystem(() -> {
                    try {
                        if (!shutdownNow && tenantService != null && persistenceService != null) {
                            updateMetrics();
                        }
                    } catch (Exception e) {
                        logger.error("Error updating metrics", e);
                    }
                });
            } catch (Exception e) {
                logger.error("Error executing metrics update as system subject", e);
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    private void updateMetrics() {
        if (shutdownNow) {
            return;
        }
        
        // Check if required condition types are available before updating metrics
        if (definitionsService == null) {
            logger.debug("DefinitionsService not available, skipping metrics update");
            return;
        }
        
        ConditionType profilePropertyConditionType = definitionsService.getConditionType("profilePropertyCondition");
        ConditionType eventPropertyConditionType = definitionsService.getConditionType("eventPropertyCondition");
        
        if (profilePropertyConditionType == null || eventPropertyConditionType == null) {
            logger.debug("Required condition types not available (profilePropertyCondition: {}, eventPropertyCondition: {}), skipping metrics update",
                    profilePropertyConditionType != null, eventPropertyConditionType != null);
            return;
        }
        
        try {
            List<Tenant> tenants = tenantService.getAllTenants();
            for (Tenant tenant : tenants) {
                if (shutdownNow) return;
                
                TenantMetrics metrics = new TenantMetrics();
                metrics.setProfileCount(countProfiles(tenant.getItemId(), profilePropertyConditionType));
                metrics.setEventCount(countEvents(tenant.getItemId(), eventPropertyConditionType));
                metrics.setStorageSize(persistenceService.calculateStorageSize(tenant.getItemId()));
                metrics.setApiCallCount(persistenceService.getApiCallCount(tenant.getItemId()));

                metricsCache.put(tenant.getItemId(), metrics);
            }
        } catch (Exception e) {
            logger.error("Error updating tenant metrics", e);
        }
    }

    private long countProfiles(String tenantId, ConditionType conditionType) {
        Condition condition = new Condition();
        condition.setConditionTypeId("profilePropertyCondition");
        condition.setConditionType(conditionType);
        condition.setParameter("propertyName", "tenantId");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", tenantId);
        return persistenceService.queryCount(condition, Profile.ITEM_TYPE);
    }

    private long countEvents(String tenantId, ConditionType conditionType) {
        Condition condition = new Condition();
        condition.setConditionTypeId("eventPropertyCondition");
        condition.setConditionType(conditionType);
        condition.setParameter("propertyName", "tenantId");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", tenantId);
        return persistenceService.queryCount(condition, Event.ITEM_TYPE);
    }

    private void stopMetricsCollection() {
        if (executor != null) {
            try {
                executor.shutdownNow();
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
