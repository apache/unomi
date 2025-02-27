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

import org.apache.unomi.api.Item;

import java.io.Serializable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Configuration for a cacheable type in the multi-type cache service.
 *
 * @param <T> the type of plugin that can be cached with this configuration
 */
public class CacheableTypeConfig<T extends Serializable> {
    private final Class<T> type;
    private final String itemType;
    private final String metaInfPath;
    private final boolean inheritFromSystemTenant;
    private final boolean requiresRefresh;
    private final long refreshInterval;
    private final Function<T, String> idExtractor;
    private final Consumer<T> postProcessor;

    /**
     * Creates a new configuration for a cacheable type.
     *
     * @param type the class of the type to cache
     * @param itemType the string identifier for the item type
     * @param metaInfPath the path in META-INF where predefined items are stored
     * @param inheritFromSystemTenant whether values should be inherited from the system tenant
     * @param requiresRefresh whether the type requires periodic refresh
     * @param refreshInterval the refresh interval in milliseconds
     * @param idExtractor function to extract the ID from an item
     */
    public CacheableTypeConfig(Class<T> type, String itemType, String metaInfPath, boolean inheritFromSystemTenant, 
                             boolean requiresRefresh, long refreshInterval, Function<T, String> idExtractor) {
        this(type, itemType, metaInfPath, inheritFromSystemTenant, requiresRefresh, refreshInterval, idExtractor, null);
    }

    /**
     * Creates a new configuration for a cacheable type with a post processor.
     *
     * @param type the class of the type to cache
     * @param itemType the string identifier for the item type
     * @param metaInfPath the path in META-INF where predefined items are stored
     * @param inheritFromSystemTenant whether values should be inherited from the system tenant
     * @param requiresRefresh whether the type requires periodic refresh
     * @param refreshInterval the refresh interval in milliseconds
     * @param idExtractor function to extract the ID from an item
     * @param postProcessor optional consumer function to process items after loading
     */
    public CacheableTypeConfig(Class<T> type, String itemType, String metaInfPath, boolean inheritFromSystemTenant, 
                             boolean requiresRefresh, long refreshInterval, Function<T, String> idExtractor,
                             Consumer<T> postProcessor) {
        this.type = type;
        this.itemType = itemType;
        this.metaInfPath = metaInfPath;
        this.inheritFromSystemTenant = inheritFromSystemTenant;
        this.requiresRefresh = requiresRefresh;
        this.refreshInterval = refreshInterval;
        this.idExtractor = idExtractor;
        this.postProcessor = postProcessor;
    }

    public Class<T> getType() {
        return type;
    }

    public String getItemType() {
        return itemType;
    }

    public String getMetaInfPath() {
        return metaInfPath;
    }

    public boolean isInheritFromSystemTenant() {
        return inheritFromSystemTenant;
    }

    public boolean isRequiresRefresh() {
        return requiresRefresh;
    }

    public long getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * Gets the function used to extract the ID from an item.
     *
     * @return the ID extractor function
     */
    public Function<T, String> getIdExtractor() {
        return idExtractor;
    }

    /**
     * Gets the post-processor function for processing items after loading.
     *
     * @return the post-processor function, or null if none is configured
     */
    public Consumer<T> getPostProcessor() {
        return postProcessor;
    }

    /**
     * Checks if the type is persistable (extends Item).
     *
     * @return true if the type extends Item
     */
    public boolean isPersistable() {
        return Item.class.isAssignableFrom(type);
    }
} 