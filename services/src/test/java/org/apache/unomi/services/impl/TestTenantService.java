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
import org.apache.unomi.api.tenants.TenantStatus;

import javax.xml.bind.DatatypeConverter;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Custom TenantService implementation for testing
public class TestTenantService implements TenantService {
    private ThreadLocal<String> currentTenantId = new ThreadLocal<>();
    private Map<String, Tenant> tenants = new ConcurrentHashMap<>();
    private ThreadLocal<Boolean> inSystemOperation = new ThreadLocal<>();
    private static final SecureRandom secureRandom = new SecureRandom();

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
    public boolean validateApiKey(String tenantId, String key) {
        return validateApiKeyWithType(tenantId, key, null);
    }

    @Override
    public boolean validateApiKeyWithType(String tenantId, String key, ApiKey.ApiKeyType requiredType) {
        Tenant tenant = getTenant(tenantId);
        if (tenant == null) {
            return false;
        }
        if (tenant.getApiKeys() == null) {
            return false;
        }
        return tenant.getApiKeys().stream()
                .anyMatch(apiKey -> apiKey.getKey().equals(key) &&
                        !apiKey.isRevoked() &&
                        (requiredType == null || apiKey.getKeyType() == requiredType) &&
                        (apiKey.getExpirationDate() == null || apiKey.getExpirationDate().after(new Date())));
    }

    @Override
    public Tenant createTenant(String tenantId, Map<String, Object> properties) {
        Tenant tenant = new Tenant();
        tenant.setItemId(tenantId);
        tenant.setProperties(properties != null ? properties : new HashMap<>());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setCreationDate(new Date());
        tenant.setLastModificationDate(new Date());
        
        saveTenant(tenant);

        // Generate both public and private API keys (consistent with TenantServiceImpl)
        generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, null);
        generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, null);

        // Return the updated tenant with API keys
        return getTenant(tenant.getItemId());
    }

    @Override
    public ApiKey generateApiKey(String tenantId, Long validityPeriod) {
        return generateApiKeyWithType(tenantId, ApiKey.ApiKeyType.PUBLIC, validityPeriod);
    }

    @Override
    public ApiKey generateApiKeyWithType(String tenantId, ApiKey.ApiKeyType keyType, Long validityPeriod) {
        ApiKey apiKey = new ApiKey();
        apiKey.setItemId(UUID.randomUUID().toString());
        String key = generateSecureKey();
        apiKey.setKey(key);
        apiKey.setKeyType(keyType);
        apiKey.setCreationDate(new Date());
        if (validityPeriod != null) {
            apiKey.setExpirationDate(new Date(System.currentTimeMillis() + validityPeriod));
        }

        Tenant tenant = getTenant(tenantId);
        if (tenant != null) {
            // Remove any existing key of the same type
            if (tenant.getApiKeys() == null) {
                tenant.setApiKeys(new ArrayList<>());
            }
            tenant.getApiKeys().removeIf(existingKey -> existingKey.getKeyType() == keyType);
            tenant.getApiKeys().add(apiKey);
            saveTenant(tenant);
        }

        return apiKey;
    }

    @Override
    public Tenant getTenantByApiKey(String apiKey) {
        return tenants.values().stream()
                .filter(tenant -> tenant.getApiKeys() != null && 
                        tenant.getApiKeys().stream()
                                .anyMatch(key -> key.getKey().equals(apiKey)))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Tenant getTenantByApiKey(String apiKey, ApiKey.ApiKeyType keyType) {
        return tenants.values().stream()
                .filter(tenant -> tenant.getApiKeys() != null && 
                        tenant.getApiKeys().stream()
                                .anyMatch(key -> key.getKey().equals(apiKey) && key.getKeyType() == keyType))
                .findFirst()
                .orElse(null);
    }

    @Override
    public ApiKey getApiKey(String tenantId, ApiKey.ApiKeyType keyType) {
        Tenant tenant = getTenant(tenantId);
        if (tenant != null && tenant.getApiKeys() != null) {
            return tenant.getApiKeys().stream()
                .filter(key -> key.getKeyType() == keyType)
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    private String generateSecureKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return DatatypeConverter.printHexBinary(randomBytes);
    }
}
