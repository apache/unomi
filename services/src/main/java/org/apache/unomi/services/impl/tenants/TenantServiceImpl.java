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

import org.apache.unomi.api.services.TenantLifecycleListener;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.tenants.TenantStatus;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class TenantServiceImpl implements TenantService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TenantServiceImpl.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    private final List<TenantLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private PersistenceService persistenceService;
    private ConfigurationAdmin configAdmin;
    private ThreadLocal<String> currentTenant = new ThreadLocal<>();

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setConfigAdmin(ConfigurationAdmin configAdmin) {
        this.configAdmin = configAdmin;
    }

    public void bindListener(TenantLifecycleListener listener) {
        lifecycleListeners.add(listener);
        LOGGER.debug("Added tenant lifecycle listener: {}", listener.getClass().getName());
    }

    public void unbindListener(TenantLifecycleListener listener) {
        lifecycleListeners.remove(listener);
        LOGGER.debug("Removed tenant lifecycle listener: {}", listener.getClass().getName());
    }

    @Override
    public Tenant createTenant(String name, Map<String, Object> properties) {
        Tenant tenant = new Tenant();
        tenant.setItemId(UUID.randomUUID().toString());
        tenant.setName(name);
        tenant.setProperties(properties);
        tenant.setStatus(TenantStatus.ACTIVE);

        persistenceService.save(tenant);
        return tenant;
    }

    @Override
    public ApiKey generateApiKey(String tenantId, Long validityPeriod) {
        ApiKey apiKey = new ApiKey();
        apiKey.setItemId(UUID.randomUUID().toString());
        String key = generateSecureKey();
        apiKey.setKey(key);
        apiKey.setCreationDate(new Date());
        if (validityPeriod != null) {
            apiKey.setExpirationDate(new Date(System.currentTimeMillis() + validityPeriod));
        }

        Tenant tenant = persistenceService.load(tenantId, Tenant.class);
        if (tenant != null) {
            tenant.getApiKeys().add(apiKey);
            persistenceService.save(tenant);
        }

        return apiKey;
    }

    @Override
    public String getCurrentTenantId() {
        return currentTenant.get();
    }

    @Override
    public void setCurrentTenant(String tenantId) {
        currentTenant.set(tenantId);
    }

    @Override
    public List<Tenant> getAllTenants() {
        return persistenceService.getAllItems(Tenant.class);
    }

    @Override
    public Tenant getTenant(String tenantId) {
        return persistenceService.load(tenantId, Tenant.class);
    }

    private String generateSecureKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return DatatypeConverter.printHexBinary(randomBytes);
    }

    @Override
    public void saveTenant(Tenant tenant) {
        persistenceService.save(tenant);
    }

    @Override
    public void deleteTenant(String tenantId) {
        persistenceService.remove(tenantId, Tenant.class);

        // Notify all listeners of tenant removal
        for (TenantLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onTenantRemoved(tenantId);
            } catch (Exception e) {
                LOGGER.error("Error notifying listener {} of tenant removal: {}", listener.getClass().getName(), tenantId, e);
            }
        }

        LOGGER.info("Tenant {} and associated resources have been removed", tenantId);
    }

    @Override
    public boolean validateApiKey(String tenantId, String key) {
        Tenant tenant = getTenant(tenantId);
        if (tenant == null) {
            return false;
        }
        return tenant.getApiKeys().stream()
                .anyMatch(apiKey -> apiKey.getKey().equals(key) &&
                        (apiKey.getExpirationDate() == null || apiKey.getExpirationDate().after(new Date())));
    }
}
