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
package org.apache.unomi.services.impl.cache;

import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Implementation of the MultiTypeCacheService interface.
 * Provides caching functionality for plugin types across multiple tenants.
 */
public class MultiTypeCacheServiceImpl implements MultiTypeCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiTypeCacheServiceImpl.class);
    private static final String SYSTEM_TENANT = "system";

    private final Map<Class<?>, CacheableTypeConfig<?>> typeConfigs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, Serializable>>> cache = new ConcurrentHashMap<>();
    private final CacheStatisticsImpl statistics = new CacheStatisticsImpl();

    private static class CacheStatisticsImpl implements CacheStatistics {
        private final Map<String, TypeStatisticsImpl> typeStats = new ConcurrentHashMap<>();

        @Override
        public Map<String, TypeStatistics> getAllStats() {
            return Collections.unmodifiableMap(new HashMap<>(typeStats));
        }

        @Override
        public void reset() {
            typeStats.clear();
        }

        TypeStatisticsImpl getOrCreateStats(String type) {
            return typeStats.computeIfAbsent(type, k -> new TypeStatisticsImpl());
        }

        private static class TypeStatisticsImpl implements TypeStatistics {
            private final AtomicLong hits = new AtomicLong();
            private final AtomicLong misses = new AtomicLong();
            private final AtomicLong updates = new AtomicLong();
            private final AtomicLong validationFailures = new AtomicLong();
            private final AtomicLong indexingErrors = new AtomicLong();

            @Override
            public long getHits() { return hits.get(); }
            @Override
            public long getMisses() { return misses.get(); }
            @Override
            public long getUpdates() { return updates.get(); }
            @Override
            public long getValidationFailures() { return validationFailures.get(); }
            @Override
            public long getIndexingErrors() { return indexingErrors.get(); }

            void incrementHits() { hits.incrementAndGet(); }
            void incrementMisses() { misses.incrementAndGet(); }
            void incrementUpdates() { updates.incrementAndGet(); }
            void incrementValidationFailures() { validationFailures.incrementAndGet(); }
            void incrementIndexingErrors() { indexingErrors.incrementAndGet(); }
        }
    }

    @Override
    public CacheStatistics getStatistics() {
        return statistics;
    }

    @Override
    public <T extends Serializable> void registerType(CacheableTypeConfig<T> config) {
        if (config == null || config.getType() == null) {
            LOGGER.warn("Attempted to register null or invalid type configuration");
            return;
        }
        typeConfigs.put(config.getType(), config);
        LOGGER.debug("Registered type configuration for {}", config.getType().getSimpleName());
    }

    @Override
    public <T extends Serializable> void put(String itemType, String id, String tenantId, T value) {
        if (itemType == null || id == null || tenantId == null || value == null) {
            LOGGER.warn("Attempted to put null value or invalid parameters in cache");
            return;
        }

        Map<String, Map<String, Serializable>> tenantCache = cache.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        Map<String, Serializable> typeCache = tenantCache.computeIfAbsent(itemType, k -> new ConcurrentHashMap<>());
        typeCache.put(id, value);
        statistics.getOrCreateStats(itemType).incrementUpdates();
        LOGGER.debug("Cached value for type: {}, id: {}, tenant: {}", itemType, id, tenantId);
    }

    @Override
    public <T extends Serializable> T getWithInheritance(String id, String tenantId, Class<T> typeClass) {
        if (id == null || tenantId == null || typeClass == null) {
            return null;
        }

        CacheableTypeConfig<T> config = (CacheableTypeConfig<T>) typeConfigs.get(typeClass);
        if (config == null) {
            return null;
        }

        T value = getFromCache(id, tenantId, typeClass);
        if (value != null) {
            statistics.getOrCreateStats(config.getItemType()).incrementHits();
            return value;
        }

        // Try system tenant if not found and inheritance is enabled
        if (!SYSTEM_TENANT.equals(tenantId) && config.isInheritFromSystemTenant()) {
            value = getFromCache(id, SYSTEM_TENANT, typeClass);
            if (value != null) {
                statistics.getOrCreateStats(config.getItemType()).incrementHits();
                return value;
            }
        }

        statistics.getOrCreateStats(config.getItemType()).incrementMisses();
        return null;
    }

    @Override
    public <T extends Serializable> Set<T> getValuesByPredicateWithInheritance(String tenantId, Class<T> typeClass, Predicate<T> predicate) {
        if (tenantId == null || typeClass == null || predicate == null) {
            return Collections.emptySet();
        }

        CacheableTypeConfig<T> config = (CacheableTypeConfig<T>) typeConfigs.get(typeClass);
        if (config == null) {
            return Collections.emptySet();
        }

        Map<String, T> result = new HashMap<>();

        // First get system tenant values if inheritance is enabled
        if (!SYSTEM_TENANT.equals(tenantId) && config.isInheritFromSystemTenant()) {
            Map<String, T> systemCache = getTenantCache(SYSTEM_TENANT, typeClass);
            systemCache.values().stream()
                .filter(predicate)
                .forEach(value -> result.put(config.getIdExtractor().apply(value), value));
        }

        // Then overlay tenant-specific values
        Map<String, T> tenantCache = getTenantCache(tenantId, typeClass);
        tenantCache.values().stream()
            .filter(predicate)
            .forEach(value -> result.put(config.getIdExtractor().apply(value), value));

        return new HashSet<>(result.values());
    }

    @Override
    public <T extends Serializable> Map<String, T> getTenantCache(String tenantId, Class<T> typeClass) {
        if (tenantId == null || typeClass == null) {
            return Collections.emptyMap();
        }

        CacheableTypeConfig<T> config = (CacheableTypeConfig<T>) typeConfigs.get(typeClass);
        if (config == null) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, Serializable>> tenantCache = cache.get(tenantId);
        if (tenantCache == null) {
            return Collections.emptyMap();
        }

        Map<String, Serializable> typeCache = tenantCache.get(config.getItemType());
        if (typeCache == null) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap((Map<String, T>) typeCache);
    }

    @Override
    public <T extends Serializable> void remove(String itemType, String id, String tenantId, Class<T> typeClass) {
        if (itemType == null || id == null || tenantId == null || typeClass == null) {
            return;
        }

        Map<String, Map<String, Serializable>> tenantCache = cache.get(tenantId);
        if (tenantCache != null) {
            Map<String, Serializable> typeCache = tenantCache.get(itemType);
            if (typeCache != null) {
                typeCache.remove(id);
                LOGGER.debug("Removed from cache - type: {}, id: {}, tenant: {}", itemType, id, tenantId);
            }
        }
    }

    @Override
    public void clear(String tenantId) {
        if (tenantId != null) {
            cache.remove(tenantId);
            LOGGER.debug("Cleared cache for tenant: {}", tenantId);
        }
    }

    @Override
    public <T extends Serializable> void refreshTypeCache(CacheableTypeConfig<T> config) {
        if (config == null || !config.isRequiresRefresh()) {
            return;
        }

        try {
            // Implementation of refresh logic
            LOGGER.debug("Refreshing cache for type: {}", config.getType().getSimpleName());
            // Add refresh implementation here
        } catch (Exception e) {
            LOGGER.error("Error refreshing cache for type: {}", config.getType().getSimpleName(), e);
            statistics.getOrCreateStats(config.getItemType()).incrementIndexingErrors();
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Serializable> T getFromCache(String id, String tenantId, Class<T> typeClass) {
        CacheableTypeConfig<T> config = (CacheableTypeConfig<T>) typeConfigs.get(typeClass);
        if (config == null) {
            return null;
        }

        Map<String, Map<String, Serializable>> tenantCache = cache.get(tenantId);
        if (tenantCache == null) {
            return null;
        }

        Map<String, Serializable> typeCache = tenantCache.get(config.getItemType());
        if (typeCache == null) {
            return null;
        }

        return (T) typeCache.get(id);
    }
}
