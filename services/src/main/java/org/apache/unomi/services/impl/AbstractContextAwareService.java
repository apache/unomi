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
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

/**
 * Base class for services that need to be context-aware and handle inheritance from the system tenant.
 */
public abstract class AbstractContextAwareService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractContextAwareService.class);

    protected PersistenceService persistenceService;
    protected volatile ExecutionContextManager contextManager = null;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public PersistenceService getPersistenceService() {
        return persistenceService;
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
            item = contextManager.executeAsSystem(() -> {
                return persistenceService.load(itemId, itemClass);
            });
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
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        if (currentTenant != null) {
            item.setTenantId(currentTenant);
        }
        persistenceService.save(item);
    }

    /**
     * Get metadata items with tenant awareness and inheritance.
     *
     * @param query The query to execute
     * @param clazz The class of items to retrieve
     * @return A partial list of metadata items
     */
    protected <T extends MetadataItem> PartialList<Metadata> getMetadatas(Query query, Class<T> clazz) {
        String currentTenantId =  contextManager.getCurrentContext().getTenantId();
        if (currentTenantId == null) {
            return new PartialList<>();
        }

        Condition tenantCondition = createTenantCondition(currentTenantId);
        Condition finalCondition = combineTenantCondition(query.getCondition(), tenantCondition);

        PartialList<T> items = persistenceService.query(finalCondition, query.getSortby(), clazz, query.getOffset(), query.getLimit());
        return convertToMetadataList(items);
    }

    /**
     * Create a condition to filter by tenant
     */
    protected Condition createTenantCondition(String tenantId) {
        Condition tenantCondition = new Condition();
        tenantCondition.setConditionTypeId("sessionPropertyCondition");
        tenantCondition.setParameter("propertyName", "tenantId");
        tenantCondition.setParameter("comparisonOperator", "equals");
        tenantCondition.setParameter("propertyValue", tenantId);
        return tenantCondition;
    }

    /**
     * Combine a query condition with a tenant condition
     */
    protected Condition combineTenantCondition(Condition queryCondition, Condition tenantCondition) {
        Condition finalCondition = new Condition();
        finalCondition.setConditionTypeId("booleanCondition");
        finalCondition.setParameter("operator", "and");
        finalCondition.setParameter("subConditions", Arrays.asList(queryCondition, tenantCondition));
        return finalCondition;
    }

    /**
     * Convert a list of items to a list of metadata
     */
    protected <T extends MetadataItem> PartialList<Metadata> convertToMetadataList(PartialList<T> items) {
        List<Metadata> metadatas = new LinkedList<>();
        for (T item : items.getList()) {
            metadatas.add(item.getMetadata());
        }
        return new PartialList<>(metadatas, items.getOffset(), items.getPageSize(), items.getTotalSize(), items.getTotalSizeRelation());
    }

    /**
     * Check if the current tenant is the system tenant
     *
     * @return true if the current tenant is the system tenant
     */
    protected boolean isSystemTenant() {
        String currentTenant =  contextManager.getCurrentContext().getTenantId();
        return SYSTEM_TENANT.equals(currentTenant);
    }

    /**
     * Execute code in the context of the system tenant
     *
     * @param runnable The code to execute
     */
    protected void executeAsSystem(Runnable operation) {
        contextManager.executeAsSystem(operation);
    }

    /**
     * Execute code in the context of the system tenant and return a value
     *
     * @param supplier The code to execute that returns a value
     * @return The value returned by the supplier
     */
    protected <T> T executeAsSystem(Supplier<T> operation) {
        return contextManager.executeAsSystem(operation);
    }

}
