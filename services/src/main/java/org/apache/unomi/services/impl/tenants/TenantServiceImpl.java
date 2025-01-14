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

import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.tenants.TenantStatus;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component(service = TenantService.class)
public class TenantServiceImpl implements TenantService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TenantServiceImpl.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    @Reference
    private PersistenceService persistenceService;

    @Reference
    private ConfigurationAdmin configAdmin;

    private ThreadLocal<String> currentTenant = new ThreadLocal<>();

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
