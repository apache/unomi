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

import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.BaseAggregate;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.unomi.services.impl.TestTenantService.SYSTEM_TENANT;

/**
 * An in-memory implementation of PersistenceService for testing purposes.
 */
public class InMemoryPersistenceServiceImpl  implements PersistenceService {
    private final Map<String, Item> items = new ConcurrentHashMap<>();
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private final TenantService tenantService;

    public InMemoryPersistenceServiceImpl(TenantService tenantService) {
        initializeConditionEvaluators();
        this.tenantService = tenantService;
    }

    public void initializeConditionEvaluators() {
        this.conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();
    }

    @Override
    public boolean isValidCondition(Condition condition, Item item) {
        if (condition == null) {
            return true;
        }
        if (conditionEvaluatorDispatcher == null) {
            throw new IllegalStateException("ConditionEvaluatorDispatcher is not set");
        }
        return conditionEvaluatorDispatcher.eval(condition, item);
    }

    private <T extends Item> String getKey(String itemId, Class<T> clazz) {
        return clazz.getName() + ":" + itemId + ":" + getCurrentTenantId();
    }

    private String getCurrentTenantId() {
        return tenantService != null ? tenantService.getCurrentTenantId() : SYSTEM_TENANT;
    }

    @Override
    public String getName() {
        return "InMemoryPersistenceServiceImpl";
    }

    @Override
    public boolean save(Item item) {
        if (item.getTenantId() == null) {
            item.setTenantId(getCurrentTenantId());
        }
        items.put(getKey(item.getItemId(), (Class<Item>) item.getClass()), item);
        return true;
    }

    @Override
    public boolean save(Item item, boolean useBatching) {
        return save(item);
    }

    @Override
    public boolean save(Item item, Boolean useBatching, Boolean alwaysOverwrite) {
        return save(item);
    }

    @Override
    public <T extends Item> T load(String itemId, Class<T> clazz) {
        String currentTenant = getCurrentTenantId();
        T item = (T) items.get(getKey(itemId, clazz));

        // If not found in current tenant and current tenant is not system, try system tenant
        if (item == null && currentTenant != null && !currentTenant.equals("system")) {
            String systemKey = clazz.getName() + ":" + itemId + ":system";
            item = (T) items.get(systemKey);
        }

        return item;
    }

    @Override
    public <T extends Item> T load(String itemId, Date dateHint, Class<T> clazz) {
        return load(itemId, clazz);
    }

    @Override
    public <T extends Item> boolean remove(String itemId, Class<T> clazz) {
        // Only remove from current tenant
        items.remove(getKey(itemId, clazz));
        return true;
    }

    @Override
    public <T extends Item> boolean removeByQuery(Condition condition, Class<T> clazz) {
        List<T> itemsToRemove = query(condition, null, clazz);
        for (T item : itemsToRemove) {
            remove(item.getItemId(), clazz);
        }
        return true;
    }

    @Override
    public <T extends Item> List<T> getAllItems(Class<T> clazz) {
        List<T> result = new ArrayList<>();
        String prefix = clazz.getName() + ":";
        String currentTenant = getCurrentTenantId();

        for (Map.Entry<String, Item> entry : items.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                Item item = entry.getValue();
                // Only return items for current tenant or system tenant if current tenant has no override
                if (item.getTenantId() != null && (item.getTenantId().equals(currentTenant) ||
                    (item.getTenantId().equals("system") && !hasCurrentTenantOverride(item.getItemId(), clazz)))) {
                    result.add((T) item);
                }
            }
        }
        return result;
    }

    private <T extends Item> boolean hasCurrentTenantOverride(String itemId, Class<T> clazz) {
        String currentTenant = getCurrentTenantId();
        if ("system".equals(currentTenant)) {
            return false;
        }
        String tenantKey = clazz.getName() + ":" + itemId + ":" + currentTenant;
        return items.containsKey(tenantKey);
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy) {
        List<T> items = getAllItems(clazz);
        List<T> pageItems = items.subList(Math.min(offset, items.size()), Math.min(offset + size, items.size()));
        return new PartialList<>(pageItems, offset, size, items.size(), PartialList.Relation.EQUAL);
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy, String scrollTimeValidity) {
        return getAllItems(clazz, offset, size, sortBy);
    }

    @Override
    public <T extends Item> List<T> query(Condition condition, String sortBy, Class<T> clazz) {
        List<T> allItems = getAllItems(clazz);
        List<T> matchingItems = new ArrayList<>();

        for (T item : allItems) {
            if (isValidCondition(condition, item)) {
                matchingItems.add(item);
            }
        }
        return matchingItems;
    }

    @Override
    public <T extends Item> PartialList<T> query(Condition condition, String sortBy, Class<T> clazz, int offset, int size) {
        List<T> allItems = getAllItems(clazz);
        List<T> matchingItems = new ArrayList<>();

        for (T item : allItems) {
            if (isValidCondition(condition, item)) {
                matchingItems.add(item);
            }
        }

        int totalSize = matchingItems.size();
        int fromIndex = Math.min(offset, totalSize);
        int toIndex = Math.min(offset + size, totalSize);

        List<T> pageItems = matchingItems.subList(fromIndex, toIndex);
        return new PartialList<>(pageItems, offset, size, totalSize, PartialList.Relation.EQUAL);
    }

    @Override
    public <T extends Item> List<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz) {
        List<T> results = new ArrayList<>();
        String prefix = clazz.getName() + ":";

        for (Map.Entry<String, Item> entry : items.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                T item = (T) entry.getValue();
                if (matchesField(item, fieldName, fieldValue)) {
                    results.add(item);
                }
            }
        }
        return results;
    }

    private boolean matchesField(Item item, String fieldName, String fieldValue) {
        if (item == null || fieldName == null || fieldValue == null) {
            return false;
        }

        if (fieldName.startsWith("metadata.")) {
            String metadataField = fieldName.substring("metadata.".length());
            if (item instanceof MetadataItem) {
                Metadata metadata = ((MetadataItem) item).getMetadata();
                if (metadata != null) {
                    if ("tags".equals(metadataField)) {
                        return metadata.getTags() != null && metadata.getTags().contains(fieldValue);
                    } else if ("systemTags".equals(metadataField)) {
                        return metadata.getSystemTags() != null && metadata.getSystemTags().contains(fieldValue);
                    }
                }
            }
        }

        return false;
    }

    @Override
    public <T extends Item> List<T> query(String fieldName, String[] fieldValues, String sortBy, Class<T> clazz) {
        if (fieldValues == null || fieldValues.length == 0) {
            return Collections.emptyList();
        }

        List<T> results = new ArrayList<>();
        for (String fieldValue : fieldValues) {
            results.addAll(query(fieldName, fieldValue, sortBy, clazz));
        }
        return results;
    }

    @Override
    public <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size) {
        List<T> items = query(fieldName, fieldValue, sortBy, clazz);
        List<T> pageItems = items.subList(Math.min(offset, items.size()), Math.min(offset + size, items.size()));
        return new PartialList<>(pageItems, offset, size, items.size(), PartialList.Relation.EQUAL);
    }

    @Override
    public <T extends Item> PartialList<T> rangeQuery(String fieldName, String from, String to, String sortBy, Class<T> clazz, int offset, int size) {
        return new PartialList<>(Collections.<T>emptyList(), offset, size, 0, PartialList.Relation.EQUAL);
    }

    @Override
    public <T extends Item> PartialList<T> query(Condition condition, String sortBy, Class<T> clazz, int offset, int size, String scrollTimeValidity) {
        List<T> matchingItems = query(condition, sortBy, clazz);
        int totalSize = matchingItems.size();
        int fromIndex = Math.min(offset, totalSize);
        int toIndex = Math.min(offset + size, totalSize);

        List<T> pageItems = matchingItems.subList(fromIndex, toIndex);
        return new PartialList<>(pageItems, offset, size, totalSize, PartialList.Relation.EQUAL);
    }

    // Other required methods with default no-op implementations
    @Override public void refresh() {}
    @Override public void purge(Date date) {}
    @Override public void purge(String scope) {}
    @Override public <T extends Item> void refreshIndex(Class<T> clazz, Date dateHint) {}
    @Override public void createMapping(String itemType, String mappingConfig) {}
    @Override public Map<String,Map<String,Object>> getPropertiesMapping(String itemType) { return Collections.emptyMap(); }
    @Override public Map<String,Object> getPropertyMapping(String itemType, String property) { return Collections.emptyMap(); }
    @Override public void setPropertyMapping(PropertyType propertyType, String itemType) {}
    @Override public boolean removeIndex(String itemType) { return true; }
    @Override public boolean createIndex(String itemType) { return true; }
    @Override public boolean removeQuery(String queryString) { return true; }
    @Override public boolean saveQuery(String queryString, Condition condition) { return true; }
    @Override public boolean testMatch(Condition condition, Item item) { return false; }
    @Override public <T extends Item> PartialList<T> queryFullText(String fieldName, String fullTextSearch, Class<T> clazz, int offset, int size) { return new PartialList<>(Collections.<T>emptyList(), offset, size, 0, PartialList.Relation.EQUAL); }
    @Override public <T extends Item> PartialList<T> queryFullText(String fullTextSearch, Condition condition, String sortBy, Class<T> clazz, int offset, int size) { return new PartialList<>(Collections.<T>emptyList(), offset, size, 0, PartialList.Relation.EQUAL); }
    @Override public <T extends Item> PartialList<T> queryFullText(String fieldName, String fullTextSearch, String sortBy, String scrollTimeValidity, Class<T> clazz, int offset, int size) { return new PartialList<>(Collections.<T>emptyList(), offset, size, 0, PartialList.Relation.EQUAL); }
    @Override public long getAllItemsCount(String itemType) { return 0; }
    @Override public Map<String, Double> getSingleValuesMetrics(Condition condition, String[] metrics, String field, String itemType) { return Collections.emptyMap(); }
    @Override public boolean update(Item item, Date dateHint, Class<?> clazz, Map<?, ?> sourceMap) { return true; }
    @Override public boolean update(Item item, Date dateHint, Class<?> clazz, String propertyName, Object propertyValue) { return true; }
    @Override public boolean update(Item item, Date dateHint, Class<?> clazz, Map<?, ?> sourceMap, boolean noScriptCall) { return true; }
    @Override public List<String> update(Map<Item, Map> items, Date dateHint, Class clazz) { return new ArrayList<>(); }
    @Override public boolean updateWithScript(Item item, Date dateHint, Class<?> clazz, String script, Map<String, Object> scriptParams) { return true; }
    @Override public boolean updateWithQueryAndScript(Date dateHint, Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) { return true; }
    @Override public boolean updateWithQueryAndStoredScript(Date dateHint, Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) { return true; }
    @Override public boolean updateWithQueryAndStoredScript(Class<?>[] classes, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions, boolean noScriptCall) { return true; }
    @Override public boolean storeScripts(Map<String, String> mappings) { return true; }
    @Override public PartialList<CustomItem> queryCustomItem(Condition condition, String itemType, String fieldName, int size, int offset, String sortBy) { return new PartialList<>(Collections.<CustomItem>emptyList(), offset, size, 0, PartialList.Relation.EQUAL); }
    @Override public CustomItem loadCustomItem(String itemId, Date dateHint, String itemType) { return null; }
    @Override public boolean removeCustomItem(String itemId, String itemType) { return true; }
    @Override public PartialList<CustomItem> continueCustomItemScrollQuery(String scrollIdentifier, String itemType, String fieldName) { return new PartialList<>(Collections.<CustomItem>emptyList(), 0, 0, 0, PartialList.Relation.EQUAL); }
    @Override public <T extends Item> PartialList<T> continueScrollQuery(Class<T> clazz, String scrollTimeValidity, String scrollIdentifier) { return new PartialList<>(Collections.<T>emptyList(), 0, 0, 0, PartialList.Relation.EQUAL); }
    @Override public Map<String, Long> aggregateWithOptimizedQuery(Condition condition, BaseAggregate aggregate, String itemType) { return Collections.emptyMap(); }
    @Override public Map<String, Long> aggregateWithOptimizedQuery(Condition condition, BaseAggregate aggregate, String itemType, int size) { return Collections.emptyMap(); }
    @Override public Map<String, Long> aggregateQuery(Condition condition, BaseAggregate aggregate, String itemType) { return Collections.emptyMap(); }
    @Override public long queryCount(Condition condition, String itemType) { return 0; }
    @Override public boolean isConsistent(Item item) { return true; }
    @Override public <T extends Item> void purgeTimeBasedItems(int olderThanInDays, Class<T> clazz) { }
    @Override public boolean migrateTenantData(String fromTenantId, String toTenantId, List<String> itemTypes) { return true; }
    @Override public long getApiCallCount(String apiName) { return 0; }
    @Override public long calculateStorageSize(String itemType) { return 0; }
}
