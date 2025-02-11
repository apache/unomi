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

import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Custom TenantService implementation for testing
public class TestTenantService implements TenantService {
    private ThreadLocal<String> currentTenantId = new ThreadLocal<>();
    private Map<String, Tenant> tenants = new ConcurrentHashMap<>();
    private ThreadLocal<Boolean> inSystemOperation = new ThreadLocal<>();

    public void setInSystemOperation(boolean inSystemOperation) {
        this.inSystemOperation.set(inSystemOperation);
    }

    public void clearInSystemOperation() {
        this.inSystemOperation.remove();
    }

    public void setCurrentTenantId(String tenantId) {
        currentTenantId.set(tenantId);
    }

    @Override
    public List<Tenant> getAllTenants() {
        return new ArrayList<>(tenants.values());
    }

    @Override
    public Tenant getTenant(String tenantId) {
        return tenants.get(tenantId);
    }

    @Override
    public void saveTenant(Tenant tenant) {
        if (tenant != null && tenant.getItemId() != null) {
            tenants.put(tenant.getItemId(), tenant);
        }
    }

    @Override
    public void deleteTenant(String tenantId) {
        tenants.remove(tenantId);
    }

    @Override
    public boolean validateApiKey(String tenantId, String apiKey) {
        return true; // For testing purposes
    }

    @Override
    public boolean validateApiKeyWithType(String tenantId, String apiKey, ApiKey.ApiKeyType type) {
        return true; // For testing purposes
    }

    @Override
    public Tenant createTenant(String tenantId, Map<String, Object> properties) {
        Tenant tenant = new Tenant();
        tenant.setItemId(tenantId);
        tenant.setProperties(properties != null ? properties : new HashMap<>());
        saveTenant(tenant);
        return tenant;
    }

    @Override
    public ApiKey generateApiKey(String tenantId, Long validityPeriod) {
        return null; // Not needed for testing
    }

    @Override
    public ApiKey generateApiKeyWithType(String tenantId, ApiKey.ApiKeyType type, Long validityPeriod) {
        return null; // Not needed for testing
    }

    @Override
    public Tenant getTenantByApiKey(String apiKey) {
        return null; // Not needed for testing
    }

    @Override
    public Tenant getTenantByApiKey(String apiKey, ApiKey.ApiKeyType type) {
        return null; // Not needed for testing
    }

    @Override
    public ApiKey getApiKey(String tenantId, ApiKey.ApiKeyType type) {
        return null; // Not needed for testing
    }
}
