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
import org.apache.unomi.api.tenants.TenantTransformationListener;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.*;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An in-memory implementation of PersistenceService for testing purposes.
 */
public class InMemoryPersistenceServiceImpl implements PersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryPersistenceServiceImpl.class);
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("[^a-zA-Z0-9-_.]");
    public static final String DEFAULT_STORAGE_DIR = "data/persistence";

    // System items list - matches Elasticsearch/OpenSearch persistence services
    // System items have their itemType appended to the document ID: tenantId_itemId_itemType
    private static final Collection<String> systemItems = Arrays.asList("actionType", "campaign", "campaignevent", "goal", "userList",
            "propertyType", "scope", "conditionType", "rule", "scoring", "segment", "groovyAction", "topic", "patch", "jsonSchema",
            "importConfig", "exportConfig", "rulestats");

    private final Map<String, Item> itemsById = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> propertyMappings = new ConcurrentHashMap<>();
    private final Map<String, ScrollState> scrollStates = new ConcurrentHashMap<>();
    private final Map<String, Long> sequenceNumbersByIndex = new ConcurrentHashMap<>();
    private final Map<String, Long> primaryTermsByIndex = new ConcurrentHashMap<>();
    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();
    private final ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private final ExecutionContextManager executionContextManager;
    private final CustomObjectMapper objectMapper;
    private final Path storageRootPath;
    private final boolean fileStorageEnabled;
    private final boolean clearStorageOnInit;
    private final boolean prettyPrintJson;

    // Refresh delay simulation (simulates Elasticsearch/OpenSearch behavior)
    private final boolean simulateRefreshDelay;
    private final long refreshIntervalMs;
    private final Map<String, Long> pendingRefreshItems = new ConcurrentHashMap<>(); // itemKey -> timestamp when it becomes available
    private final Set<String> refreshedIndexes = ConcurrentHashMap.newKeySet(); // indexes that have been refreshed
    private Thread refreshThread;
    private volatile boolean shutdownRefreshThread = false;

    // Refresh policy per item type (simulates Elasticsearch/OpenSearch refresh policies)
    // Valid values: False/NONE (default - wait for automatic refresh), True/IMMEDIATE (immediate refresh), WaitFor/WAIT_UNTIL (wait for refresh)
    private final Map<String, RefreshPolicy> itemTypeToRefreshPolicy = new ConcurrentHashMap<>();

    // Default query limit (simulates Elasticsearch/OpenSearch default query limit)
    private Integer defaultQueryLimit = 10;

    // Tenant transformation listeners (simulates Elasticsearch/OpenSearch tenant transformations)
    private final List<TenantTransformationListener> transformationListeners = new ArrayList<>();

    /**
     * Refresh policy enum that simulates Elasticsearch/OpenSearch refresh behavior.
     *
     * - FALSE/NONE: Don't refresh immediately, wait for automatic refresh (default behavior)
     * - TRUE/IMMEDIATE: Force an immediate refresh after indexing
     * - WAIT_FOR/WAIT_UNTIL: Wait for refresh to complete before returning (similar to True but with different semantics)
     */
    public enum RefreshPolicy {
        /**
         * FALSE/NONE - Don't refresh immediately. Changes become visible after the next automatic refresh.
         * This is the default and most efficient option.
         */
        FALSE,

        /**
         * TRUE/IMMEDIATE - Force an immediate refresh after indexing.
         * Changes are immediately visible but more resource-intensive.
         */
        TRUE,

        /**
         * WAIT_FOR/WAIT_UNTIL - Wait for refresh to complete before returning.
         * Similar to TRUE but ensures the refresh operation completes before the request returns.
         */
        WAIT_FOR
    }

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
        this(executionContextManager, conditionEvaluatorDispatcher, DEFAULT_STORAGE_DIR, true, true, true, true, 1000L);
    }

    public InMemoryPersistenceServiceImpl(ExecutionContextManager executionContextManager, ConditionEvaluatorDispatcher conditionEvaluatorDispatcher, String storageDir) {
        this(executionContextManager, conditionEvaluatorDispatcher, storageDir, true, true, true, true, 1000L);
    }

    public InMemoryPersistenceServiceImpl(ExecutionContextManager executionContextManager, ConditionEvaluatorDispatcher conditionEvaluatorDispatcher, String storageDir, boolean enableFileStorage) {
        this(executionContextManager, conditionEvaluatorDispatcher, storageDir, enableFileStorage, true, true, true, 1000L);
    }

    public InMemoryPersistenceServiceImpl(ExecutionContextManager executionContextManager, ConditionEvaluatorDispatcher conditionEvaluatorDispatcher,
            String storageDir, boolean enableFileStorage, boolean clearStorageOnInit, boolean prettyPrintJson) {
        this(executionContextManager, conditionEvaluatorDispatcher, storageDir, enableFileStorage, clearStorageOnInit, prettyPrintJson, true, 1000L);
    }

    public InMemoryPersistenceServiceImpl(ExecutionContextManager executionContextManager, ConditionEvaluatorDispatcher conditionEvaluatorDispatcher,
            String storageDir, boolean enableFileStorage, boolean clearStorageOnInit, boolean prettyPrintJson, boolean simulateRefreshDelay, long refreshIntervalMs) {
        this(executionContextManager, conditionEvaluatorDispatcher, storageDir, enableFileStorage, clearStorageOnInit, prettyPrintJson, simulateRefreshDelay, refreshIntervalMs, 10);
    }

    public InMemoryPersistenceServiceImpl(ExecutionContextManager executionContextManager, ConditionEvaluatorDispatcher conditionEvaluatorDispatcher,
            String storageDir, boolean enableFileStorage, boolean clearStorageOnInit, boolean prettyPrintJson, boolean simulateRefreshDelay, long refreshIntervalMs, int defaultQueryLimit) {
        this.executionContextManager = executionContextManager;
        this.conditionEvaluatorDispatcher = conditionEvaluatorDispatcher;
        this.fileStorageEnabled = enableFileStorage;
        this.clearStorageOnInit = clearStorageOnInit;
        this.prettyPrintJson = prettyPrintJson;
        this.simulateRefreshDelay = simulateRefreshDelay;
        this.refreshIntervalMs = refreshIntervalMs > 0 ? refreshIntervalMs : 1000L;
        this.defaultQueryLimit = defaultQueryLimit > 0 ? defaultQueryLimit : 10;

        if (fileStorageEnabled) {
            this.objectMapper = new CustomObjectMapper();
            if (prettyPrintJson) {
                this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            }
            this.storageRootPath = Paths.get(storageDir).toAbsolutePath().normalize();
            LOGGER.debug("Using storage root path: {}", storageRootPath);

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
                // For AccessDeniedException (common in tests), log without stack trace to reduce noise
                if (e instanceof AccessDeniedException) {
                    LOGGER.error("Failed to create or access storage directory: {} - {}", storageRootPath, e.getMessage());
                } else {
                    LOGGER.error("Failed to create or access storage directory: {}", storageRootPath, e);
                }
                throw new RuntimeException("Failed to initialize storage", e);
            }
        } else {
            this.objectMapper = null;
            this.storageRootPath = null;
        }

        // Start background refresh thread if refresh delay simulation is enabled
        if (simulateRefreshDelay) {
            startRefreshThread();
        }
    }

    /**
     * Starts the background thread that periodically refreshes indexes, simulating Elasticsearch/OpenSearch behavior.
     * By default, Elasticsearch refreshes indexes every 1 second, making newly indexed documents searchable.
     */
    private void startRefreshThread() {
        refreshThread = new Thread(() -> {
            while (!shutdownRefreshThread) {
                try {
                    Thread.sleep(refreshIntervalMs);
                    performPeriodicRefresh();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Error in refresh thread", e);
                }
            }
        }, "InMemoryPersistenceService-RefreshThread");
        refreshThread.setDaemon(true);
        refreshThread.start();
    }

    /**
     * Performs periodic refresh of pending items, making them available for querying.
     * This simulates Elasticsearch's automatic refresh behavior.
     */
    private void performPeriodicRefresh() {
        long currentTime = System.currentTimeMillis();
        Set<String> itemsToRefresh = new HashSet<>();

        for (Map.Entry<String, Long> entry : pendingRefreshItems.entrySet()) {
            if (entry.getValue() <= currentTime) {
                itemsToRefresh.add(entry.getKey());
            }
        }

        if (!itemsToRefresh.isEmpty()) {
            for (String itemKey : itemsToRefresh) {
                pendingRefreshItems.remove(itemKey);
                // Extract index name from itemKey (format: "index:itemId:tenantId")
                String[] parts = itemKey.split(":", 3);
                if (parts.length >= 1) {
                    refreshedIndexes.add(parts[0]);
                }
            }
            LOGGER.debug("Periodically refreshed {} items", itemsToRefresh.size());
        }
    }

    /**
     * Shuts down the refresh thread. Should be called when the service is being destroyed.
     */
    public void shutdown() {
        shutdownRefreshThread = true;
        if (refreshThread != null) {
            refreshThread.interrupt();
            try {
                refreshThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
            String key = getKey(item.getItemId(), getIndex(item.getClass()));
            itemsById.put(key, item);
            // Items loaded from file are considered immediately available (already persisted)
            // They don't need to wait for refresh
            if (simulateRefreshDelay) {
                String indexName = getIndexName(item);
                refreshedIndexes.add(indexName);
            }
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
        String indexName = getIndexName(item);
        indexName = sanitizePathComponent(indexName);
        String tenantId = sanitizePathComponent(item.getTenantId());
        String itemId = sanitizePathComponent(item.getItemId());

        return storageRootPath.resolve(indexName)
            .resolve(tenantId)
            .resolve(itemId + ".json");
    }

    private void persistItem(Item item) {
        if (!fileStorageEnabled) {
            return;
        }

        Path itemPath = getItemPath(item);
        String pathKey = itemPath.toString();
        Object lock = fileLocks.computeIfAbsent(pathKey, k -> new Object());

        synchronized (lock) {
            // Retry logic for handling transient file system issues in concurrent scenarios
            int maxRetries = 3;
            int retryCount = 0;

            while (retryCount < maxRetries) {
                try {
                    // Create parent directories, handling race conditions where directory might already exist
                    // or be created/deleted by another thread
                    Path parent = itemPath.getParent();
                    if (parent != null) {
                        try {
                            Files.createDirectories(parent);
                        } catch (IOException e) {
                            // Files.createDirectories should not throw if directory exists, so if it throws,
                            // check if directory was created by another thread (race condition)
                            if (Files.exists(parent) && Files.isDirectory(parent)) {
                                // Directory exists (created by another thread), continue
                            } else {
                                // Directory doesn't exist - this might be a transient issue, retry
                                retryCount++;
                                if (retryCount < maxRetries) {
                                    try {
                                        Thread.sleep(10); // Small delay before retry
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException("Interrupted during retry", ie);
                                    }
                                    continue;
                                }
                                LOGGER.error("Failed to create parent directory after {} retries: {}", retryCount, parent, e);
                                throw new RuntimeException("Failed to create parent directory", e);
                            }
                        }
                    }

                    String json = objectMapper.writeValueAsString(item);
                    Files.writeString(itemPath, json, StandardCharsets.UTF_8);
                    // Success - break out of retry loop
                    return;
                } catch (IOException e) {
                    retryCount++;
                    if (retryCount < maxRetries) {
                        // Check if file exists (might have been created by another thread or previous attempt)
                        if (Files.exists(itemPath)) {
                            // File exists, operation might have succeeded - verify by trying to read it
                            try {
                                Files.readString(itemPath);
                                // File exists and is readable, consider operation successful
                                return;
                            } catch (IOException readException) {
                                // File exists but can't be read, might be in use - retry
                            }
                        }
                        try {
                            Thread.sleep(10); // Small delay before retry
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during retry", ie);
                        }
                    } else {
                        LOGGER.error("Failed to persist item to file after {} retries: {}", retryCount, itemPath, e);
                        throw new RuntimeException("Failed to persist item", e);
                    }
                }
            }
        }
    }

    private void deleteItemFile(Item item) {
        if (!fileStorageEnabled) {
            return;
        }

        Path itemPath = getItemPath(item);
        String pathKey = itemPath.toString();
        Object lock = fileLocks.computeIfAbsent(pathKey, k -> new Object());

        synchronized (lock) {
            try {
                Files.deleteIfExists(itemPath);

                // Try to clean up empty directories
                Path parent = itemPath.getParent();
                while (parent != null && !parent.equals(storageRootPath)) {
                    try {
                        if (Files.exists(parent)) {
                            try (Stream<Path> stream = Files.list(parent)) {
                                // Check if directory is truly empty
                                if (stream.findFirst().isEmpty()) {
                                    // Directory is empty, try to delete it
                                    try {
                                        Files.delete(parent);
                                        parent = parent.getParent();
                                    } catch (DirectoryNotEmptyException e) {
                                        // Directory became non-empty between check and delete (race condition)
                                        // This is expected in concurrent scenarios - just stop cleanup
                                        break;
                                    } catch (IOException e) {
                                        // Directory may have been deleted by another thread
                                        // This is expected in concurrent scenarios - just stop cleanup
                                        break;
                                    }
                                } else {
                                    // Directory is not empty, stop cleanup
                                    break;
                                }
                            }
                        } else {
                            // Directory doesn't exist, stop cleanup
                            break;
                        }
                    } catch (IOException e) {
                        // Directory may have been deleted by another thread, or may not be empty
                        // This is expected in concurrent scenarios - just stop cleanup
                        break;
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to delete item file: {}", itemPath, e);
            }
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

    private <T extends Item> String getKey(String itemId, String index) {
        return index + ":" + itemId + ":" + executionContextManager.getCurrentContext().getTenantId();
    }

    /**
     * Get the document ID for an item type, matching Elasticsearch/OpenSearch format.
     * For system items, the format is: tenantId_itemId_itemType
     * For non-system items, the format is: tenantId_itemId
     *
     * @param itemId the item ID
     * @param itemType the item type
     * @return the document ID
     */
    private String getDocumentIDForItemType(String itemId, String itemType) {
        String tenantId = executionContextManager.getCurrentContext().getTenantId();
        String baseId = systemItems.contains(itemType) ? (itemId + "_" + itemType.toLowerCase()) : itemId;
        return tenantId + "_" + baseId;
    }

    /**
     * Strip tenant prefix from document ID, matching Elasticsearch/OpenSearch format.
     *
     * @param documentId the document ID
     * @return the document ID without tenant prefix
     */
    private String stripTenantFromDocumentId(String documentId) {
        if (documentId == null) {
            return null;
        }
        String tenantId = executionContextManager.getCurrentContext().getTenantId();
        if (documentId.startsWith(tenantId + "_")) {
            return documentId.substring(tenantId.length() + 1);
        }
        // Also check for system tenant
        String systemTenant = "system";
        if (documentId.startsWith(systemTenant + "_")) {
            return documentId.substring(systemTenant.length() + 1);
        }
        return documentId;
    }

    /**
     * Extract itemId from document ID for system items, matching Elasticsearch/OpenSearch behavior.
     * For system items, document ID format is: tenantId_itemId_itemType
     * This method removes the tenant prefix and itemType suffix to get the actual itemId.
     *
     * @param documentId the document ID (may include tenant prefix)
     * @param itemType the item type
     * @return the extracted itemId
     */
    private String extractItemIdFromDocumentId(String documentId, String itemType) {
        if (documentId == null) {
            return null;
        }
        String strippedId = stripTenantFromDocumentId(documentId);
        if (!systemItems.contains(itemType)) {
            // For non-system items, the stripped ID is the itemId
            return strippedId;
        } else {
            // For system items, check if there's an itemType suffix and remove it
            // This handles the case where document ID is: tenantId_itemId_itemType
            String itemTypeSuffix = "_" + itemType.toLowerCase();
            if (strippedId != null && strippedId.endsWith(itemTypeSuffix)) {
                return strippedId.substring(0, strippedId.length() - itemTypeSuffix.length());
            }
            // If no suffix, return as-is (might be from old format or migration)
            return strippedId;
        }
    }

    /**
     * Get the sequence number for an item, incrementing it if necessary
     * @param indexName the index name
     * @param increment whether to increment the sequence number
     * @return the sequence number
     */
    private Long getSequenceNumber(String indexName, boolean increment) {
        if (increment) {
            return sequenceNumbersByIndex.compute(indexName, (k, v) -> v != null ? v + 1 : 1L);
        } else {
            return sequenceNumbersByIndex.computeIfAbsent(indexName, k -> 0L);
        }
    }

    /**
     * Get the primary term for an index
     * @param indexName the index name
     * @return the primary term
     */
    private Long getPrimaryTerm(String indexName) {
        return primaryTermsByIndex.computeIfAbsent(indexName, k -> 1L);
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

        // Apply tenant transformations before save (simulates Elasticsearch/OpenSearch behavior)
        item = handleItemTransformation(item);

        String indexName = getIndexName(item);
        String key = getKey(item.getItemId(), indexName);
        Item existingItem = itemsById.get(key);

        // Handle _seq_no and _primary_term fields using system metadata
        // Check for optimistic concurrency control
        if (existingItem != null) {
            Object existingSeqNo = existingItem.getSystemMetadata("_seq_no");
            Object existingPrimaryTerm = existingItem.getSystemMetadata("_primary_term");

            // If the item has _seq_no and _primary_term specified, check them against the existing item
            Object requestedSeqNo = item.getSystemMetadata("_seq_no");
            Object requestedPrimaryTerm = item.getSystemMetadata("_primary_term");

            if (requestedSeqNo != null && requestedPrimaryTerm != null) {
                // If sequence numbers don't match the existing ones, it's a conflict
                if (existingSeqNo != null &&
                    ((Number) requestedSeqNo).longValue() != ((Number) existingSeqNo).longValue()) {
                    LOGGER.warn("Sequence number conflict detected for item {}: requested={}, current={}",
                               item.getItemId(), requestedSeqNo, existingSeqNo);
                    return false;
                }

                // If primary terms don't match, it's a conflict
                if (existingPrimaryTerm != null &&
                    ((Number) requestedPrimaryTerm).longValue() != ((Number) existingPrimaryTerm).longValue()) {
                    LOGGER.warn("Primary term conflict detected for item {}: requested={}, current={}",
                               item.getItemId(), requestedPrimaryTerm, existingPrimaryTerm);
                    return false;
                }
            }
        }

        // Get sequence number for this item
        Long currentSeqNo = getSequenceNumber(indexName, true);

        // Get primary term for this index
        Long currentPrimaryTerm = getPrimaryTerm(indexName);

        // Set the new sequence number and primary term on the item
        item.setSystemMetadata("_seq_no", currentSeqNo);
        item.setSystemMetadata("_primary_term", currentPrimaryTerm);

        // Handle item versioning (the existing version system)
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

        // Handle refresh policy per item type (simulates Elasticsearch/OpenSearch refresh policies)
        // Request-based override (via system metadata) takes precedence over per-item-type policy
        if (simulateRefreshDelay) {
            String itemType = item.getItemType();
            if (item instanceof CustomItem) {
                String customItemType = ((CustomItem) item).getCustomItemType();
                if (customItemType != null) {
                    itemType = customItemType;
                }
            }
            RefreshPolicy refreshPolicy = getRefreshPolicy(itemType, item);

            switch (refreshPolicy) {
                case TRUE:
                case WAIT_FOR:
                    // Immediate refresh: make item available immediately
                    // For WAIT_FOR, we also ensure refresh completes (same behavior as TRUE in in-memory)
                    pendingRefreshItems.remove(key);
                    refreshedIndexes.add(indexName);
                    LOGGER.debug("Immediately refreshed item {} of type {} due to refresh policy {}",
                            item.getItemId(), itemType, refreshPolicy);
                    break;
                case FALSE:
                default:
                    // Default behavior: wait for automatic refresh
                    long refreshTime = System.currentTimeMillis() + refreshIntervalMs;
                    pendingRefreshItems.put(key, refreshTime);
                    // Remove from refreshed indexes set if it was previously refreshed
                    refreshedIndexes.remove(indexName);
                    break;
            }
        }

        return true;
    }

    /**
     * Gets the refresh policy for a given item type.
     * Checks for request-based override in item's system metadata first,
     * then falls back to per-item-type policy, and finally defaults to FALSE.
     *
     * @param itemType the item type
     * @param item the item (may be null) - used to check for request-based override
     * @return the refresh policy for this item type
     */
    private RefreshPolicy getRefreshPolicy(String itemType, Item item) {
        // Check for request-based refresh policy override in system metadata
        // This simulates Elasticsearch/OpenSearch's refresh parameter in API requests
        if (item != null) {
            Object refreshOverride = item.getSystemMetadata("refresh");
            if (refreshOverride != null) {
                if (refreshOverride instanceof RefreshPolicy) {
                    return (RefreshPolicy) refreshOverride;
                } else if (refreshOverride instanceof String) {
                    RefreshPolicy parsed = parseRefreshPolicy((String) refreshOverride);
                    LOGGER.debug("Using request-based refresh policy override: {} for item type {}", parsed, itemType);
                    return parsed;
                } else if (refreshOverride instanceof Boolean) {
                    // Support boolean values: true -> TRUE, false -> FALSE
                    return ((Boolean) refreshOverride) ? RefreshPolicy.TRUE : RefreshPolicy.FALSE;
                }
            }
        }

        // Fall back to per-item-type policy
        return itemTypeToRefreshPolicy.getOrDefault(itemType, RefreshPolicy.FALSE);
    }

    /**
     * Gets the refresh policy for a given item type (without item context).
     * Used when item is not available.
     *
     * @param itemType the item type
     * @return the refresh policy for this item type
     */
    private RefreshPolicy getRefreshPolicy(String itemType) {
        return itemTypeToRefreshPolicy.getOrDefault(itemType, RefreshPolicy.FALSE);
    }

    /**
     * Sets the refresh policy for a specific item type.
     * This simulates Elasticsearch/OpenSearch's itemTypeToRefreshPolicy configuration.
     *
     * @param itemType the item type
     * @param refreshPolicy the refresh policy (FALSE, TRUE, or WAIT_FOR)
     */
    public void setRefreshPolicy(String itemType, RefreshPolicy refreshPolicy) {
        if (itemType != null && refreshPolicy != null) {
            itemTypeToRefreshPolicy.put(itemType, refreshPolicy);
            LOGGER.debug("Set refresh policy for item type {} to {}", itemType, refreshPolicy);
        }
    }

    /**
     * Sets refresh policies from a JSON string, matching Elasticsearch/OpenSearch configuration format.
     * Example: {"event":"WAIT_FOR","rule":"FALSE","scheduledTask":"TRUE"}
     *
     * @param refreshPolicyJson JSON string mapping item types to refresh policies
     */
    public void setItemTypeToRefreshPolicy(String refreshPolicyJson) {
        if (refreshPolicyJson == null || refreshPolicyJson.trim().isEmpty()) {
            return;
        }

        try {
            if (objectMapper != null) {
                @SuppressWarnings("unchecked")
                Map<String, String> policies = objectMapper.readValue(refreshPolicyJson, Map.class);
                for (Map.Entry<String, String> entry : policies.entrySet()) {
                    String itemType = entry.getKey();
                    String policyStr = entry.getValue();
                    try {
                        RefreshPolicy policy = RefreshPolicy.valueOf(policyStr.toUpperCase());
                        setRefreshPolicy(itemType, policy);
                    } catch (IllegalArgumentException e) {
                        // Try to map Elasticsearch/OpenSearch string values
                        RefreshPolicy policy = parseRefreshPolicy(policyStr);
                        setRefreshPolicy(itemType, policy);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse refresh policy JSON: {}", refreshPolicyJson, e);
        }
    }

    /**
     * Parses refresh policy string values from Elasticsearch/OpenSearch configuration.
     * Supports: False/NONE, True/IMMEDIATE, WaitFor/WAIT_UNTIL
     *
     * @param policyStr the policy string
     * @return the corresponding RefreshPolicy enum value
     */
    private RefreshPolicy parseRefreshPolicy(String policyStr) {
        if (policyStr == null) {
            return RefreshPolicy.FALSE;
        }

        String upper = policyStr.toUpperCase();
        if ("FALSE".equals(upper) || "NONE".equals(upper)) {
            return RefreshPolicy.FALSE;
        } else if ("TRUE".equals(upper) || "IMMEDIATE".equals(upper)) {
            return RefreshPolicy.TRUE;
        } else if ("WAITFOR".equals(upper) || "WAIT_FOR".equals(upper) || "WAIT_UNTIL".equals(upper)) {
            return RefreshPolicy.WAIT_FOR;
        }

        LOGGER.warn("Unknown refresh policy value: {}, defaulting to FALSE", policyStr);
        return RefreshPolicy.FALSE;
    }

    /**
     * Checks if an item is available for querying (i.e., has been refreshed).
     * In Elasticsearch/OpenSearch, items are not immediately available after indexing.
     *
     * @param itemKey the item key (format: "index:itemId:tenantId")
     * @param indexName the index name
     * @return true if the item is available for querying, false otherwise
     */
    private boolean isItemAvailableForQuery(String itemKey, String indexName) {
        if (!simulateRefreshDelay) {
            return true; // If refresh delay is disabled, all items are immediately available
        }

        // If the index has been explicitly refreshed, all items in it are available
        if (refreshedIndexes.contains(indexName)) {
            return true;
        }

        // Check if this specific item has passed its refresh time
        Long refreshTime = pendingRefreshItems.get(itemKey);
        if (refreshTime == null) {
            // Item is not in pending list, so it's available (e.g., loaded from file)
            return true;
        }

        return System.currentTimeMillis() >= refreshTime;
    }

    private String getIndexName(Item item) {
        String indexName = getIndex(item.getClass());
        if (item instanceof CustomItem) {
            String customItemType = ((CustomItem) item).getCustomItemType();
            if (customItemType != null) {
                indexName = customItemType;
            } else {
                indexName = item.getItemType();
            }
        }
        return indexName;
    }

    @Override
    public <T extends Item> T load(String itemId, Class<T> clazz) {
        Item item = itemsById.get(getKey(itemId, getIndex(clazz)));
        if (item != null && clazz.isAssignableFrom(item.getClass()) && executionContextManager.getCurrentContext().getTenantId().equals(item.getTenantId())) {
            // Apply reverse tenant transformations after load (simulates Elasticsearch/OpenSearch behavior)
            return (T) handleItemReverseTransformation(item);
        }
        return null;
    }

    @Override
    public <T extends Item> T load(String itemId, Date dateHint, Class<T> clazz) {
        return load(itemId, clazz);
    }

    @Override
    public <T extends Item> boolean remove(String itemId, Class<T> clazz) {
        String key = getKey(itemId, getIndex(clazz));
        Item item = itemsById.get(key);
        if (item != null && clazz.isAssignableFrom(item.getClass()) &&
            executionContextManager.getCurrentContext().getTenantId().equals(item.getTenantId())) {
            itemsById.remove(key);
            // Remove from pending refresh list if present
            if (simulateRefreshDelay) {
                pendingRefreshItems.remove(key);
            }
            deleteItemFile(item);
            return true;
        }
        return false;
    }

    @Override
    public <T extends Item> boolean removeByQuery(Condition condition, Class<T> clazz) {
        // In Elasticsearch, deleteByQuery works on all matching documents regardless of refresh status
        // So we need to query all items, not just refreshed ones
        String currentTenantId = executionContextManager.getCurrentContext().getTenantId();
        List<T> itemsToRemove = new ArrayList<>();

        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
            Item item = entry.getValue();
            if (clazz.isAssignableFrom(item.getClass()) &&
                currentTenantId.equals(item.getTenantId())) {
                // Check condition if provided (but don't filter by refresh status for delete operations)
                if (condition == null || testMatch(condition, item)) {
                    itemsToRemove.add((T) item);
                }
            }
        }

        for (T item : itemsToRemove) {
            remove(item.getItemId(), clazz);
        }
        return true;
    }

    @Override
    public <T extends Item> List<T> getAllItems(Class<T> clazz) {
        return filterItemsByClass(clazz);
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy) {
        List<T> items = filterItemsByClass(clazz);
        items = sortItems(items, sortBy);
        return createPartialList(items, offset, size);
    }

    @Override
    public <T extends Item> List<T> query(Condition condition, String sortBy, Class<T> clazz) {
        List<T> items = filterItemsByClass(clazz);
        items = filterItemsByCondition(items, condition);
        return sortItems(items, sortBy);
    }

    @Override
    public <T extends Item> PartialList<T> query(Condition condition, String sortBy, Class<T> clazz, int offset, int size) {
        List<T> items = query(condition, sortBy, clazz);
        return createPartialList(items, offset, size);
    }

    @Override
    public <T extends Item> List<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz) {
        List<T> items = filterItemsByClass(clazz);
        items = items.stream()
                .filter(item -> matchesField(item, fieldName, fieldValue))
                .collect(Collectors.toList());
        return sortItems(items, sortBy);
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
        return sortItems(results, sortBy);
    }

    @Override
    public <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size) {
        List<T> items = query(fieldName, fieldValue, sortBy, clazz);
        return createPartialList(items, offset, size);
    }

    @Override
    public <T extends Item> PartialList<T> rangeQuery(String fieldName, String from, String to, String sortBy, Class<T> clazz, int offset, int size) {
        List<T> items = filterItemsByClass(clazz);
        items = items.stream()
                .filter(item -> isInRange(item, fieldName, from, to))
                .collect(Collectors.toList());
        items = sortItems(items, sortBy);
        return createPartialList(items, offset, size);
    }

    private boolean isInRange(Item item, String fieldName, String from, String to) {
        try {
            Object value = PropertyUtils.getProperty(item, fieldName);
            if (value instanceof Comparable) {
                Comparable comparableValue = (Comparable) value;
                Comparable fromValue = from != null ? (Comparable) convertValue(from, value.getClass()) : null;
                Comparable toValue = to != null ? (Comparable) convertValue(to, value.getClass()) : null;

                boolean matches = true;
                if (fromValue != null) {
                    matches = matches && comparableValue.compareTo(fromValue) >= 0;
                }
                if (toValue != null) {
                    matches = matches && comparableValue.compareTo(toValue) <= 0;
                }
                return matches;
            }
        } catch (Exception e) {
            LOGGER.debug("Error checking range for field: " + fieldName, e);
        }
        return false;
    }

    private Object convertValue(String value, Class<?> targetType) {
        if (targetType == Integer.class) {
            return Integer.parseInt(value);
        } else if (targetType == Long.class) {
            return Long.parseLong(value);
        } else if (targetType == Double.class) {
            return Double.parseDouble(value);
        } else if (targetType == Float.class) {
            return Float.parseFloat(value);
        } else if (targetType == String.class) {
            return value;
        }
        return value;
    }

    @Override
    public <T extends Item> PartialList<T> query(Condition condition, String sortBy, Class<T> clazz, int offset, int size, String scrollTimeValidity) {
        List<T> matchingItems = query(condition, sortBy, clazz);
        int totalSize = matchingItems.size();

        // Apply default query limit when size < 0 (simulates Elasticsearch/OpenSearch behavior)
        int effectiveSize = size < 0 ? defaultQueryLimit : size;

        // If size is negative and no scroll, use default limit
        if (size < 0 && scrollTimeValidity == null) {
            int fromIndex = Math.min(offset, totalSize);
            int toIndex = Math.min(offset + effectiveSize, totalSize);
            List<T> pageItems = matchingItems.subList(fromIndex, toIndex);
            // Preserve original size parameter in pageSize to indicate what was requested
            return new PartialList<>(pageItems, offset, size, totalSize, PartialList.Relation.EQUAL);
        }

        if (scrollTimeValidity != null) {
            // Don't create scroll state for empty results or when all items fit in one page
            if (totalSize == 0 || totalSize <= effectiveSize) {
                List<T> pageItems = matchingItems.subList(0, totalSize);
                // Preserve original size parameter in pageSize
                return new PartialList<>(pageItems, 0, size, totalSize, PartialList.Relation.EQUAL);
            }

            // Generate a unique scroll ID
            String scrollId = UUID.randomUUID().toString();
            // Parse scroll time validity (assuming it's in milliseconds)
            long validityTime = getScrollTimeValidityMs(scrollTimeValidity);
            // Store scroll state with filtered items
            scrollStates.put(scrollId, new ScrollState(new ArrayList<>(matchingItems), effectiveSize, System.currentTimeMillis() + validityTime, effectiveSize));
            // Return first page with scroll ID
            List<T> pageItems = matchingItems.subList(0, effectiveSize);
            // Preserve original size parameter in pageSize
            PartialList<T> partialList = new PartialList<>(pageItems, 0, size, totalSize, PartialList.Relation.EQUAL);
            partialList.setScrollIdentifier(scrollId);
            partialList.setScrollTimeValidity(scrollTimeValidity);
            return partialList;
        }

        int fromIndex = Math.min(offset, totalSize);
        int toIndex = Math.min(offset + effectiveSize, totalSize);
        List<T> pageItems = matchingItems.subList(fromIndex, toIndex);
        // Preserve original size parameter in pageSize
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

    @Override
    public void refresh() {
        if (simulateRefreshDelay) {
            // Immediately refresh all pending items, making them available for querying
            // This simulates Elasticsearch's refresh() API which forces an immediate refresh
            int itemsRefreshed = pendingRefreshItems.size();
            Set<String> indexesToRefresh = new HashSet<>();

            for (Map.Entry<String, Long> entry : pendingRefreshItems.entrySet()) {
                String itemKey = entry.getKey();
                // Extract index name from itemKey (format: "index:itemId:tenantId")
                String[] parts = itemKey.split(":", 3);
                if (parts.length >= 1) {
                    indexesToRefresh.add(parts[0]);
                }
            }

            // Clear all pending items
            pendingRefreshItems.clear();

            // Mark all indexes as refreshed
            refreshedIndexes.addAll(indexesToRefresh);
            LOGGER.debug("Manually refreshed all indexes, made {} items immediately available", itemsRefreshed);
        } else {
            LOGGER.debug("Refresh called on in-memory persistence service (refresh delay simulation disabled)");
        }
    }

    @Override
    public void purge(Date date) {
        if (date == null) {
            return;
        }

        String currentTenantId = executionContextManager.getCurrentContext().getTenantId();
        List<String> keysToRemove = new ArrayList<>();

        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
            Item item = entry.getValue();
            // Only purge items for the current tenant
            if (currentTenantId.equals(item.getTenantId()) &&
                item.getCreationDate() != null &&
                item.getCreationDate().before(date)) {
                keysToRemove.add(entry.getKey());
                if (fileStorageEnabled) {
                    deleteItemFile(item);
                }
            }
        }

        for (String key : keysToRemove) {
            itemsById.remove(key);
            // Remove from pending refresh list if present
            if (simulateRefreshDelay) {
                pendingRefreshItems.remove(key);
            }
        }

        LOGGER.info("Purged {} items older than {} for tenant {}", keysToRemove.size(), date, currentTenantId);
    }

    @Override
    public void purge(String scope) {
        if (scope == null) {
            return;
        }

        String currentTenantId = executionContextManager.getCurrentContext().getTenantId();
        List<String> keysToRemove = new ArrayList<>();

        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
            Item item = entry.getValue();
            // Only purge items for the current tenant
            if (currentTenantId.equals(item.getTenantId()) &&
                scope.equals(item.getScope())) {
                keysToRemove.add(entry.getKey());
                if (fileStorageEnabled) {
                    deleteItemFile(item);
                }
            }
        }

        for (String key : keysToRemove) {
            itemsById.remove(key);
            // Remove from pending refresh list if present
            if (simulateRefreshDelay) {
                pendingRefreshItems.remove(key);
            }
        }

        LOGGER.info("Purged {} items with scope {} for tenant {}", keysToRemove.size(), scope, currentTenantId);
    }

    @Override
    public <T extends Item> void refreshIndex(Class<T> clazz, Date dateHint) {
        if (simulateRefreshDelay && clazz != null) {
            // Immediately refresh the specified index, making all pending items in it available for querying
            // This simulates Elasticsearch's refreshIndex() API which forces an immediate refresh of a specific index
            String indexName = getIndex(clazz);
            int itemsRefreshed = 0;

            // Remove all pending items for this index from the pending list
            Set<String> keysToRemove = new HashSet<>();
            for (Map.Entry<String, Long> entry : pendingRefreshItems.entrySet()) {
                String itemKey = entry.getKey();
                // Extract index name from itemKey (format: "index:itemId:tenantId")
                String[] parts = itemKey.split(":", 3);
                if (parts.length >= 1 && indexName.equals(parts[0])) {
                    keysToRemove.add(itemKey);
                    itemsRefreshed++;
                }
            }

            for (String key : keysToRemove) {
                pendingRefreshItems.remove(key);
            }

            // Mark this index as refreshed
            refreshedIndexes.add(indexName);
            LOGGER.debug("Manually refreshed index {} for class {} with date hint {}, made {} items immediately available",
                    indexName, clazz.getName(), dateHint, itemsRefreshed);
        } else {
        if (clazz != null) {
                LOGGER.debug("RefreshIndex called for class {} with date hint {} (refresh delay simulation disabled)",
                        clazz.getName(), dateHint);
            }
        }
    }

    @Override
    public void createMapping(String itemType, String mappingConfig) {
        if (itemType == null || mappingConfig == null) {
            throw new IllegalArgumentException("Item type and mapping configuration cannot be null");
        }

        try {
            // Parse the mapping configuration using the object mapper
            if (objectMapper != null) {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> mappingMap = objectMapper.readValue(mappingConfig, Map.class);
                // Store the mapping configuration
                propertyMappings.put(itemType, mappingMap);
                LOGGER.info("Created mapping for item type: {}", itemType);
            } else {
                // If object mapper is null (file storage disabled), use a simple HashMap
                Map<String, Map<String, Object>> mappingMap = new HashMap<>();
                Map<String, Object> properties = new HashMap<>();
                properties.put("mappingConfig", mappingConfig);
                mappingMap.put("properties", properties);
                propertyMappings.put(itemType, mappingMap);
                LOGGER.info("Created simple mapping for item type: {}", itemType);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse mapping configuration: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean removeIndex(String itemType) {
        if (itemType == null) {
            return false;
        }

        String currentTenantId = executionContextManager.getCurrentContext().getTenantId();

        // We don't remove mappings as they are shared across tenants
        // But we remove items of the specified type for the current tenant only
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
            Item item = entry.getValue();
            if (itemType.equals(item.getItemType()) &&
                currentTenantId.equals(item.getTenantId())) {
                keysToRemove.add(entry.getKey());
                if (fileStorageEnabled) {
                    deleteItemFile(item);
                }
            }
        }

        for (String key : keysToRemove) {
            itemsById.remove(key);
            // Remove from pending refresh list if present
            if (simulateRefreshDelay) {
                pendingRefreshItems.remove(key);
            }
        }

        LOGGER.info("Removed index for item type {}, deleted {} items for tenant {}",
                itemType, keysToRemove.size(), currentTenantId);
        return true;
    }

    @Override
    public boolean createIndex(String itemType) {
        if (itemType == null) {
            return false;
        }

        // For in-memory implementation, creating an index just means ensuring we have a mapping
        if (!propertyMappings.containsKey(itemType)) {
            propertyMappings.put(itemType, new HashMap<>());
            LOGGER.info("Created index for item type: {}", itemType);
        } else {
            LOGGER.debug("Index for item type {} already exists", itemType);
        }

        // If file storage is enabled, ensure the directory exists
        if (fileStorageEnabled) {
            try {
                Path indexPath = storageRootPath.resolve(sanitizePathComponent(itemType));
                Files.createDirectories(indexPath);
            } catch (IOException e) {
                LOGGER.error("Failed to create directory for item type: {}", itemType, e);
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean testMatch(Condition condition, Item item) {
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

    @Override
    public long getAllItemsCount(String itemType) {
        if (itemType == null) {
            return 0;
        }

        String currentTenantId = executionContextManager.getCurrentContext().getTenantId();
        LOGGER.debug("Counting all items of type {} for tenant {}", itemType, currentTenantId);

        // Filter by refresh status to simulate Elasticsearch/OpenSearch behavior
        Map<String, Item> filteredItems = new HashMap<>();
        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
            Item item = entry.getValue();
            if (item.getItemType().equals(itemType) && currentTenantId.equals(item.getTenantId())) {
                String itemKey = entry.getKey();
                if (isItemAvailableForQuery(itemKey, itemType)) {
                    filteredItems.put(itemKey, item);
                }
            }
        }
        return filteredItems.size();
    }

    @Override
    public Map<String, Double> getSingleValuesMetrics(Condition condition, String[] metrics, String field, String itemType) {
        if (metrics == null || metrics.length == 0 || field == null) {
            return Collections.emptyMap();
        }

        String finalField;
        if (field.endsWith(".keyword")) {
            finalField = field.substring(0, field.length() - ".keyword".length());
        } else {
            finalField = field;
        }

        // Get all items of the specified type that match the condition and are available for querying
        List<Item> matchingItems = new ArrayList<>();
        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
            Item item = entry.getValue();
            if (item.getItemType().equals(itemType)) {
                String itemKey = entry.getKey();
                if (isItemAvailableForQuery(itemKey, itemType) && (condition == null || testMatch(condition, item))) {
                    matchingItems.add(item);
                }
            }
        }

        Map<String, Double> results = new HashMap<>();

        // Extract field values from matching items
        List<Number> numericValues = matchingItems.stream()
                .map(item -> {
                    Object value = getPropertyValue(item, finalField);
                    return value instanceof Number ? (Number) value : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Calculate requested metrics
        for (String metric : metrics) {
            switch (metric.toLowerCase()) {
                case "card":
                    results.put("_card", (double) matchingItems.stream()
                            .map(item -> getPropertyValue(item, finalField))
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

    @Override
    public boolean update(Item item, Date dateHint, Class<?> clazz, Map<?, ?> sourceMap) {
        if (item == null || sourceMap == null || clazz == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        String key = getKey(item.getItemId(), getIndex((Class<? extends Item>) clazz));
        Item existingItem = itemsById.get(key);
        if (existingItem == null || !clazz.isAssignableFrom(existingItem.getClass()) ||
            !executionContextManager.getCurrentContext().getTenantId().equals(existingItem.getTenantId())) {
            return false;
        }

        try {
            // Update properties using PropertyUtils
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                String propertyName = entry.getKey().toString();
                Object propertyValue = entry.getValue();
                try {
                    PropertyUtils.setProperty(existingItem, propertyName, propertyValue);
                } catch (Exception e) {
                    LOGGER.debug("Failed to set property: " + propertyName, e);
                    return false;
                }
            }

            // Increment version
            if (existingItem.getVersion() != null) {
                existingItem.setVersion(existingItem.getVersion() + 1);
            } else {
                existingItem.setVersion(1L);
            }

            // Apply tenant transformations before save (simulates Elasticsearch/OpenSearch behavior)
            existingItem = handleItemTransformation(existingItem);

            // Save updated item
            itemsById.put(key, existingItem);
            if (fileStorageEnabled) {
                persistItem(existingItem);
            }

            // Handle refresh policy per item type for updates (same as save)
            // Request-based override (via system metadata) takes precedence over per-item-type policy
            if (simulateRefreshDelay) {
                String itemType = existingItem.getItemType();
                if (existingItem instanceof CustomItem) {
                    String customItemType = ((CustomItem) existingItem).getCustomItemType();
                    if (customItemType != null) {
                        itemType = customItemType;
                    }
                }
                String indexName = getIndexName(existingItem);
                RefreshPolicy refreshPolicy = getRefreshPolicy(itemType, existingItem);

                switch (refreshPolicy) {
                    case TRUE:
                    case WAIT_FOR:
                        // Immediate refresh: make item available immediately
                        pendingRefreshItems.remove(key);
                        refreshedIndexes.add(indexName);
                        LOGGER.debug("Immediately refreshed updated item {} of type {} due to refresh policy {}",
                                existingItem.getItemId(), itemType, refreshPolicy);
                        break;
                    case FALSE:
                    default:
                        // Default behavior: wait for automatic refresh
                        long refreshTime = System.currentTimeMillis() + refreshIntervalMs;
                        pendingRefreshItems.put(key, refreshTime);
                        refreshedIndexes.remove(indexName);
                        break;
                }
            }

            return true;
        } catch (Exception e) {
            LOGGER.error("Error updating item", e);
            return false;
        }
    }

    @Override
    public boolean update(Item item, Date dateHint, Class<?> clazz, String propertyName, Object propertyValue) {
        if (item == null || propertyName == null || clazz == null) {
            return false;
        }

        return update(item, dateHint, clazz, Collections.singletonMap(propertyName, propertyValue));
    }

    @Override
    public boolean update(Item item, Date dateHint, Class<?> clazz, Map<?, ?> sourceMap, boolean noScriptCall) {
        // In this implementation, noScriptCall doesn't affect behavior since we don't execute scripts
        return update(item, dateHint, clazz, sourceMap);
    }

    @Override
    public List<String> update(Map<Item, Map> items, Date dateHint, Class clazz) {
        if (items == null || clazz == null) {
            return null;
        }

        List<String> failedUpdates = new ArrayList<>();

        for (Map.Entry<Item, Map> entry : items.entrySet()) {
            Item item = entry.getKey();
            Map sourceMap = entry.getValue();

            if (!update(item, dateHint, clazz, sourceMap)) {
                failedUpdates.add(item.getItemId());
            }
        }

        return failedUpdates;
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

        // In Elasticsearch, updateByQuery works on all matching documents regardless of refresh status
        // So we need to query all items, not just refreshed ones (similar to removeByQuery)
        boolean success = true;
        for (int i = 0; i < scripts.length; i++) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            List<Item> items = new ArrayList<>();
            String currentTenantId = executionContextManager.getCurrentContext().getTenantId();

            // Query all items directly from itemsById, not filtering by refresh status
            for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
                Item item = entry.getValue();
                if (clazz.isAssignableFrom(item.getClass()) &&
                    currentTenantId.equals(item.getTenantId())) {
                    // Check condition if provided (but don't filter by refresh status)
                    if (conditions[i] == null || testMatch(conditions[i], item)) {
                        items.add(item);
                    }
                }
            }

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

    @Override
    public PartialList<CustomItem> queryCustomItem(Condition condition, String sortBy, String customItemType, int offset, int size, String scrollTimeValidity) {
        // Get all items that match the item type and are available for querying
        String currentTenantId = executionContextManager.getCurrentContext().getTenantId();
        List<CustomItem> customItems = new ArrayList<>();
        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
            Item item = entry.getValue();
            if (item instanceof CustomItem && customItemType.equals(item.getItemType())
                    && currentTenantId.equals(item.getTenantId())) {
                String itemKey = entry.getKey();
                if (isItemAvailableForQuery(itemKey, customItemType) && (condition == null || testMatch(condition, item))) {
                    customItems.add((CustomItem) item);
                }
            }
        }

        // Sort items if needed
        if (sortBy != null) {
            customItems = sortItems(customItems, sortBy);
        }

        // Apply offset and size
        int totalSize = customItems.size();
        int fromIndex = Math.min(offset, totalSize);
        int toIndex = size < 0 ? totalSize : Math.min(offset + size, totalSize);
        List<CustomItem> pagedItems = customItems.subList(fromIndex, toIndex);

        PartialList<CustomItem> result = new PartialList<>(pagedItems, offset, size, totalSize, PartialList.Relation.EQUAL);

        // Setup scroll state if scrollTimeValidity is provided
        if (scrollTimeValidity != null && !scrollTimeValidity.isEmpty()) {
            // Generate a unique scroll ID
            String scrollId = UUID.randomUUID().toString();

            // Parse the scroll time validity (in milliseconds)
            long scrollTimeValidityMs = getScrollTimeValidityMs(scrollTimeValidity);

            // Calculate expiry time
            long expiryTime = System.currentTimeMillis() + scrollTimeValidityMs;

            // Store the scroll state
            scrollStates.put(scrollId, new ScrollState((List<Item>)(List<?>)customItems, toIndex, expiryTime, size));

            // Add scroll information to the result
            result.setScrollIdentifier(scrollId);
            result.setScrollTimeValidity(scrollTimeValidity);
        }

        return result;
    }

    private static long getScrollTimeValidityMs(String scrollTimeValidity) {
        long scrollTimeValidityMs = 60000; // Default to 1 minute if parsing fails
        try {
            if (scrollTimeValidity.endsWith("ms")) {
                scrollTimeValidityMs = Long.parseLong(scrollTimeValidity.substring(0, scrollTimeValidity.length() - 2));
            } else if (scrollTimeValidity.endsWith("m")) {
                scrollTimeValidityMs = Long.parseLong(scrollTimeValidity.substring(0, scrollTimeValidity.length() - 1)) * 60 * 1000L;
            } else if (scrollTimeValidity.endsWith("s")) {
                scrollTimeValidityMs = Long.parseLong(scrollTimeValidity.substring(0, scrollTimeValidity.length() - 1)) * 1000L;
            } else if (scrollTimeValidity.endsWith("h")) {
                scrollTimeValidityMs = Long.parseLong(scrollTimeValidity.substring(0, scrollTimeValidity.length() - 1)) * 60 * 60 * 1000L;
            } else {
                scrollTimeValidityMs = Long.parseLong(scrollTimeValidity);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid scroll time validity string: {}", scrollTimeValidity, e);
        }
        return scrollTimeValidityMs;
    }

    @Override
    public PartialList<CustomItem> continueCustomItemScrollQuery(String customItemType, String scrollIdentifier, String scrollTimeValidity) {
        if (scrollIdentifier == null) {
            return new PartialList<>(Collections.emptyList(), 0, 0, 0, PartialList.Relation.EQUAL);
        }

        // Clean up expired scroll states
        scrollStates.entrySet().removeIf(entry -> entry.getValue().expiryTime < System.currentTimeMillis());

        ScrollState state = scrollStates.get(scrollIdentifier);
        if (state == null) {
            return new PartialList<>(Collections.emptyList(), 0, 0, 0, PartialList.Relation.EQUAL);
        }

        // Get next page of items, filtering by type and converting to CustomItem
        int fromIndex = state.currentPosition;
        int remainingItems = state.items.size() - fromIndex;
        int pageSize = state.pageSize < 0 ? remainingItems : Math.min(state.pageSize, remainingItems);
        int toIndex = fromIndex + pageSize;

        List<CustomItem> pageItems = state.items.subList(fromIndex, toIndex).stream()
                .filter(item -> item instanceof CustomItem)
                .filter(item -> customItemType.equals(item.getItemType()))
                .filter(item -> executionContextManager.getCurrentContext().getTenantId().equals(item.getTenantId()))
                .map(item -> (CustomItem) item)
                .collect(Collectors.toList());

        // Update scroll state with new position
        if (toIndex >= state.items.size()) {
            // End of scroll, remove the state
            scrollStates.remove(scrollIdentifier);
        } else {
            // Parse the new scroll time validity if provided
            long scrollTimeValidityMs = getScrollTimeValidityMs(scrollTimeValidity);

            // Extend the scroll timeout
            scrollStates.put(scrollIdentifier, new ScrollState((List<Item>)(List<?>)state.items, toIndex,
                    System.currentTimeMillis() + scrollTimeValidityMs, state.pageSize));
        }

        PartialList<CustomItem> partialList = new PartialList<>(pageItems, fromIndex, pageSize, state.items.size(), PartialList.Relation.EQUAL);
        if (toIndex < state.items.size()) {
            partialList.setScrollIdentifier(scrollIdentifier);
            partialList.setScrollTimeValidity(scrollTimeValidity);
        }

        return partialList;
    }

    @Override
    public CustomItem loadCustomItem(String itemId, Date dateHint, String itemType) {
        return loadCustomItem(itemId, itemType);
    }

    @Override
    public CustomItem loadCustomItem(String itemId, String itemType) {
        if (itemId == null || itemType == null) {
            return null;
        }

        // Create a key that includes the item type and tenant ID
        String key = getKey(itemId, itemType);
        Item item = itemsById.get(key);

        if (item instanceof CustomItem &&
            itemType.equals(item.getItemType()) &&
            executionContextManager.getCurrentContext().getTenantId().equals(item.getTenantId())) {
            // Apply reverse tenant transformations after load (simulates Elasticsearch/OpenSearch behavior)
            return (CustomItem) handleItemReverseTransformation(item);
        }

        return null;
    }

    @Override
    public boolean removeCustomItem(String itemId, String itemType) {
        if (itemId == null || itemType == null) {
            return false;
        }

        String key = getKey(itemId, itemType);
        Item item = itemsById.get(key);

        if (item instanceof CustomItem &&
            itemType.equals(item.getItemType()) &&
            executionContextManager.getCurrentContext().getTenantId().equals(item.getTenantId())) {
            itemsById.remove(key);
            // Remove from pending refresh list if present
            if (simulateRefreshDelay) {
                pendingRefreshItems.remove(key);
            }
            if (fileStorageEnabled) {
                deleteItemFile(item);
            }
            return true;
        }

        return false;
    }

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
        long validityTime = getScrollTimeValidityMs(scrollTimeValidity);

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

    @Override
    public Map<String, Long> aggregateQuery(Condition condition, BaseAggregate aggregate, String itemType) {
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

        // Get all items of the specified type that are available for querying
        List<Item> items = new ArrayList<>();
        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
            Item item = entry.getValue();
            if (item.getItemType().equals(itemType)) {
                String itemKey = entry.getKey();
                if (isItemAvailableForQuery(itemKey, itemType) && (condition == null || testMatch(condition, item))) {
                    items.add(item);
                }
            }
        }

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
        // Apply default query limit when size < 0 (simulates Elasticsearch/OpenSearch behavior)
        int effectiveSize = size < 0 ? defaultQueryLimit : size;
        int fromIndex = Math.min(offset, totalSize);
        int toIndex = Math.min(offset + effectiveSize, totalSize);
        List<T> pageItems = items.subList(fromIndex, toIndex);
        // Preserve original size parameter in pageSize to indicate what was requested
        // even though we apply a default limit when size < 0
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

    @Override
    public long queryCount(Condition condition, String itemType) {
        // Filter by refresh status to simulate Elasticsearch/OpenSearch behavior
        long count = 0;
        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
            Item item = entry.getValue();
            if (item.getItemType().equals(itemType)) {
                String itemKey = entry.getKey();
                if (isItemAvailableForQuery(itemKey, itemType) && (condition == null || testMatch(condition, item))) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public boolean isConsistent(Item item) {
        // In Elasticsearch, isConsistent returns true if refresh policy is not FALSE
        // This indicates whether changes are immediately visible (consistent)
        // Request-based override takes precedence over per-item-type policy
        if (simulateRefreshDelay && item != null) {
            String itemType = item.getItemType();
            if (item instanceof CustomItem) {
                String customItemType = ((CustomItem) item).getCustomItemType();
                if (customItemType != null) {
                    itemType = customItemType;
                }
            }
            RefreshPolicy policy = getRefreshPolicy(itemType, item);
            return policy != RefreshPolicy.FALSE;
        }
        return true; // as we work in memory this is always true
    }

    @Override
    public <T extends Item> void purgeTimeBasedItems(int olderThanInDays, Class<T> clazz) {
        if (olderThanInDays <= 0 || clazz == null) {
            return;
        }

        // Calculate the date olderThanInDays days ago
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -olderThanInDays);
        Date cutoffDate = calendar.getTime();

        String currentTenantId = executionContextManager.getCurrentContext().getTenantId();
        String itemType = getIndex(clazz);
        List<String> keysToRemove = new ArrayList<>();

        for (Map.Entry<String, Item> entry : itemsById.entrySet()) {
            Item item = entry.getValue();
            // Use creation date instead of timestamp, and check tenant
            if (currentTenantId.equals(item.getTenantId()) &&
                item.getItemType().equals(itemType) &&
                item.getCreationDate() != null &&
                item.getCreationDate().before(cutoffDate)) {
                keysToRemove.add(entry.getKey());
                if (fileStorageEnabled) {
                    deleteItemFile(item);
                }
            }
        }

        for (String key : keysToRemove) {
            itemsById.remove(key);
            // Remove from pending refresh list if present
            if (simulateRefreshDelay) {
                pendingRefreshItems.remove(key);
            }
        }

        LOGGER.info("Purged {} items of type {} older than {} days for tenant {}",
                keysToRemove.size(), itemType, olderThanInDays, currentTenantId);
    }

    @Override
    public boolean migrateTenantData(String fromTenantId, String toTenantId, List<String> itemTypes) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public long getApiCallCount(String apiName) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public long calculateStorageSize(String itemType) {
        throw new UnsupportedOperationException("Not implemented");
    }

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

    // Add new utility methods for common operations
    private <T extends Item> List<T> sortItems(List<T> items, String sortBy) {
        if (sortBy == null) {
            return items;
        }

        List<T> sortedItems = new ArrayList<>(items);
        Collections.sort(sortedItems, (o1, o2) -> {
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
                LOGGER.debug("Error comparing properties for sorting", e);
                return 0;
            }
        });
        return sortedItems;
    }

    private <T extends Item> List<T> filterItemsByClass(Class<T> clazz) {
        String indexName = getIndex(clazz);
        return itemsById.entrySet().stream()
                .filter(entry -> {
                    Item item = entry.getValue();
                    String itemKey = entry.getKey();
                    // Filter by class and tenant
                    if (!clazz.isAssignableFrom(item.getClass()) ||
                            !executionContextManager.getCurrentContext().getTenantId().equals(item.getTenantId())) {
                        return false;
                    }
                    // Filter out items that are not yet available for querying (refresh delay simulation)
                    return isItemAvailableForQuery(itemKey, indexName);
                })
                .map(entry -> (T) entry.getValue())
                .collect(Collectors.toList());
    }

    private <T extends Item> List<T> filterItemsByCondition(List<T> items, Condition condition) {
        if (condition == null) {
            return items;
        }
        return items.stream()
                .filter(item -> testMatch(condition, item))
                .collect(Collectors.toList());
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy, String scrollTimeValidity) {
        return getAllItems(clazz, offset, size, sortBy);
    }

    private boolean matchesField(Item item, String fieldName, String fieldValue) {
        if (item == null || fieldName == null || fieldValue == null) {
            return false;
        }

        try {
            Object value = getValueFromPath(item, fieldName);
            if (value == null) {
                return false;
            }

            if (value instanceof Collection) {
                return ((Collection<?>) value).contains(fieldValue);
            }

            return value.toString().equals(fieldValue);
        } catch (Exception e) {
            LOGGER.debug("Error matching field: " + fieldName, e);
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
            boolean inQuotes = false;
            boolean escaped = false;
            char quoteChar = 0;

            for (int i = 0; i < path.length(); i++) {
                char c = path.charAt(i);

                if (escaped) {
                    if (c == '.' || c == '[' || c == ']' || c == '\'' || c == '"' || c == '\\') {
                        currentPart.append(c);
                    } else {
                        currentPart.append('\\').append(c);
                    }
                    escaped = false;
                    continue;
                }

                switch (c) {
                    case '\\':
                        if (!inQuotes) {
                            escaped = true;
                        } else {
                            currentPart.append(c);
                        }
                        break;
                    case '\'':
                    case '"':
                        if (!inQuotes) {
                            inQuotes = true;
                            quoteChar = c;
                        } else if (c == quoteChar) {
                            inQuotes = false;
                            quoteChar = 0;
                        } else {
                            currentPart.append(c);
                        }
                        break;
                    case '[':
                        if (!inQuotes) {
                            if (currentPart.length() > 0) {
                                current = resolveValue(current, currentPart.toString());
                                currentPart.setLength(0);
                            }
                        } else {
                            currentPart.append(c);
                        }
                        break;
                    case ']':
                        if (!inQuotes) {
                            if (currentPart.length() > 0) {
                                String arrayIndex = currentPart.toString().trim();
                                current = resolveArrayValue(current, arrayIndex);
                                currentPart.setLength(0);
                            }
                        } else {
                            currentPart.append(c);
                        }
                        break;
                    case '.':
                        if (!inQuotes && !escaped) {
                            if (currentPart.length() > 0) {
                                current = resolveValue(current, currentPart.toString());
                                currentPart.setLength(0);
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

    private Object resolveArrayValue(Object obj, String index) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof List) {
            try {
                List<?> list = (List<?>) obj;
                int idx = Integer.parseInt(index);
                if (idx >= 0 && idx < list.size()) {
                    return list.get(idx);
                }
            } catch (NumberFormatException e) {
                // Fall through to try Map access
            }
        }

        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).get(index);
        }

        return null;
    }

    private Object resolveValue(Object obj, String key) {
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
            try {
                return getter.invoke(obj);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOGGER.debug("Error invoking getter method: " + getterName, e);
                return null;
            }
        } catch (NoSuchMethodException e) {
            // Try boolean getter
            try {
                String isName = "is" + key.substring(0, 1).toUpperCase() + key.substring(1);
                Method isGetter = obj.getClass().getMethod(isName);
                try {
                    return isGetter.invoke(obj);
                } catch (IllegalAccessException | InvocationTargetException e2) {
                    LOGGER.debug("Error invoking boolean getter method: " + isName, e2);
                    return null;
                }
            } catch (NoSuchMethodException e2) {
                // Try field access
                try {
                    Field field = obj.getClass().getDeclaredField(key);
                    field.setAccessible(true);
                    try {
                        return field.get(obj);
                    } catch (IllegalAccessException e3) {
                        LOGGER.debug("Error accessing field: " + key, e3);
                        return null;
                    }
                } catch (NoSuchFieldException e3) {
                    return null;
                }
            }
        }
    }

    private String getIndex(Class<?> clazz) {
        return Item.getItemType(clazz);
    }

    /**
     * Applies tenant transformations to an item before save/update.
     * This simulates Elasticsearch/OpenSearch tenant transformation behavior.
     *
     * @param item the item to transform
     * @param <T> the item type
     * @return the transformed item (or original if no transformations applied)
     */
    @SuppressWarnings("unchecked")
    private <T extends Item> T handleItemTransformation(T item) {
        if (item != null) {
            String tenantId = item.getTenantId();
            if (tenantId != null && !transformationListeners.isEmpty()) {
                // Sort listeners by priority (higher priority first)
                List<TenantTransformationListener> sortedListeners = new ArrayList<>(transformationListeners);
                sortedListeners.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

                for (TenantTransformationListener listener : sortedListeners) {
                    if (listener.isTransformationEnabled()) {
                        try {
                            Item transformedItem = listener.transformItem(item, tenantId);
                            if (transformedItem != null) {
                                item = (T) transformedItem;
                            }
                        } catch (Exception e) {
                            // Log error but continue with other listeners since transformation is optional
                            LOGGER.warn("Error during item transformation for tenant {} with listener {}: {}",
                                tenantId, listener.getTransformationType(), e.getMessage());
                        }
                    }
                }
            }
        }
        return item;
    }

    /**
     * Applies reverse tenant transformations to an item after load.
     * This simulates Elasticsearch/OpenSearch tenant reverse transformation behavior.
     *
     * @param item the item to reverse transform
     * @param <T> the item type
     * @return the reverse transformed item (or original if no transformations applied)
     */
    @SuppressWarnings("unchecked")
    private <T extends Item> T handleItemReverseTransformation(T item) {
        if (item != null) {
            String tenantId = item.getTenantId();
            if (tenantId != null && !transformationListeners.isEmpty()) {
                // Sort listeners by priority (higher priority first) for reverse transformation
                List<TenantTransformationListener> sortedListeners = new ArrayList<>(transformationListeners);
                sortedListeners.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

                for (TenantTransformationListener listener : sortedListeners) {
                    if (listener.isTransformationEnabled()) {
                        try {
                            Item transformedItem = listener.reverseTransformItem(item, tenantId);
                            if (transformedItem != null) {
                                item = (T) transformedItem;
                            }
                        } catch (Exception e) {
                            // Log error but continue with other listeners since transformation is optional
                            LOGGER.warn("Error during item reverse transformation for tenant {} with listener {}: {}",
                                tenantId, listener.getTransformationType(), e.getMessage());
                        }
                    }
                }
            }
        }
        return item;
    }

    /**
     * Adds a tenant transformation listener (for testing purposes).
     * This simulates OSGi service registration in Elasticsearch/OpenSearch implementations.
     *
     * @param listener the transformation listener to add
     */
    public void addTransformationListener(TenantTransformationListener listener) {
        if (listener != null) {
            transformationListeners.add(listener);
            LOGGER.debug("Added tenant transformation listener: {}", listener.getTransformationType());
        }
    }

    /**
     * Removes a tenant transformation listener (for testing purposes).
     *
     * @param listener the transformation listener to remove
     */
    public void removeTransformationListener(TenantTransformationListener listener) {
        if (listener != null) {
            transformationListeners.remove(listener);
            LOGGER.debug("Removed tenant transformation listener: {}", listener.getTransformationType());
        }
    }

    /**
     * Gets the default query limit.
     *
     * @return the default query limit
     */
    public Integer getDefaultQueryLimit() {
        return defaultQueryLimit;
    }

    /**
     * Sets the default query limit.
     *
     * @param defaultQueryLimit the default query limit to set
     */
    public void setDefaultQueryLimit(Integer defaultQueryLimit) {
        this.defaultQueryLimit = defaultQueryLimit != null && defaultQueryLimit > 0 ? defaultQueryLimit : 10;
    }
}

