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
import org.apache.unomi.api.services.TriFunction;

import java.io.Serializable;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.net.URL;
import java.util.Map;
import java.io.InputStream;

/**
 * Configuration for a cacheable item type in Unomi.
 * 
 * <p>This class defines how a specific type of item is cached, loaded, and processed within
 * the Unomi caching system. It supports a comprehensive callback system for processing items
 * at different stages of their lifecycle:</p>
 * 
 * <h2>Callback System Overview</h2>
 * 
 * <p>The callback system includes two major categories of callbacks:</p>
 * 
 * <h3>1. Item-Level Processing Callbacks</h3>
 * <p>These callbacks operate on individual items during loading and are executed in the following
 * order of precedence (only the first applicable callback is called):</p>
 * <ul>
 *   <li><b>urlAwareBundleItemProcessor</b>: Most specific, gets item, bundle context, and resource URL</li>
 *   <li><b>bundleItemProcessor</b>: Gets item and bundle context</li>
 *   <li><b>postProcessor</b>: Most general, gets only the item</li>
 * </ul>
 * 
 * <h3>2. Cache Refresh Callbacks</h3>
 * <p>These callbacks operate after items are loaded and cached:</p>
 * <ul>
 *   <li><b>tenantRefreshCallback</b>: Called for each tenant that has changes after refresh</li>
 *   <li><b>postRefreshCallback</b>: Called once after all tenants are processed if any changes occurred</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Define a cacheable type for PropertyType
 * CacheableTypeConfig.<PropertyType>builder(PropertyType.class, "propertyType", "properties")
 *     .withInheritFromSystemTenant(true)
 *     .withRequiresRefresh(true)
 *     .withRefreshInterval(10000)
 *     .withIdExtractor(PropertyType::getItemId)
 *     
 *     // Simple post-processor example
 *     .withPostProcessor(propertyType -> {
 *         // Normalize or initialize fields
 *         if (propertyType.getPriority() == 0) {
 *             propertyType.setPriority(1);
 *         }
 *     })
 *     
 *     // URL-aware processor example
 *     .withUrlAwareBundleItemProcessor((bundleContext, propertyType, url) -> {
 *         // Extract information from the URL path
 *         if (url.getPath().contains("/profiles/")) {
 *             propertyType.setTarget("profiles");
 *         }
 *     })
 *     
 *     // Tenant-specific callback example
 *     .withTenantRefreshCallback((tenantId, oldState, newState) -> {
 *         // Process tenant-specific changes efficiently
 *         boolean hasChanges = !oldState.equals(newState);
 *         if (hasChanges) {
 *             System.out.println("Tenant " + tenantId + " property types updated");
 *             // Update tenant-specific caches or indices
 *         }
 *     })
 *     
 *     // Global callback example
 *     .withPostRefreshCallback((oldState, newState) -> {
 *         // Process cross-tenant relationships or global state
 *         System.out.println("All property types refreshed, updating type registry");
 *         // Update cross-tenant registries or perform global operations
 *     })
 *     .build();
 * }</pre>
 *
 * @param <T> the type of the cacheable item
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
    private final TriConsumer<BundleContext, T, URL> urlAwareBundleItemProcessor;
    private final Comparator<URL> urlComparator;
    private final BiConsumer<Map<String, Map<String, T>>, Map<String, Map<String, T>>> postRefreshCallback;
    private final TriConsumer<String, Map<String, T>, Map<String, T>> tenantRefreshCallback;
    private final TriFunction<BundleContext, URL, InputStream, T> streamProcessor;

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
        this.urlAwareBundleItemProcessor = builder.urlAwareBundleItemProcessor;
        this.urlComparator = builder.urlComparator;
        this.postRefreshCallback = builder.postRefreshCallback;
        this.tenantRefreshCallback = builder.tenantRefreshCallback;
        this.streamProcessor = builder.streamProcessor;
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
     * Get the URL comparator for sorting predefined items.
     *
     * @return the URL comparator, or null if none is defined
     */
    public Comparator<URL> getUrlComparator() {
        return urlComparator;
    }

    /**
     * Check if this type config has a custom URL comparator.
     *
     * @return true if a URL comparator is defined, false otherwise
     */
    public boolean hasUrlComparator() {
        return urlComparator != null;
    }

    /**
     * Check if this type config has a URL-aware bundle item processor.
     *
     * @return true if a URL-aware bundle item processor is defined, false otherwise
     */
    public boolean hasUrlAwareBundleItemProcessor() {
        return urlAwareBundleItemProcessor != null;
    }

    /**
     * Get the URL-aware bundle item processor that handles bundle-specific processing.
     *
     * @return the URL-aware bundle item processor
     */
    public TriConsumer<BundleContext, T, URL> getUrlAwareBundleItemProcessor() {
        return urlAwareBundleItemProcessor;
    }

    /**
     * Check if this type config has a post-refresh callback.
     *
     * @return true if a post-refresh callback is defined, false otherwise
     */
    public boolean hasPostRefreshCallback() {
        return postRefreshCallback != null;
    }

    /**
     * Get the post-refresh callback that is executed after all items across all tenants have been reloaded.
     * The callback receives both old and new states for change detection.
     *
     * @return the post-refresh callback
     */
    public BiConsumer<Map<String, Map<String, T>>, Map<String, Map<String, T>>> getPostRefreshCallback() {
        return postRefreshCallback;
    }

    /**
     * Check if this type config has a tenant-specific refresh callback.
     *
     * @return true if a tenant-specific refresh callback is defined, false otherwise
     */
    public boolean hasTenantRefreshCallback() {
        return tenantRefreshCallback != null;
    }

    /**
     * Get the tenant-specific refresh callback that is executed after each tenant's items have been reloaded.
     * The callback receives the tenant ID, old state, and new state for that specific tenant.
     *
     * @return the tenant-specific refresh callback
     */
    public TriConsumer<String, Map<String, T>, Map<String, T>> getTenantRefreshCallback() {
        return tenantRefreshCallback;
    }

    /**
     * Check if this configuration has a stream processor.
     *
     * @return true if there is a stream processor
     */
    public boolean hasStreamProcessor() {
        return streamProcessor != null;
    }

    /**
     * Get the stream processor that handles direct input stream processing.
     *
     * @return the stream processor
     */
    public TriFunction<BundleContext, URL, InputStream, T> getStreamProcessor() {
        return streamProcessor;
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
        private TriConsumer<BundleContext, T, URL> urlAwareBundleItemProcessor = null;
        private Comparator<URL> urlComparator = null;
        private BiConsumer<Map<String, Map<String, T>>, Map<String, Map<String, T>>> postRefreshCallback = null;
        private TriConsumer<String, Map<String, T>, Map<String, T>> tenantRefreshCallback = null;
        private TriFunction<BundleContext, URL, InputStream, T> streamProcessor = null;

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
         * Sets a URL-aware processor for bundle items that includes the resource URL.
         * This is called after an item is loaded from a bundle but before it is persisted.
         * This allows for customization based on both the item and its source URL.
         * If both this and bundleItemProcessor are set, this one takes precedence.
         *
         * @param urlAwareBundleItemProcessor the TriConsumer that processes bundle items with URL access
         * @return the builder
         */
        public Builder<T> withUrlAwareBundleItemProcessor(TriConsumer<BundleContext, T, URL> urlAwareBundleItemProcessor) {
            this.urlAwareBundleItemProcessor = urlAwareBundleItemProcessor;
            return this;
        }

        /**
         * Set a custom comparator for sorting URLs when loading predefined items.
         * 
         * <p>This comparator determines the order in which predefined items are loaded from bundles.
         * When defined, the URLs of predefined items will be sorted using this comparator before
         * loading the items.</p>
         * 
         * <p>This is particularly useful for items that need to be processed in a specific order,
         * such as patches or migrations that must be applied sequentially.</p>
         *
         * @param urlComparator the comparator for sorting URLs
         * @return this builder for method chaining
         */
        public Builder<T> withUrlComparator(Comparator<URL> urlComparator) {
            this.urlComparator = urlComparator;
            return this;
        }

        /**
         * Sets a post-refresh callback that is executed after all items across all tenants have been reloaded.
         * This allows for comparing the old and new states to detect changes and perform additional operations.
         * The first parameter is the old state (Map of tenant ID to a Map of item ID to item).
         * The second parameter is the new state (same structure).
         * 
         * @param postRefreshCallback the callback to execute after a full refresh
         * @return the builder
         */
        public Builder<T> withPostRefreshCallback(BiConsumer<Map<String, Map<String, T>>, Map<String, Map<String, T>>> postRefreshCallback) {
            this.postRefreshCallback = postRefreshCallback;
            return this;
        }

        /**
         * Sets a tenant-specific refresh callback that is executed after each tenant's items have been reloaded.
         * This allows for efficient processing of changes on a per-tenant basis.
         * The first parameter is the tenant ID.
         * The second parameter is the old state for this tenant (Map of item ID to item).
         * The third parameter is the new state for this tenant (same structure).
         * 
         * @param tenantRefreshCallback the callback to execute after each tenant's refresh
         * @return the builder
         */
        public Builder<T> withTenantRefreshCallback(TriConsumer<String, Map<String, T>, Map<String, T>> tenantRefreshCallback) {
            this.tenantRefreshCallback = tenantRefreshCallback;
            return this;
        }

        /**
         * Set a stream processor that will directly process the input stream from a predefined item resource.
         * This is an alternative to the standard deserialization process and allows for custom processing of the raw data.
         * When this processor is defined, it takes precedence over the standard JSON deserialization.
         *
         * <p>The processor is given the bundle context, the URL of the resource, and the input stream to read from.
         * It must return a fully constructed item instance or null if processing fails.</p>
         *
         * <p>This is particularly useful for items that require special processing of the source data before
         * they can be instantiated, such as JSON schemas that need to be validated, parsed, or transformed.</p>
         *
         * @param streamProcessor the function to process the input stream
         * @return the builder instance for method chaining
         */
        public Builder<T> withStreamProcessor(TriFunction<BundleContext, URL, InputStream, T> streamProcessor) {
            this.streamProcessor = streamProcessor;
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

    /**
     * A functional interface for a consumer that accepts three arguments.
     * Similar to BiConsumer but with a third argument.
     *
     * @param <T> the type of the first argument
     * @param <U> the type of the second argument
     * @param <V> the type of the third argument
     */
    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }
} 