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

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class TenantMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(TenantMonitoringService.class);

    @Reference
    private PersistenceService persistenceService;

    @Reference
    private TenantService tenantService;

    private final Map<String, TenantMetrics> metricsCache = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        startMetricsCollection();
    }

    public TenantMetrics getMetrics(String tenantId) {
        return metricsCache.get(tenantId);
    }

    private void startMetricsCollection() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(() -> {
            try {
                updateMetrics();
            } catch (Exception e) {
                logger.error("Error updating metrics", e);
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    private void updateMetrics() {
        List<Tenant> tenants = tenantService.getAllTenants();
        for (Tenant tenant : tenants) {
            TenantMetrics metrics = new TenantMetrics();
            metrics.setProfileCount(countProfiles(tenant.getItemId()));
            metrics.setEventCount(countEvents(tenant.getItemId()));
            metrics.setStorageSize(persistenceService.calculateStorageSize(tenant.getItemId()));
            metrics.setApiCallCount(persistenceService.getApiCallCount(tenant.getItemId()));

            metricsCache.put(tenant.getItemId(), metrics);
        }
    }

    private long countProfiles(String tenantId) {
        Condition condition = new Condition();
        condition.setConditionTypeId("propertyCondition");
        condition.setParameter("propertyName", "tenantId");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", tenantId);
        return persistenceService.queryCount(condition, "profile");
    }

    private long countEvents(String tenantId) {
        Condition condition = new Condition();
        condition.setConditionTypeId("propertyCondition");
        condition.setParameter("propertyName", "tenantId");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", tenantId);
        return persistenceService.queryCount(condition, "event");
    }
}
