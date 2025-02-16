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

import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.DateRange;
import org.apache.unomi.api.query.IpRange;
import org.apache.unomi.api.query.NumericRange;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.*;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An in-memory implementation of PersistenceService for testing purposes.
 */
public class InMemoryPersistenceServiceImpl implements PersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryPersistenceServiceImpl.class);
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("[^a-zA-Z0-9-_.]");
    public static final String DEFAULT_STORAGE_DIR = "data/persistence";

    private final Map<String, Item> itemsById = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> propertyMappings = new ConcurrentHashMap<>();
    private final Map<String, ScrollState> scrollStates = new ConcurrentHashMap<>();
    private final ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private final ExecutionContextManager executionContextManager;
    private final CustomObjectMapper objectMapper;
    private final Path storageRootPath;
    private final boolean fileStorageEnabled;
    private final boolean clearStorageOnInit;
    private final boolean prettyPrintJson;

    private static class ScrollState {
        private final List<Item> items;
        private final int currentPosition;
        private final long expiryTime;
        private final int pageSize;

        public ScrollState(List<Item> items, int currentPosition, long expiryTime, int pageSize) {
            this.items = items;
            this.currentPosition = currentPosition;
            this.expiryTime = expiryTime;
            this.pageSize = pageSize;
        }
    }

    public InMemoryPersistenceServiceImpl(ExecutionContextManager executionContextManager, ConditionEvaluatorDispatcher conditionEvaluatorDispatcher) {
        this(executionContextManager, conditionEvaluatorDispatcher, DEFAULT_STORAGE_DIR, true, true, true);
    }

    public InMemoryPersistenceServiceImpl(ExecutionContextManager executionContextManager, ConditionEvaluatorDispatcher conditionEvaluatorDispatcher, String storageDir) {
        this(executionContextManager, conditionEvaluatorDispatcher, storageDir, true, true, true);
    }

    public InMemoryPersistenceServiceImpl(ExecutionContextManager executionContextManager, ConditionEvaluatorDispatcher conditionEvaluatorDispatcher, String storageDir, boolean enableFileStorage) {
        this(executionContextManager, conditionEvaluatorDispatcher, storageDir, enableFileStorage, true, true);
    }

    public InMemoryPersistenceServiceImpl(ExecutionContextManager executionContextManager, ConditionEvaluatorDispatcher conditionEvaluatorDispatcher,
            String storageDir, boolean enableFileStorage, boolean clearStorageOnInit, boolean prettyPrintJson) {
        this.executionContextManager = executionContextManager;
        this.conditionEvaluatorDispatcher = conditionEvaluatorDispatcher;
        this.fileStorageEnabled = enableFileStorage;
        this.clearStorageOnInit = clearStorageOnInit;
        this.prettyPrintJson = prettyPrintJson;

        if (fileStorageEnabled) {
            this.objectMapper = new CustomObjectMapper();
            if (prettyPrintJson) {
                this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            }
            this.storageRootPath = Paths.get(storageDir).toAbsolutePath().normalize();
            LOGGER.info("Using storage root path: {}", storageRootPath);

            // Create storage directory if it doesn't exist
            try {
                if (clearStorageOnInit && Files.exists(storageRootPath)) {
                    // Delete all contents of the storage directory
                    Files.walk(storageRootPath)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                LOGGER.error("Failed to delete path: {}", path, e);
                            }
                        });
                }
                Files.createDirectories(storageRootPath);
                loadPersistedItems();
            } catch (IOException e) {
                LOGGER.error("Failed to create or access storage directory: {}", storageRootPath, e);
                throw new RuntimeException("Failed to initialize storage", e);
            }
        } else {
            this.objectMapper = null;
            this.storageRootPath = null;
        }
    }

    private void loadPersistedItems() {
        if (!fileStorageEnabled) {
            return;
        }

        try {
            if (Files.exists(storageRootPath)) {
                Files.walk(storageRootPath)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(this::loadItemFromFile);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load persisted items from {}", storageRootPath, e);
        }
    }

    private void loadItemFromFile(Path filePath) {
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            Item item = objectMapper.readValue(json, Item.class);
            itemsById.put(getKey(item.getItemId(), item.getClass()), item);
        } catch (IOException e) {
            LOGGER.error("Failed to load item from file: {}", filePath, e);
        }
    }

    private String sanitizePathComponent(String input) {
        if (input == null || input.isEmpty()) {
            return "_";
        }

        // Normalize to ASCII and replace dots with underscores
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("[^\\x00-\\x7F]", "")
            .replace('.', '_');

        // Replace unsafe characters with underscore
        String safe = SAFE_FILENAME_PATTERN.matcher(normalized).replaceAll("_");

        // Ensure the string is not empty and doesn't start with a dot
        if (safe.isEmpty() || safe.startsWith("_")) {
            safe = "x" + safe;
        }

        return safe;
    }

    private Path getItemPath(Item item) {
        String className = sanitizePathComponent(item.getClass().getName());
        String tenantId = sanitizePathComponent(item.getTenantId());
        String itemId = sanitizePathComponent(item.getItemId());

        return storageRootPath.resolve(className)
            .resolve(tenantId)
            .resolve(itemId + ".json");
    }

    private void persistItem(Item item) {
        if (!fileStorageEnabled) {
            return;
        }

        Path itemPath = getItemPath(item);
        try {
            Files.createDirectories(itemPath.getParent());
            String json = objectMapper.writeValueAsString(item);
            Files.writeString(itemPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to persist item to file: {}", itemPath, e);
            throw new RuntimeException("Failed to persist item", e);
        }
    }

    private void deleteItemFile(Item item) {
        if (!fileStorageEnabled) {
            return;
        }

        Path itemPath = getItemPath(item);
        try {
            Files.deleteIfExists(itemPath);

            // Try to clean up empty directories
            Path parent = itemPath.getParent();
            while (parent != null && !parent.equals(storageRootPath)) {
                if (Files.list(parent).findFirst().isEmpty()) {
                    Files.delete(parent);
                    parent = parent.getParent();
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to delete item file: {}", itemPath, e);
        }
    }

    @Override
    public boolean isValidCondition(Condition condition, Item item) {
        if (condition == null) {
            return false;
        }
        if (conditionEvaluatorDispatcher == null) {
            throw new IllegalStateException("ConditionEvaluatorDispatcher is not set");
        }
        try {
            conditionEvaluatorDispatcher.eval(condition, item);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private <T extends Item> String getKey(String itemId, Class<T> clazz) {
        return clazz.getName() + ":" + itemId + ":" + executionContextManager.getCurrentContext().getTenantId();
    }

    @Override
    public String getName() {
        return "InMemoryPersistenceServiceImpl";
    }

    @Override
    public boolean save(Item item) {
        if (item.getItemId() == null) {
            item.setItemId(UUID.randomUUID().toString());
        }
        return save(item, false, true);
    }

    @Override
    public boolean save(Item item, boolean useBatching) {
        return save(item, useBatching, true);
    }

    @Override
    public boolean save(Item item, Boolean useBatching, Boolean alwaysOverwrite) {
        if (item == null) {
            return false;
        }

        item.setTenantId(executionContextManager.getCurrentContext().getTenantId());

        // Handle versioning
        String key = getKey(item.getItemId(), item.getClass());
        Item existingItem = itemsById.get(key);
        if ((existingItem == null || existingItem.getVersion() == null) && (item.getVersion() == null)) {
            // New item or item without version, set initial version
            item.setVersion(1L);
        } else {
            // Existing item being updated, increment version
            if (existingItem != null && existingItem.getVersion() != null) {
                item.setVersion(existingItem.getVersion() + 1);
            } else {
                item.setVersion(item.getVersion() + 1);
            }
        }

        itemsById.put(key, item);

        if (fileStorageEnabled) {
            persistItem(item);
        }

        return true;
    }

    @Override
    public <T extends Item> T load(String itemId, Class<T> clazz) {
        Item item = itemsById.get(getKey(itemId, clazz));
        if (item != null && clazz.isAssignableFrom(item.getClass()) && executionContextManager.getCurrentContext().getTenantId().equals(item.getTenantId())) {
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
        String key = getKey(itemId, clazz);
        Item item = itemsById.get(key);
        if (item != null && clazz.isAssignableFrom(item.getClass()) &&
            executionContextManager.getCurrentContext().getTenantId().equals(item.getTenantId())) {
            itemsById.remove(key);
            deleteItemFile(item);
            return true;
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
                .filter(item -> clazz.isAssignableFrom(item.getClass()) && executionContextManager.getCurrentContext().getTenantId().equals(item.getTenantId()))
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
                .filter(item -> clazz.isAssignableFrom(item.getClass()) && executionContextManager.getCurrentContext().getTenantId().equals(item.getTenantId()))
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
        int fromIndex = Math.min(offset, totalSize);
        int toIndex = size < 0 ? totalSize : Math.min(offset + size, totalSize);

        items = items.subList(fromIndex, toIndex);

        return new PartialList<>(items, offset, size, totalSize, PartialList.Relation.EQUAL);
    }

    @Override
    public <T extends Item> List<T> query(Condition condition, String sortBy, Class<T> clazz) {
        List<T> allItems = getAllItems(clazz);
        List<T> matchingItems = new ArrayList<>();

        for (T item : allItems) {
            if (testMatch(condition, item)) {
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
            if (testMatch(condition, item)) {
                matchingItems.add(item);
            }
        }

        int totalSize = matchingItems.size();
        int fromIndex = Math.min(offset, totalSize);
        int toIndex = size < 0 ? totalSize : Math.min(offset + size, totalSize);

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
        int totalSize = items.size();
        int fromIndex = Math.min(offset, totalSize);
        int toIndex = size < 0 ? totalSize : Math.min(offset + size, totalSize);

        List<T> pageItems = items.subList(fromIndex, toIndex);
        return new PartialList<>(pageItems, offset, size, totalSize, PartialList.Relation.EQUAL);
    }

    @Override
    public <T extends Item> PartialList<T> rangeQuery(String fieldName, String from, String to, String sortBy, Class<T> clazz, int offset, int size) {
        return new PartialList<>(Collections.<T>emptyList(), offset, size, 0, PartialList.Relation.EQUAL);
    }

    @Override
    public <T extends Item> PartialList<T> query(Condition condition, String sortBy, Class<T> clazz, int offset, int size, String scrollTimeValidity) {
        List<T> matchingItems = query(condition, sortBy, clazz);
        int totalSize = matchingItems.size();

        // If size is negative, return all items without scroll
        if (size < 0) {
            List<T> pageItems = matchingItems.subList(offset, totalSize);
            return new PartialList<>(pageItems, offset, size, totalSize, PartialList.Relation.EQUAL);
        }

        if (scrollTimeValidity != null) {
            // Don't create scroll state for empty results or when all items fit in one page
            if (totalSize == 0 || totalSize <= size) {
                List<T> pageItems = matchingItems.subList(0, totalSize);
                return new PartialList<>(pageItems, 0, size, totalSize, PartialList.Relation.EQUAL);
            }

            // Generate a unique scroll ID
            String scrollId = UUID.randomUUID().toString();
            // Parse scroll time validity (assuming it's in milliseconds)
            long validityTime = Long.parseLong(scrollTimeValidity);
            // Store scroll state with filtered items
            scrollStates.put(scrollId, new ScrollState(new ArrayList<>(matchingItems), size, System.currentTimeMillis() + validityTime, size));
            // Return first page with scroll ID
            List<T> pageItems = matchingItems.subList(0, size);
            PartialList<T> partialList = new PartialList<>(pageItems, 0, size, totalSize, PartialList.Relation.EQUAL);
            partialList.setScrollIdentifier(scrollId);
            partialList.setScrollTimeValidity(scrollTimeValidity);
            return partialList;
        }

        int fromIndex = Math.min(offset, totalSize);
        int toIndex = size < 0 ? totalSize : Math.min(offset + size, totalSize);
        List<T> pageItems = matchingItems.subList(fromIndex, toIndex);
        return new PartialList<>(pageItems, offset, size, totalSize, PartialList.Relation.EQUAL);
    }

    @Override
    public void setPropertyMapping(PropertyType property, String itemType) {
        Map<String, Map<String, Object>> mappings = propertyMappings.computeIfAbsent(itemType, k -> new HashMap<>());
        Map<String, Object> properties = mappings.computeIfAbsent("properties", k -> new HashMap<>());
        Map<String, Object> fieldProperties = (Map<String,Object>) properties.computeIfAbsent("properties", k -> new HashMap<>());

        // Convert PropertyType to Map using CustomObjectMapper
        try {
            // First convert to JSON string then back to Map to ensure proper conversion
            String jsonString = objectMapper.writeValueAsString(property);
            @SuppressWarnings("unchecked")
            Map<String, Object> propertyMap = objectMapper.readValue(jsonString, Map.class);
            fieldProperties.put(property.getItemId(), propertyMap);
        } catch (IOException e) {
            LOGGER.error("Error converting PropertyType to Map", e);
            // Fallback to simple conversion if JSON conversion fails
            fieldProperties.put(property.getItemId(), Collections.emptyMap());
        }
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
        Map<String,Object> properties = (Map<String, Object>) mappings.get("properties");
        Map<String,Object> fieldProperties = (Map<String,Object>) properties.computeIfAbsent("properties", k -> new HashMap<>());
        return (Map<String,Object>) fieldProperties.get(property);
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
        if (condition == null) {
            return true;
        }
        if (item == null) {
            return false;
        }
        if (conditionEvaluatorDispatcher == null) {
            throw new IllegalStateException("ConditionEvaluatorDispatcher is not set");
        }
        return conditionEvaluatorDispatcher.eval(condition, item);
    }
    @Override public long getAllItemsCount(String itemType) { return 0; }
    @Override public Map<String, Double> getSingleValuesMetrics(Condition condition, String[] metrics, String field, String itemType) {
        if (metrics == null || metrics.length == 0 || field == null) {
            return Collections.emptyMap();
        }

        // Get all items of the specified type that match the condition
        List<Item> matchingItems = itemsById.values().stream()
                .filter(item -> item.getItemType().equals(itemType))
                .filter(item -> condition == null || testMatch(condition, item))
                .collect(Collectors.toList());

        Map<String, Double> results = new HashMap<>();

        // Extract field values from matching items
        List<Number> numericValues = matchingItems.stream()
                .map(item -> {
                    Object value = getPropertyValue(item, field);
                    return value instanceof Number ? (Number) value : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Calculate requested metrics
        for (String metric : metrics) {
            switch (metric.toLowerCase()) {
                case "card":
                    results.put("_card", (double) matchingItems.stream()
                            .map(item -> getPropertyValue(item, field))
                            .filter(Objects::nonNull)
                            .distinct()
                            .count());
                    break;
                case "sum":
                    if (!numericValues.isEmpty()) {
                        results.put("_sum", numericValues.stream()
                                .mapToDouble(Number::doubleValue)
                                .sum());
                    }
                    break;
                case "min":
                    if (!numericValues.isEmpty()) {
                        results.put("_min", numericValues.stream()
                                .mapToDouble(Number::doubleValue)
                                .min()
                                .orElse(0.0));
                    }
                    break;
                case "max":
                    if (!numericValues.isEmpty()) {
                        results.put("_max", numericValues.stream()
                                .mapToDouble(Number::doubleValue)
                                .max()
                                .orElse(0.0));
                    }
                    break;
                case "avg":
                    if (!numericValues.isEmpty()) {
                        results.put("_avg", numericValues.stream()
                                .mapToDouble(Number::doubleValue)
                                .average()
                                .orElse(0.0));
                    }
                    break;
            }
        }

        return results;
    }
    @Override public boolean update(Item item, Date dateHint, Class<?> clazz, Map<?, ?> sourceMap) { return true; }
    @Override public boolean update(Item item, Date dateHint, Class<?> clazz, String propertyName, Object propertyValue) { return true; }
    @Override public boolean update(Item item, Date dateHint, Class<?> clazz, Map<?, ?> sourceMap, boolean noScriptCall) { return true; }
    @Override public List<String> update(Map<Item, Map> items, Date dateHint, Class clazz) { return new ArrayList<>(); }
    @Override public boolean updateWithScript(Item item, Class<?> clazz, String script, Map<String, Object> scriptParams) {
        return updateWithScript(item, null, clazz, script, scriptParams);
    }

    @Override
    public boolean updateWithScript(Item item, Date dateHint, Class<?> clazz, String script, Map<String, Object> scriptParams) {
        if (item == null || script == null) {
            return false;
        }

        // Execute the script based on its name/content
        boolean success = false;
        if (script.contains("updatePastEventOccurences")) {
            success = executeUpdatePastEventOccurrencesScript(item, scriptParams);
        } else if (script.contains("updateProfileId")) {
            success = executeUpdateProfileIdScript(item, scriptParams);
        } else if (script.contains("resetScoringPlan")) {
            success = executeResetScoringPlanScript(item, scriptParams);
        } else if (script.contains("evaluateScoringPlanElement")) {
            success = executeEvaluateScoringPlanElementScript(item, scriptParams);
        }

        // Log warning if script wasn't matched
        if (!success) {
            String sanitizedScript = script.replaceAll("[\\r\\n]", "").substring(0, Math.min(script.length(), 100));
            LOGGER.warn("No matching script handler found for script: {}", sanitizedScript);
        }

        return success;
    }

    private boolean executeResetScoringPlanScript(Item item, Map<String, Object> params) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> systemProperties = (Map<String, Object>) PropertyUtils.getProperty(item, "systemProperties");
            if (systemProperties == null) {
                systemProperties = new HashMap<>();
                PropertyUtils.setProperty(item, "systemProperties", systemProperties);
            }

            systemProperties.remove("scores");
            systemProperties.remove("scoringPlan");

            save(item);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error executing resetScoringPlan script", e);
            return false;
        }
    }

    private boolean executeEvaluateScoringPlanElementScript(Item item, Map<String, Object> params) {
        try {
            String scoringPlanId = (String) params.get("scoringPlanId");
            String elementId = (String) params.get("elementId");
            Integer score = (Integer) params.get("score");

            @SuppressWarnings("unchecked")
            Map<String, Object> systemProperties = (Map<String, Object>) PropertyUtils.getProperty(item, "systemProperties");
            if (systemProperties == null) {
                systemProperties = new HashMap<>();
                PropertyUtils.setProperty(item, "systemProperties", systemProperties);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> scores = (Map<String, Object>) systemProperties.computeIfAbsent("scores", k -> new HashMap<>());
            scores.put(scoringPlanId + "--" + elementId, score);

            save(item);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error executing evaluateScoringPlanElement script", e);
            return false;
        }
    }

    private boolean executeViewEventPagePathMigrationScript(Item item, Map<String, Object> params) {
        try {
            if (!"view".equals(item.getItemType())) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> target = (Map<String, Object>) PropertyUtils.getProperty(item, "target");
            if (target == null || !target.containsKey("properties")) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) target.get("properties");
            if (properties == null || !properties.containsKey("pageInfo")) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> pageInfo = (Map<String, Object>) properties.get("pageInfo");
            if (pageInfo == null || !pageInfo.containsKey("parameters")) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> flattenedProperties = (Map<String, Object>) PropertyUtils.getProperty(item, "flattenedProperties");
            if (flattenedProperties == null) {
                flattenedProperties = new HashMap<>();
                PropertyUtils.setProperty(item, "flattenedProperties", flattenedProperties);
            }

            flattenedProperties.put("URLParameters", pageInfo.get("parameters"));
            pageInfo.remove("parameters");

            save(item);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error executing view event page path migration script", e);
            return false;
        }
    }

    private boolean executeUpdatePastEventsProfileScript(Item item, Map<String, Object> params) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> systemProperties = (Map<String, Object>) PropertyUtils.getProperty(item, "systemProperties");
            if (systemProperties == null || !systemProperties.containsKey("pastEvents")) {
                return false;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pastEvents = (List<Map<String, Object>>) systemProperties.get("pastEvents");
            if (pastEvents == null) {
                return false;
            }

            // Update past events format if needed
            for (Map<String, Object> pastEvent : pastEvents) {
                if (pastEvent.containsKey("properties")) {
                    pastEvent.remove("properties");
                }
            }

            save(item);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error executing update past events profile script", e);
            return false;
        }
    }

    private boolean executeRemovePastEventsSessionScript(Item item, Map<String, Object> params) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> systemProperties = (Map<String, Object>) PropertyUtils.getProperty(item, "systemProperties");
            if (systemProperties == null) {
                return false;
            }

            systemProperties.remove("pastEvents");
            save(item);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error executing remove past events session script", e);
            return false;
        }
    }

    @Override
    public boolean updateWithQueryAndScript(Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) {
        return updateWithQueryAndScript(null, clazz, scripts, scriptParams, conditions);
    }

    @Override
    public boolean updateWithQueryAndScript(Date dateHint, Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) {
        if (scripts == null || scriptParams == null || conditions == null ||
            scripts.length != scriptParams.length || scripts.length != conditions.length) {
            return false;
        }

        boolean success = true;
        for (int i = 0; i < scripts.length; i++) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            List<Item> items = query(conditions[i], null, (Class) clazz);
            for (Item item : items) {
                success &= updateWithScript(item, dateHint, clazz, scripts[i], scriptParams[i]);
            }
        }
        return success;
    }

    @Override
    public boolean updateWithQueryAndStoredScript(Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) {
        return updateWithQueryAndStoredScript(null, clazz, scripts, scriptParams, conditions);
    }

    @Override
    public boolean updateWithQueryAndStoredScript(Date dateHint, Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) {
        return updateWithQueryAndScript(dateHint, clazz, scripts, scriptParams, conditions);
    }

    @Override
    public boolean updateWithQueryAndStoredScript(Class<?>[] classes, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions, boolean waitForComplete) {
        if (classes == null || classes.length != scripts.length) {
            return false;
        }

        boolean success = true;
        for (int i = 0; i < scripts.length; i++) {
            success &= updateWithQueryAndScript(null, classes[i], new String[]{scripts[i]}, new Map[]{scriptParams[i]}, new Condition[]{conditions[i]});
        }
        return success;
    }

    @Override
    public boolean storeScripts(Map<String, String> scripts) {
        // In-memory implementation doesn't need to store scripts
        return true;
    }

    @Override public PartialList<CustomItem> queryCustomItem(Condition condition, String itemType, String fieldName, int size, int offset, String sortBy) { return new PartialList<>(Collections.<CustomItem>emptyList(), offset, size, 0, PartialList.Relation.EQUAL); }
    @Override public CustomItem loadCustomItem(String itemId, Date dateHint, String itemType) { return null; }
    @Override public boolean removeCustomItem(String itemId, String itemType) { return true; }
    @Override public PartialList<CustomItem> continueCustomItemScrollQuery(String scrollIdentifier, String itemType, String fieldName) { return new PartialList<>(Collections.<CustomItem>emptyList(), 0, 0, 0, PartialList.Relation.EQUAL); }
    @Override
    @SuppressWarnings("unchecked")
    public <T extends Item> PartialList<T> continueScrollQuery(Class<T> clazz, String scrollTimeValidity, String scrollIdentifier) {
        if (scrollIdentifier == null) {
            return new PartialList<>(Collections.emptyList(), 0, 0, 0, PartialList.Relation.EQUAL);
        }

        // Clean up expired scroll states
        scrollStates.entrySet().removeIf(entry -> entry.getValue().expiryTime < System.currentTimeMillis());

        ScrollState state = scrollStates.get(scrollIdentifier);
        if (state == null) {
            return new PartialList<>(Collections.emptyList(), 0, 0, 0, PartialList.Relation.EQUAL);
        }

        // Parse scroll time validity
        long validityTime = Long.parseLong(scrollTimeValidity);

        // Get next page of items
        int fromIndex = state.currentPosition;
        int remainingItems = state.items.size() - fromIndex;
        int pageSize = state.pageSize < 0 ? remainingItems : Math.min(state.pageSize, remainingItems);
        int toIndex = fromIndex + pageSize;

        @SuppressWarnings("unchecked")
        List<T> pageItems = state.items.subList(fromIndex, toIndex).stream()
                .map(item -> (T) item)
                .collect(Collectors.toList());

        // Update scroll state with new position
        if (toIndex >= state.items.size()) {
            // End of scroll, remove the state
            scrollStates.remove(scrollIdentifier);
        } else {
            scrollStates.put(scrollIdentifier, new ScrollState(state.items, toIndex, System.currentTimeMillis() + validityTime, state.pageSize));
        }

        PartialList<T> partialList = new PartialList<>(pageItems, fromIndex, pageSize, state.items.size(), PartialList.Relation.EQUAL);
        if (toIndex < state.items.size()) {
            partialList.setScrollIdentifier(scrollIdentifier);
            partialList.setScrollTimeValidity(scrollTimeValidity);
        }
        return partialList;
    }
    @Override public Map<String, Long> aggregateQuery(Condition condition, BaseAggregate aggregate, String itemType) {
        // This is the deprecated version, delegate to the optimized version
        return aggregateWithOptimizedQuery(condition, aggregate, itemType);
    }

    @Override
    public Map<String, Long> aggregateWithOptimizedQuery(Condition condition, BaseAggregate aggregate, String itemType) {
        return aggregateWithOptimizedQuery(condition, aggregate, itemType, -1);
    }

    @Override
    public Map<String, Long> aggregateWithOptimizedQuery(Condition condition, BaseAggregate aggregate, String itemType, int size) {
        Map<String, Long> results = new HashMap<>();

        // Get all items of the specified type
        List<Item> items = itemsById.values().stream()
                .filter(item -> item.getItemType().equals(itemType))
                .filter(item -> condition == null || testMatch(condition, item))
                .collect(Collectors.toList());

        if (aggregate instanceof TermsAggregate) {
            handleTermsAggregate(items, (TermsAggregate) aggregate, results, size);
        } else if (aggregate instanceof DateAggregate) {
            handleDateAggregate(items, (DateAggregate) aggregate, results);
        } else if (aggregate instanceof NumericRangeAggregate) {
            handleNumericRangeAggregate(items, (NumericRangeAggregate) aggregate, results);
        } else if (aggregate instanceof DateRangeAggregate) {
            handleDateRangeAggregate(items, (DateRangeAggregate) aggregate, results);
        } else if (aggregate instanceof IpRangeAggregate) {
            handleIpRangeAggregate(items, (IpRangeAggregate) aggregate, results);
        }

        return results;
    }

    private void handleTermsAggregate(List<Item> items, TermsAggregate aggregate, Map<String, Long> results, int size) {
        Map<Object, Long> fieldValueCounts = items.stream()
                .map(item -> {
                    try {
                        return PropertyUtils.getProperty(item, aggregate.getField());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        value -> value,
                        Collectors.counting()
                ));

        fieldValueCounts.forEach((value, count) ->
            results.put(String.valueOf(value), count));

        if (size > 0 && results.size() > size) {
            Map<String, Long> limitedResults = results.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(size)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                    ));
            results.clear();
            results.putAll(limitedResults);
        }
    }

    private void handleDateAggregate(List<Item> items, DateAggregate aggregate, Map<String, Long> results) {
        Map<Date, Long> dateValueCounts = items.stream()
                .map(item -> {
                    try {
                        Object value = PropertyUtils.getProperty(item, aggregate.getField());
                        return value instanceof Date ? (Date) value : null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        date -> date,
                        Collectors.counting()
                ));

        dateValueCounts.forEach((date, count) ->
                results.put(String.valueOf(date.getTime()), count));
    }

    private void handleNumericRangeAggregate(List<Item> items, NumericRangeAggregate aggregate, Map<String, Long> results) {
        Map<String, Long> rangeValueCounts = items.stream()
                .map(item -> {
                    try {
                        Object value = PropertyUtils.getProperty(item, aggregate.getField());
                        if (value instanceof Number) {
                            double numericValue = ((Number) value).doubleValue();
                            return getRangeKey(numericValue, aggregate.getRanges());
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        range -> range,
                        Collectors.counting()
                ));

        results.putAll(rangeValueCounts);
    }

    private String getRangeKey(double value, List<NumericRange> ranges) {
        for (NumericRange range : ranges) {
            if ((range.getFrom() == null || value >= range.getFrom()) &&
                (range.getTo() == null || value < range.getTo())) {
                return range.getKey();
            }
        }
        return null;
    }

    private void handleDateRangeAggregate(List<Item> items, DateRangeAggregate aggregate, Map<String, Long> results) {
        Map<String, Long> rangeValueCounts = items.stream()
                .map(item -> {
                    try {
                        Object value = PropertyUtils.getProperty(item, aggregate.getField());
                        if (value instanceof Date) {
                            return getDateRangeKey((Date) value, aggregate.getDateRanges());
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        range -> range,
                        Collectors.counting()
                ));

        results.putAll(rangeValueCounts);
    }

    private String getDateRangeKey(Date value, List<DateRange> ranges) {
        for (DateRange range : ranges) {
            Date from = (Date) range.getFrom();
            Date to = (Date) range.getTo();
            if ((from == null || value.compareTo(from) >= 0) &&
                (to == null || value.compareTo(to) < 0)) {
                return range.getKey();
            }
        }
        return null;
    }

    private void handleIpRangeAggregate(List<Item> items, IpRangeAggregate aggregate, Map<String, Long> results) {
        Map<String, Long> rangeValueCounts = items.stream()
                .map(item -> {
                    try {
                        Object value = PropertyUtils.getProperty(item, aggregate.getField());
                        if (value instanceof String) {
                            return getIpRangeKey((String) value, aggregate.getRanges());
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        range -> range,
                        Collectors.counting()
                ));

        results.putAll(rangeValueCounts);
    }

    private String getIpRangeKey(String ip, List<IpRange> ranges) {
        long ipLong = ipToLong(ip);
        for (IpRange range : ranges) {
            long fromIp = ipToLong(range.getFrom());
            long toIp = ipToLong(range.getTo());
            if (ipLong >= fromIp && ipLong <= toIp) {
                return range.getKey();
            }
        }
        return null;
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fieldName, String fieldValue, String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        // First get items matching the field criteria
        List<T> fieldMatches = query(fieldName, fieldValue, sortBy, clazz);

        // Then filter by fulltext search
        List<T> fulltextMatches = fieldMatches.stream()
                .filter(item -> matchesFullText(item, fulltext))
                .collect(Collectors.toList());

        return createPartialList(fulltextMatches, offset, size);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        // Get all items of the specified class
        List<T> allItems = getAllItems(clazz);

        // Filter by fulltext search
        List<T> matches = allItems.stream()
                .filter(item -> matchesFullText(item, fulltext))
                .collect(Collectors.toList());

        return createPartialList(matches, offset, size);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fulltext, Condition query, String sortBy, Class<T> clazz, int offset, int size) {
        // First get items matching the condition
        List<T> conditionMatches = query(query, sortBy, clazz);

        // Then filter by fulltext search
        List<T> matches = conditionMatches.stream()
                .filter(item -> matchesFullText(item, fulltext))
                .collect(Collectors.toList());

        return createPartialList(matches, offset, size);
    }

    private <T extends Item> PartialList<T> createPartialList(List<T> items, int offset, int size) {
        int totalSize = items.size();
        int fromIndex = Math.min(offset, totalSize);
        int toIndex = size < 0 ? totalSize : Math.min(offset + size, totalSize);
        List<T> pageItems = items.subList(fromIndex, toIndex);
        return new PartialList<>(pageItems, offset, size, totalSize, PartialList.Relation.EQUAL);
    }

    private boolean matchesFullText(Item item, String fulltext) {
        if (fulltext == null || fulltext.trim().isEmpty()) {
            return true;
        }

        String lowercaseFulltext = fulltext.toLowerCase();

        // Check standard Item fields
        if (item.getItemId() != null && item.getItemId().toLowerCase().contains(lowercaseFulltext)) {
            return true;
        }
        if (item.getScope() != null && item.getScope().toLowerCase().contains(lowercaseFulltext)) {
            return true;
        }

        // Get all properties using PropertyUtils
        try {
            Map<String, Object> describe = PropertyUtils.describe(item);
            for (Map.Entry<String, Object> entry : describe.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }

                // Skip class property from PropertyUtils.describe()
                if ("class".equals(entry.getKey())) {
                    continue;
                }

                // Handle different value types
                Object value = entry.getValue();
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapValue = (Map<String, Object>) value;
                    if (containsFullText(mapValue, lowercaseFulltext)) {
                        return true;
                    }
                } else if (value instanceof Collection) {
                    Collection<?> collection = (Collection<?>) value;
                    for (Object element : collection) {
                        if (element != null) {
                            if (element instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> mapElement = (Map<String, Object>) element;
                                if (containsFullText(mapElement, lowercaseFulltext)) {
                                    return true;
                                }
                            } else {
                                if (element.toString().toLowerCase().contains(lowercaseFulltext)) {
                                    return true;
                                }
                            }
                        }
                    }
                } else {
                    if (value.toString().toLowerCase().contains(lowercaseFulltext)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error accessing properties via PropertyUtils", e);
        }

        return false;
    }

    private boolean containsFullText(Map<String, Object> properties, String lowercaseFulltext) {
        if (properties == null) {
            return false;
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            // Check the property name itself
            if (entry.getKey().toLowerCase().contains(lowercaseFulltext)) {
                return true;
            }

            // Handle different value types
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) value;
                if (containsFullText(mapValue, lowercaseFulltext)) {
                    return true;
                }
            } else if (value instanceof Collection) {
                Collection<?> collection = (Collection<?>) value;
                for (Object element : collection) {
                    if (element != null) {
                        if (element instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> mapElement = (Map<String, Object>) element;
                            if (containsFullText(mapElement, lowercaseFulltext)) {
                                return true;
                            }
                        } else {
                            if (element.toString().toLowerCase().contains(lowercaseFulltext)) {
                                return true;
                            }
                        }
                    }
                }
            } else {
                if (value.toString().toLowerCase().contains(lowercaseFulltext)) {
                    return true;
                }
            }
        }
        return false;
    }

    private long ipToLong(String ipAddress) {
        if (ipAddress == null) return 0;
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) return 0;
        long result = 0;
        for (String octet : octets) {
            try {
                result = result * 256 + Integer.parseInt(octet);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return result;
    }

    @Override public long queryCount(Condition condition, String itemType) {
        return itemsById.values().stream()
                .filter(item -> item.getItemType().equals(itemType))
                .filter(item -> condition == null || testMatch(condition, item))
                .count();
    }
    @Override public boolean isConsistent(Item item) { return true; }
    @Override public <T extends Item> void purgeTimeBasedItems(int olderThanInDays, Class<T> clazz) { }
    @Override public boolean migrateTenantData(String fromTenantId, String toTenantId, List<String> itemTypes) { return true; }
    @Override public long getApiCallCount(String apiName) { return 0; }
    @Override public long calculateStorageSize(String itemType) { return 0; }

    private Object getPropertyValue(Item item, String field) {
        try {
            return PropertyUtils.getProperty(item, field);
        } catch (Exception e) {
            LOGGER.debug("Error getting property value for field: " + field, e);
            return null;
        }
    }

    private boolean executeUpdatePastEventOccurrencesScript(Item item, Map<String, Object> params) {
        if (!params.containsKey(item.getItemId())) {
            return true;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> pastEventKeyValue = (Map<String, Object>) params.get(item.getItemId());
            String pastEventKey = (String) pastEventKeyValue.get("pastEventKey");
            Long valueToAdd = (Long) pastEventKeyValue.get("valueToAdd");

            // Get or create systemProperties map
            @SuppressWarnings("unchecked")
            Map<String, Object> systemProperties = (Map<String, Object>) PropertyUtils.getProperty(item, "systemProperties");
            if (systemProperties == null) {
                systemProperties = new HashMap<>();
                PropertyUtils.setProperty(item, "systemProperties", systemProperties);
            }

            // Initialize pastEvents list if needed
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pastEvents = (List<Map<String, Object>>) systemProperties.computeIfAbsent("pastEvents", k -> new ArrayList<>());

            // Update or add past event
            boolean exists = false;
            for (Map<String, Object> pastEvent : pastEvents) {
                if (pastEventKey.equals(pastEvent.get("key"))) {
                    pastEvent.put("count", valueToAdd);
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                Map<String, Object> newPastEvent = new HashMap<>();
                newPastEvent.put("key", pastEventKey);
                newPastEvent.put("count", valueToAdd);
                pastEvents.add(newPastEvent);
            }

            // Update lastUpdated timestamp
            systemProperties.put("lastUpdated", new Date());

            // Save the updated item
            save(item);
            return true;

        } catch (Exception e) {
            LOGGER.error("Error executing updatePastEventOccurrences script", e);
            return false;
        }
    }

    private boolean executeUpdateProfileIdScript(Item item, Map<String, Object> params) {
        if (!params.containsKey("profileId")) {
            return false;
        }

        try {
            String newProfileId = (String) params.get("profileId");

            // Update profileId if it exists
            try {
                String currentProfileId = (String) PropertyUtils.getProperty(item, "profileId");
                if (currentProfileId != null && !newProfileId.equals(currentProfileId)) {
                    try {
                        PropertyUtils.setProperty(item, "profileId", newProfileId);
                    } catch (NoSuchMethodException e) {
                        // No setter method, try to set field directly
                        Field profileIdField = item.getClass().getDeclaredField("profileId");
                        profileIdField.setAccessible(true);
                        profileIdField.set(item, newProfileId);
                    }
                }
            } catch (NoSuchMethodException e) {
                // No getter method, try to get field directly
                try {
                    Field profileIdField = item.getClass().getDeclaredField("profileId");
                    profileIdField.setAccessible(true);
                    String currentProfileId = (String) profileIdField.get(item);
                    if (currentProfileId != null && !newProfileId.equals(currentProfileId)) {
                        profileIdField.set(item, newProfileId);
                    }
                } catch (NoSuchFieldException ex) {
                    // Item class doesn't have profileId field, skip update
                }
            }

            // Update inner profile.itemId if it exists
            Profile profile = (Profile) PropertyUtils.getProperty(item, "profile");
            if (profile != null && profile.getItemId() != null && !newProfileId.equals(profile.getItemId())) {
                profile.setItemId(newProfileId);
            }

            // Save the updated item
            save(item);
            return true;

        } catch (Exception e) {
            LOGGER.error("Error executing updateProfileId script", e);
            return false;
        }
    }
}

