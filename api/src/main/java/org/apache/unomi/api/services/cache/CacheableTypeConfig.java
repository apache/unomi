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
import org.osgi.framework.BundleContext;

import java.io.Serializable;
import java.util.function.BiConsumer;
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
    private final boolean hasPredefinedItems;
    private final BiConsumer<BundleContext, T> bundleItemProcessor;

    /**
     * Private constructor used by the builder
     */
    private CacheableTypeConfig(Builder<T> builder) {
        this.type = builder.type;
        this.itemType = builder.itemType;
        this.metaInfPath = builder.metaInfPath;
        this.inheritFromSystemTenant = builder.inheritFromSystemTenant;
        this.requiresRefresh = builder.requiresRefresh;
        this.refreshInterval = builder.refreshInterval;
        this.idExtractor = builder.idExtractor;
        this.postProcessor = builder.postProcessor;
        this.hasPredefinedItems = builder.hasPredefinedItems;
        this.bundleItemProcessor = builder.bundleItemProcessor;
    }

    /**
     * Creates a new builder for the config
     * @param type the class of the cacheable type
     * @param itemType the string identifier for the type
     * @param metaInfPath the predefined items path in META-INF/cxs
     * @param <T> the type parameter
     * @return a new builder
     */
    public static <T extends Serializable> Builder<T> builder(Class<T> type, String itemType, String metaInfPath) {
        return new Builder<>(type, itemType, metaInfPath);
    }

    /**
     * Get the class of the cacheable type.
     *
     * @return the class of the cacheable type
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * Get the item type identifier.
     *
     * @return the item type identifier
     */
    public String getItemType() {
        return itemType;
    }

    /**
     * Get the META-INF path for predefined items.
     *
     * @return the META-INF path for predefined items
     */
    public String getMetaInfPath() {
        return metaInfPath;
    }

    /**
     * Check if items should be inherited from the system tenant.
     *
     * @return true if items should be inherited from the system tenant
     */
    public boolean isInheritFromSystemTenant() {
        return inheritFromSystemTenant;
    }

    /**
     * Check if the cache requires periodic refresh.
     *
     * @return true if the cache requires periodic refresh
     */
    public boolean isRequiresRefresh() {
        return requiresRefresh;
    }

    /**
     * Get the refresh interval in milliseconds.
     *
     * @return the refresh interval in milliseconds
     */
    public long getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * Check if the type has predefined items that should be loaded from bundles.
     *
     * @return true if the type has predefined items
     */
    public boolean hasPredefinedItems() {
        return hasPredefinedItems;
    }

    /**
     * Check if this configuration has a bundle item processor.
     *
     * @return true if there is a bundle item processor
     */
    public boolean hasBundleItemProcessor() {
        return bundleItemProcessor != null;
    }

    /**
     * Get the bundle item processor that handles bundle-specific processing.
     *
     * @return the bundle item processor
     */
    public BiConsumer<BundleContext, T> getBundleItemProcessor() {
        return bundleItemProcessor;
    }

    /**
     * Get the ID extractor function.
     *
     * @return the ID extractor function
     */
    public Function<T, String> getIdExtractor() {
        return idExtractor;
    }

    /**
     * Get the post-processor for items.
     *
     * @return the post-processor for items
     */
    public Consumer<T> getPostProcessor() {
        return postProcessor;
    }

    /**
     * Check if items of this type are persistable.
     * An item is persistable if it extends Item.
     *
     * @return true if items of this type are persistable
     */
    public boolean isPersistable() {
        return Item.class.isAssignableFrom(type);
    }

    /**
     * Builder for CacheableTypeConfig
     * @param <T> the type parameter for the cacheable type
     */
    public static class Builder<T extends Serializable> {
        private final Class<T> type;
        private final String itemType;
        private final String metaInfPath;
        private boolean inheritFromSystemTenant = false;
        private boolean requiresRefresh = false;
        private long refreshInterval = 0;
        private Function<T, String> idExtractor;
        private Consumer<T> postProcessor = null;
        private boolean hasPredefinedItems = true;
        private BiConsumer<BundleContext, T> bundleItemProcessor = null;

        private Builder(Class<T> type, String itemType, String metaInfPath) {
            this.type = type;
            this.itemType = itemType;
            this.metaInfPath = metaInfPath;
        }

        /**
         * Set whether items should be inherited from the system tenant.
         * 
         * <p>When set to true, items defined in the system tenant will be available to all tenants.
         * This is useful for sharing base configurations across multiple tenants.</p>
         *
         * @param inheritFromSystemTenant whether items should be inherited from the system tenant
         * @return this builder for method chaining
         */
        public Builder<T> withInheritFromSystemTenant(boolean inheritFromSystemTenant) {
            this.inheritFromSystemTenant = inheritFromSystemTenant;
            return this;
        }

        /**
         * Set whether the cache requires periodic refresh.
         * 
         * <p>When set to true, the cache will be refreshed at regular intervals defined by
         * {@link #withRefreshInterval(long)}. This is useful for items that change frequently
         * or need to be synchronized with external systems.</p>
         *
         * @param requiresRefresh whether the cache requires periodic refresh
         * @return this builder for method chaining
         */
        public Builder<T> withRequiresRefresh(boolean requiresRefresh) {
            this.requiresRefresh = requiresRefresh;
            return this;
        }

        /**
         * Set the refresh interval in milliseconds.
         * 
         * <p>This setting is only used when {@link #withRequiresRefresh(boolean)} is set to true.
         * The cache will be refreshed at this interval after the initial loading.</p>
         *
         * @param refreshInterval the refresh interval in milliseconds
         * @return this builder for method chaining
         */
        public Builder<T> withRefreshInterval(long refreshInterval) {
            this.refreshInterval = refreshInterval;
            return this;
        }

        /**
         * Set the ID extractor function.
         * 
         * <p>This function is called during item loading and caching to extract a unique identifier
         * from each item. The extracted ID is used as the cache key for retrieving items.</p>
         * 
         * <p>This function is invoked:</p>
         * <ul>
         *   <li>When loading predefined items from bundles</li>
         *   <li>When adding new items to the cache</li>
         *   <li>When retrieving items by their ID</li>
         * </ul>
         *
         * @param idExtractor the function that extracts a unique ID from an item of type T
         * @return this builder for method chaining
         */
        public Builder<T> withIdExtractor(Function<T, String> idExtractor) {
            this.idExtractor = idExtractor;
            return this;
        }

        /**
         * Set the post-processor for items.
         * 
         * <p>This consumer is called after an item is loaded but before it is cached. It allows
         * for additional processing, validation, or enrichment of items.</p>
         * 
         * <p>The post-processor is invoked:</p>
         * <ul>
         *   <li>After loading predefined items from bundles or JSON files</li>
         *   <li>After deserializing items from persistence</li>
         *   <li>Before adding new or updated items to the cache</li>
         * </ul>
         * 
         * <p>Note: Modifications made by the post-processor will be reflected in the cached item.</p>
         *
         * @param postProcessor the consumer that processes items after loading but before caching
         * @return this builder for method chaining
         */
        public Builder<T> withPostProcessor(Consumer<T> postProcessor) {
            this.postProcessor = postProcessor;
            return this;
        }

        /**
         * Set whether the type has predefined items.
         * 
         * <p>When set to true, the cache service will look for predefined items in the META-INF
         * path specified when creating the builder. When set to false, only programmatically
         * added items will be available in the cache.</p>
         *
         * @param hasPredefinedItems whether the type has predefined items to load from bundles
         * @return this builder for method chaining
         */
        public Builder<T> withPredefinedItems(boolean hasPredefinedItems) {
            this.hasPredefinedItems = hasPredefinedItems;
            return this;
        }

        /**
         * Set the bundle item processor.
         * 
         * <p>This processor is called during the bundle scanning phase, when predefined items
         * are being loaded from OSGi bundles. It provides access to the BundleContext along
         * with each item being processed.</p>
         * 
         * <p>The bundle item processor is invoked:</p>
         * <ul>
         *   <li>When a bundle is installed or updated and contains predefined items</li>
         *   <li>During system initialization when scanning all active bundles</li>
         *   <li>Before the post-processor (if defined) is called</li>
         * </ul>
         * 
         * <p>This processor is particularly useful for bundle-specific initialization that
         * requires access to the bundle context, such as registering services or retrieving
         * bundle-specific configuration.</p>
         *
         * @param bundleItemProcessor the bi-consumer that processes items with the bundle context
         * @return this builder for method chaining
         */
        public Builder<T> withBundleItemProcessor(BiConsumer<BundleContext, T> bundleItemProcessor) {
            this.bundleItemProcessor = bundleItemProcessor;
            return this;
        }

        /**
         * Build the config.
         * 
         * <p>Creates a new immutable CacheableTypeConfig instance with the current builder settings.</p>
         * 
         * @return a new CacheableTypeConfig instance
         * @throws IllegalStateException if mandatory settings like idExtractor are missing
         */
        public CacheableTypeConfig<T> build() {
            if (idExtractor == null) {
                throw new IllegalStateException("idExtractor is required for CacheableTypeConfig");
            }
            return new CacheableTypeConfig<>(this);
        }
    }
} 