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
package org.apache.unomi.api.services.cache;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Service interface for managing multi-tenant type caching.
 * Provides functionality for caching and retrieving different types of plugin data across tenants.
 */
public interface MultiTypeCacheService {

    /**
     * Statistics for all cache operations
     */
    interface CacheStatistics {
        /**
         * Gets all type statistics.
         *
         * @return a map of type IDs to their statistics
         */
        Map<String, TypeStatistics> getAllStats();

        /**
         * Resets all statistics.
         */
        void reset();

        /**
         * Statistics for a specific type.
         */
        interface TypeStatistics {
            /**
             * Gets the number of cache hits.
             *
             * @return the number of hits
             */
            long getHits();

            /**
             * Gets the number of cache misses.
             *
             * @return the number of misses
             */
            long getMisses();

            /**
             * Gets the number of cache updates.
             *
             * @return the number of updates
             */
            long getUpdates();

            /**
             * Gets the number of validation failures.
             *
             * @return the number of validation failures
             */
            long getValidationFailures();

            /**
             * Gets the number of indexing errors.
             *
             * @return the number of indexing errors
             */
            long getIndexingErrors();
        }
    }

    /**
     * Gets the cache statistics.
     *
     * @return the cache statistics
     */
    CacheStatistics getStatistics();

    /**
     * Registers a new type configuration.
     *
     * @param config the configuration for the type to register
     * @param <T> the type of plugin to register
     */
    <T extends Serializable> void registerType(CacheableTypeConfig<T> config);

    /**
     * Puts a value in the cache for a specific type, ID, and tenant.
     *
     * @param itemType the type identifier
     * @param id the item identifier
     * @param tenantId the tenant identifier
     * @param value the value to cache
     * @param <T> the type of the value
     */
    <T extends Serializable> void put(String itemType, String id, String tenantId, T value);

    /**
     * Gets a value from the cache with inheritance support.
     *
     * @param id the item identifier
     * @param tenantId the tenant identifier
     * @param typeClass the class of the type to retrieve
     * @param <T> the type to retrieve
     * @return the cached value, or null if not found
     */
    <T extends Serializable> T getWithInheritance(String id, String tenantId, Class<T> typeClass);

    /**
     * Gets all values for a tenant and type that match a predicate.
     *
     * @param tenantId the tenant identifier
     * @param typeClass the class of the type to retrieve
     * @param predicate the predicate to filter values
     * @param <T> the type to retrieve
     * @return a set of matching values
     */
    <T extends Serializable> Set<T> getValuesByPredicateWithInheritance(String tenantId, Class<T> typeClass, Predicate<T> predicate);

    /**
     * Gets the tenant-specific cache for a type.
     *
     * @param tenantId the tenant identifier
     * @param typeClass the class of the type to retrieve
     * @param <T> the type to retrieve
     * @return a map of cached values for the tenant
     */
    <T extends Serializable> Map<String, T> getTenantCache(String tenantId, Class<T> typeClass);

    /**
     * Removes a value from the cache.
     *
     * @param itemType the type identifier
     * @param id the item identifier
     * @param tenantId the tenant identifier
     * @param typeClass the class of the type to remove
     * @param <T> the type to remove
     */
    <T extends Serializable> void remove(String itemType, String id, String tenantId, Class<T> typeClass);

    /**
     * Clears all cached values for a tenant.
     *
     * @param tenantId the tenant identifier
     */
    void clear(String tenantId);

    /**
     * Refreshes the cache for a specific type configuration.
     *
     * @param config the type configuration to refresh
     * @param <T> the type to refresh
     */
    <T extends Serializable> void refreshTypeCache(CacheableTypeConfig<T> config);
} 