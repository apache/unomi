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
package org.apache.unomi.services.common.service;

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
 * Base class for services that operate within a tenant {@link org.apache.unomi.api.ExecutionContext} and support
 * inheritance from the system tenant.
 * <p>
 * Subclasses use {@link #loadWithInheritance(String, Class)} and {@link #getMetadatas(Query, Class)}
 * to resolve tenant-scoped data with fallback to the system tenant. System-tenant operations are
 * delegated to {@link ExecutionContextManager#executeAsSystem(Runnable)}.
 *
 * @see org.apache.unomi.api.services.ExecutionContextManager
 * @see org.apache.unomi.services.common.cache.AbstractMultiTypeCachingService
 */
public abstract class AbstractContextAwareService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractContextAwareService.class);

    protected PersistenceService persistenceService;
    protected volatile ExecutionContextManager contextManager = null;

    /**
     * Sets the persistence service used for loading and saving items.
     *
     * @param persistenceService the persistence service
     */
    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * Sets the execution context manager for tenant-scoped operations.
     *
     * @param contextManager the execution context manager
     */
    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    /**
     * Retrieves the persistence service.
     *
     * @return the persistence service
     */
    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    /**
     * Loads an item with tenant inheritance support.
     * <p>
     * First loads from the current tenant; if not found, falls back to the system tenant.
     *
     * @param <T> the item type
     * @param itemId the identifier of the item to load
     * @param itemClass the item class
     * @return the loaded item, or {@code null} if not found in either tenant
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
     * Saves an item to the current tenant.
     * <p>
     * Sets the item's tenant identifier from the current execution context before persisting.
     *
     * @param item the item to save
     */
    protected void saveWithTenant(Item item) {
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        if (currentTenant != null) {
            item.setTenantId(currentTenant);
        }
        persistenceService.save(item);
    }

    /**
     * Retrieves metadata for items matching a query in the current tenant.
     *
     * @param <T> the metadata item type
     * @param query the query to execute
     * @param clazz the item class
     * @return a partial list of metadata, or empty if no tenant context is set
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
     * Creates a condition that filters items by tenant identifier.
     *
     * @param tenantId the tenant identifier
     * @return a condition matching the given tenant
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
     * Combines a query condition with a tenant filter using a logical AND.
     *
     * @param queryCondition the user query condition
     * @param tenantCondition the tenant filter condition
     * @return the combined condition
     */
    protected Condition combineTenantCondition(Condition queryCondition, Condition tenantCondition) {
        Condition finalCondition = new Condition();
        finalCondition.setConditionTypeId("booleanCondition");
        finalCondition.setParameter("operator", "and");
        finalCondition.setParameter("subConditions", Arrays.asList(queryCondition, tenantCondition));
        return finalCondition;
    }

    /**
     * Converts a partial list of metadata items to a partial list of {@link Metadata}.
     *
     * @param <T> the metadata item type
     * @param items the source items
     * @return the converted metadata list with the same paging metadata
     */
    protected <T extends MetadataItem> PartialList<Metadata> convertToMetadataList(PartialList<T> items) {
        List<Metadata> metadatas = new LinkedList<>();
        for (T item : items.getList()) {
            metadatas.add(item.getMetadata());
        }
        return new PartialList<>(metadatas, items.getOffset(), items.getPageSize(), items.getTotalSize(), items.getTotalSizeRelation());
    }

    /**
     * Determines whether the current execution context is the system tenant.
     *
     * @return {@code true} if the current tenant is the system tenant
     */
    protected boolean isSystemTenant() {
        String currentTenant =  contextManager.getCurrentContext().getTenantId();
        return SYSTEM_TENANT.equals(currentTenant);
    }

    /**
     * Executes an operation in the system tenant context.
     *
     * @param operation the operation to execute
     */
    protected void executeAsSystem(Runnable operation) {
        contextManager.executeAsSystem(operation);
    }

    /**
     * Executes an operation in the system tenant context and returns its result.
     *
     * @param <T> the result type
     * @param operation the operation to execute
     * @return the value returned by the operation
     */
    protected <T> T executeAsSystem(Supplier<T> operation) {
        return contextManager.executeAsSystem(operation);
    }

}
