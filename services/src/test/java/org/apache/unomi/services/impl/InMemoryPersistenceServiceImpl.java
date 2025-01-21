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

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.BaseAggregate;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

/**
 * An in-memory implementation of PersistenceService for testing purposes.
 */
public class InMemoryPersistenceServiceImpl implements PersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryPersistenceServiceImpl.class);

    private final Map<String, Item> itemsById = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> propertyMappings = new ConcurrentHashMap<>();
    private final ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private final TenantService tenantService;

    public InMemoryPersistenceServiceImpl(TenantService tenantService, ConditionEvaluatorDispatcher conditionEvaluatorDispatcher) {
        this.tenantService = tenantService;
        this.conditionEvaluatorDispatcher = conditionEvaluatorDispatcher;
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
        if (item.getItemId() == null) {
            return false;
        }
        item.setTenantId(tenantService.getCurrentTenantId());
        itemsById.put(getKey(item.getItemId(), item.getClass()), item);
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
        Item item = itemsById.get(getKey(itemId, clazz));
        if (item != null && clazz.isAssignableFrom(item.getClass()) && tenantService.getCurrentTenantId().equals(item.getTenantId())) {
            return (T) item;
        }
        return null;
    }

    @Override
    public <T extends Item> T load(String itemId, Date dateHint, Class<T> clazz) {
        return load(itemId, clazz);
    }

    @Override
    public <T extends Item> boolean remove(String itemId, Class<T> clazz) {
        Item item = itemsById.get(getKey(itemId, clazz));
        if (item != null && clazz.isAssignableFrom(item.getClass()) && tenantService.getCurrentTenantId().equals(item.getTenantId())) {
            Item removedItem = itemsById.remove(getKey(itemId, clazz));
            return removedItem != null;
        }
        return false;
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
        return itemsById.values().stream()
                .filter(item -> clazz.isAssignableFrom(item.getClass()) && tenantService.getCurrentTenantId().equals(item.getTenantId()))
                .map(item -> (T) item)
                .collect(Collectors.toList());
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy, String scrollTimeValidity) {
        return getAllItems(clazz, offset, size, sortBy);
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy) {
        List<T> items = itemsById.values().stream()
                .filter(item -> clazz.isAssignableFrom(item.getClass()) && tenantService.getCurrentTenantId().equals(item.getTenantId()))
                .map(item -> (T) item)
                .collect(Collectors.toList());

        if (sortBy != null) {
            Collections.sort(items, (o1, o2) -> {
                try {
                    String[] sortByArray = sortBy.split(":");
                    String propertyName = sortByArray[0];
                    String sortOrder = sortByArray.length > 1 ? sortByArray[1] : "asc";
                    Object propertyValue1 = PropertyUtils.getProperty(o1, propertyName);
                    Object propertyValue2 = PropertyUtils.getProperty(o2, propertyName);
                    if (propertyValue1 == null && propertyValue2 == null) {
                        return 0;
                    } else if (propertyValue1 == null) {
                        return "desc".equals(sortOrder) ? 1 : -1;
                    } else if (propertyValue2 == null) {
                        return "desc".equals(sortOrder) ? -1 : 1;
                    }
                    if (!(propertyValue1 instanceof Comparable)) {
                        return 0;
                    }
                    int comparisonResult = ((Comparable) propertyValue1).compareTo(propertyValue2);
                    return "desc".equals(sortOrder) ? -comparisonResult : comparisonResult;
                } catch (Exception e) {
                    return 0;
                }
            });
        }

        int totalSize = items.size();
        if (size == -1) {
            // Return all items when size is -1
            return new PartialList<>(items, offset, items.size(), totalSize, PartialList.Relation.EQUAL);
        }

        int fromIndex = offset;
        int toIndex = Math.min(offset + size, items.size());
        if (fromIndex > items.size()) {
            fromIndex = 0;
            toIndex = 0;
        }
        items = items.subList(fromIndex, toIndex);

        return new PartialList<>(items, offset, items.size(), totalSize, PartialList.Relation.EQUAL);
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

        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
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

        try {
            // Try direct map access first
            Object value = getValueFromPath(item, fieldName);
            if (value != null) {
                if (value instanceof Collection) {
                    return ((Collection<?>) value).contains(fieldValue);
                }
                return value.toString().equals(fieldValue);
            }

            // If direct access fails, try path-based access
            value = getValueFromPath(item, fieldName);
            if (value == null) {
                return false;
            }

            if (value instanceof Collection) {
                return ((Collection<?>) value).contains(fieldValue);
            }

            return value.toString().equals(fieldValue);
        } catch (Exception e) {
            return false;
        }
    }

    private Object getValueFromPath(Object obj, String path) {
        if (obj == null || path == null) {
            return null;
        }

        try {
            Object current = obj;
            StringBuilder currentPart = new StringBuilder();
            boolean inBrackets = false;
            boolean escaped = false;

            for (int i = 0; i < path.length(); i++) {
                char c = path.charAt(i);

                if (escaped) {
                    currentPart.append(c);
                    escaped = false;
                    continue;
                }

                switch (c) {
                    case '\\':
                        escaped = true;
                        break;
                    case '[':
                        if (currentPart.length() > 0) {
                            current = resolveValue(current, currentPart.toString());
                            currentPart = new StringBuilder();
                        }
                        inBrackets = true;
                        break;
                    case ']':
                        if (inBrackets) {
                            current = resolveValue(current, currentPart.toString());
                            currentPart = new StringBuilder();
                            inBrackets = false;
                        } else {
                            currentPart.append(c);
                        }
                        break;
                    case '.':
                        if (!inBrackets) {
                            if (currentPart.length() > 0) {
                                current = resolveValue(current, currentPart.toString());
                                currentPart = new StringBuilder();
                            }
                        } else {
                            currentPart.append(c);
                        }
                        break;
                    default:
                        currentPart.append(c);
                }
            }

            // Handle any remaining part
            if (currentPart.length() > 0) {
                current = resolveValue(current, currentPart.toString());
            }

            return current;
        } catch (Exception e) {
            LOGGER.debug("Error accessing path: " + path, e);
            return null;
        }
    }

    private Object resolveValue(Object obj, String key) throws Exception {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).get(key);
        }

        // Try getter method first
        try {
            String getterName = "get" + key.substring(0, 1).toUpperCase() + key.substring(1);
            Method getter = obj.getClass().getMethod(getterName);
            return getter.invoke(obj);
        } catch (NoSuchMethodException e) {
            // Try boolean getter
            try {
                String isName = "is" + key.substring(0, 1).toUpperCase() + key.substring(1);
                Method isGetter = obj.getClass().getMethod(isName);
                return isGetter.invoke(obj);
            } catch (NoSuchMethodException e2) {
                // Try field access
                try {
                    Field field = obj.getClass().getDeclaredField(key);
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (NoSuchFieldException e3) {
                    return null;
                }
            }
        }
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

    @Override
    public void setPropertyMapping(PropertyType property, String itemType) {
        Map<String, Map<String, Object>> mappings = propertyMappings.computeIfAbsent(itemType, k -> new HashMap<>());
        Map<String, Object> properties = mappings.computeIfAbsent("properties", k -> new HashMap<>());
        Map<String, Object> fieldProperties = (Map<String,Object>) properties.computeIfAbsent("properties", k -> new HashMap<>());
        fieldProperties.put(property.getItemId(), Collections.emptyMap());
    }

    @Override
    public Map<String, Map<String, Object>> getPropertiesMapping(String itemType) {
        return propertyMappings.get(itemType);
    }

    @Override
    public Map<String, Object> getPropertyMapping(String property, String itemType) {
        Map<String, Map<String, Object>> mappings = propertyMappings.get(itemType);
        if (mappings == null || !mappings.containsKey("properties")) {
            return null;
        }
        return (Map<String, Object>) mappings.get("properties").get(property);
    }

    // Other required methods with default no-op implementations
    @Override public void refresh() {}
    @Override public void purge(Date date) {}
    @Override public void purge(String scope) {}
    @Override public <T extends Item> void refreshIndex(Class<T> clazz, Date dateHint) {}
    @Override public void createMapping(String itemType, String mappingConfig) {}
    @Override public boolean removeIndex(String itemType) { return true; }
    @Override public boolean createIndex(String itemType) { return true; }
    @Override public boolean removeQuery(String queryString) { return true; }
    @Override public boolean saveQuery(String queryString, Condition condition) { return true; }
    @Override public boolean testMatch(Condition condition, Item item) {
        if (condition == null || item == null) {
            return false;
        }
        return isValidCondition(condition, item);
    }
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
