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

import java.util.Collections;
import java.util.List;
import java.util.Map;

// Custom TenantService implementation for testing
public class TestTenantService implements TenantService {
    private ThreadLocal<String> currentTenantId = new ThreadLocal<>();

    public void setCurrentTenantId(String tenantId) {
        currentTenantId.set(tenantId);
    }

    @Override
    public String getCurrentTenantId() {
        return currentTenantId.get();
    }

    @Override
    public void setCurrentTenant(String tenantId) {
        setCurrentTenantId(tenantId);
    }

    @Override
    public List<Tenant> getAllTenants() {
        return Collections.emptyList();
    }

    @Override
    public Tenant getTenant(String tenantId) {
        return null;
    }

    @Override
    public void saveTenant(Tenant tenant) {
        // No-op for test
    }

    @Override
    public void deleteTenant(String tenantId) {
        // No-op for test
    }

    @Override
    public boolean validateApiKey(String tenantId, String apiKey) {
        return true;
    }

    @Override
    public boolean validateApiKeyWithType(String tenantId, String apiKey, ApiKey.ApiKeyType type) {
        return true;
    }

    @Override
    public Tenant createTenant(String tenantId, Map<String, Object> properties) {
        return null;
    }

    @Override
    public ApiKey generateApiKey(String tenantId, Long validityPeriod) {
        return null;
    }

    @Override
    public ApiKey generateApiKeyWithType(String tenantId, ApiKey.ApiKeyType type, Long validityPeriod) {
        return null;
    }

    @Override
    public Tenant getTenantByApiKey(String apiKey) {
        return null;
    }

    @Override
    public Tenant getTenantByApiKey(String apiKey, ApiKey.ApiKeyType type) {
        return null;
    }

    @Override
    public ApiKey getApiKey(String tenantId, ApiKey.ApiKeyType type) {
        return null;
    }
}
