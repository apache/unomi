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
package org.apache.unomi.services.impl.scope;

import org.apache.unomi.api.Scope;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ScopeServiceImpl implements ScopeService {

    private PersistenceService persistenceService;
    private SchedulerService schedulerService;
    private TenantService tenantService;
    private Integer scopesRefreshInterval = 1000;

    // Map of tenant ID to its scopes map
    private ConcurrentMap<String, ConcurrentMap<String, Scope>> tenantScopes = new ConcurrentHashMap<>();
    private ScheduledFuture<?> scheduledFuture;

    @Override
    public List<Scope> getScopes() {
        String currentTenant = tenantService.getCurrentTenantId();
        if (currentTenant == null) {
            return Collections.emptyList();
        }
        ConcurrentMap<String, Scope> scopesForTenant = tenantScopes.get(currentTenant);
        return scopesForTenant != null ? new ArrayList<>(scopesForTenant.values()) : Collections.emptyList();
    }

    @Override
    public void save(Scope scope) {
        String currentTenant = tenantService.getCurrentTenantId();
        if (currentTenant == null) {
            throw new IllegalStateException("Cannot save scope: no tenant specified");
        }
        scope.setTenantId(currentTenant);
        persistenceService.save(scope);
    }

    @Override
    public boolean delete(String id) {
        return persistenceService.remove(id, Scope.class);
    }

    @Override
    public Scope getScope(String id) {
        String currentTenant = tenantService.getCurrentTenantId();
        if (currentTenant == null) {
            return null;
        }
        ConcurrentMap<String, Scope> scopesForTenant = tenantScopes.get(currentTenant);
        return scopesForTenant != null ? scopesForTenant.get(id) : null;
    }

    private void refreshScopes() {
        // Get all tenants including system tenant
        List<String> allTenants = new ArrayList<>();
        allTenants.add(TenantService.SYSTEM_TENANT);
        allTenants.addAll(tenantService.getAllTenants().stream()
                .map(tenant -> tenant.getItemId())
                .collect(Collectors.toList()));

        // Create new tenant scopes map
        ConcurrentMap<String, ConcurrentMap<String, Scope>> newTenantScopes = new ConcurrentHashMap<>();

        // For each tenant, load its scopes
        for (String tenantId : allTenants) {
            String previousTenant = tenantService.getCurrentTenantId();
            try {
                tenantService.setCurrentTenant(tenantId);
                List<Scope> tenantScopes = persistenceService.getAllItems(Scope.class);
                if (!tenantScopes.isEmpty()) {
                    ConcurrentMap<String, Scope> scopeMap = new ConcurrentHashMap<>();
                    for (Scope scope : tenantScopes) {
                        scopeMap.put(scope.getItemId(), scope);
                    }
                    newTenantScopes.put(tenantId, scopeMap);
                }
            } finally {
                tenantService.setCurrentTenant(previousTenant);
            }
        }

        // Atomic update of the tenant scopes map
        this.tenantScopes = newTenantScopes;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public void setScopesRefreshInterval(Integer scopesRefreshInterval) {
        this.scopesRefreshInterval = scopesRefreshInterval;
    }

    public void postConstruct() {
        initializeTimers();
    }

    public void preDestroy() {
        scheduledFuture.cancel(true);
    }

    private void initializeTimers() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                refreshScopes();
            }
        };
        scheduledFuture = schedulerService.getScheduleExecutorService()
                .scheduleWithFixedDelay(task, 0, scopesRefreshInterval, TimeUnit.MILLISECONDS);
    }
}
