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

import org.apache.unomi.api.Item;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

/**
 * Base class for services that need to be tenant-aware and handle inheritance from the system tenant.
 */
public abstract class AbstractTenantAwareService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTenantAwareService.class);

    protected PersistenceService persistenceService;
    protected TenantService tenantService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    public TenantService getTenantService() {
        return tenantService;
    }

    /**
     * Load an item with tenant inheritance support.
     * First tries to load from the current tenant, then falls back to the system tenant if not found.
     *
     * @param itemId The ID of the item to load
     * @param itemClass The class of the item
     * @return The loaded item or null if not found in either tenant
     */
    protected <T extends Item> T loadWithInheritance(String itemId, Class<T> itemClass) {
        T item = persistenceService.load(itemId, itemClass);
        if (item == null) {
            String currentTenant = tenantService.getCurrentTenantId();
            if (currentTenant != null && !currentTenant.equals(SYSTEM_TENANT)) {
                tenantService.setCurrentTenant(SYSTEM_TENANT);
                try {
                    item = persistenceService.load(itemId, itemClass);
                } finally {
                    tenantService.setCurrentTenant(currentTenant);
                }
            }
        }
        return item;
    }

    /**
     * Save an item with tenant awareness.
     * Ensures the item is saved to the current tenant and handles any inheritance implications.
     *
     * @param item The item to save
     */
    protected void saveWithTenant(Item item) {
        String currentTenant = tenantService.getCurrentTenantId();
        if (currentTenant != null) {
            item.setTenantId(currentTenant);
        }
        persistenceService.save(item);
    }

    /**
     * Check if the current tenant is the system tenant
     *
     * @return true if the current tenant is the system tenant
     */
    protected boolean isSystemTenant() {
        String currentTenant = tenantService.getCurrentTenantId();
        return SYSTEM_TENANT.equals(currentTenant);
    }

    /**
     * Execute code in the context of the system tenant
     *
     * @param runnable The code to execute
     */
    protected void withSystemTenant(Runnable runnable) {
        String currentTenant = tenantService.getCurrentTenantId();
        tenantService.setCurrentTenant(SYSTEM_TENANT);
        try {
            runnable.run();
        } finally {
            tenantService.setCurrentTenant(currentTenant);
        }
    }

    /**
     * Execute code in the context of the system tenant and return a value
     *
     * @param supplier The code to execute that returns a value
     * @return The value returned by the supplier
     */
    protected <T> T withSystemTenant(java.util.function.Supplier<T> supplier) {
        String currentTenant = tenantService.getCurrentTenantId();
        tenantService.setCurrentTenant(SYSTEM_TENANT);
        try {
            return supplier.get();
        } finally {
            tenantService.setCurrentTenant(currentTenant);
        }
    }
}
