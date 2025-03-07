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
package org.apache.unomi.services.common.cache;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.services.common.service.AbstractContextAwareService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

/**
 * Base service supporting multiple cacheable types
 */
public abstract class AbstractMultiTypeCachingService extends AbstractContextAwareService implements SynchronousBundleListener {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected BundleContext bundleContext;
    protected SchedulerService schedulerService;
    protected MultiTypeCacheService cacheService;
    protected TenantService tenantService;

    /**
     * Map tracking which plugin/bundle contributed which items.
     * Key is the bundle ID, value is the list of items contributed by that bundle.
     */
    protected final Map<Long, List<Object>> pluginContributions = new ConcurrentHashMap<>();

    /**
     * Map tracking which plugin/bundle contributed which PluginType items.
     * Key is the bundle ID, value is the list of PluginType items contributed by that bundle.
     */
    protected final Map<Long, List<PluginType>> pluginTypes = new ConcurrentHashMap<>();

    // Each service defines its supported types
    protected abstract Set<CacheableTypeConfig<?>> getTypeConfigs();

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setCacheService(MultiTypeCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public void postConstruct() {
        logger.debug("postConstruct {{}}", bundleContext.getBundle());

        // Initialize caches and load predefined items
        initializeCaches();
        loadPredefinedItems(bundleContext);

        // Process existing bundles
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null &&
                bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                loadPredefinedItems(bundle.getBundleContext());
            }
        }

        bundleContext.addBundleListener(this);
        initializeTimers();

        logger.info("{} service initialized.", getClass().getSimpleName());
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("{} service shutdown.", getClass().getSimpleName());
    }

    protected void initializeCaches() {
        for (CacheableTypeConfig<?> config : getTypeConfigs()) {
            cacheService.registerType(config);
        }
    }

    protected void initializeTimers() {
        // Initialize refresh timers for types that need it
        for (CacheableTypeConfig<?> config : getTypeConfigs()) {
            if (config.isRequiresRefresh()) {
                scheduleTypeRefresh(config);
            }
        }
    }

    protected void scheduleTypeRefresh(CacheableTypeConfig<?> config) {
        Runnable task = () -> {
            try {
                contextManager.executeAsSystem(() -> {
                    try {
                        refreshTypeCache(config);
                    } catch (Exception e) {
                        logger.error("Error refreshing cache for type: " + config.getType(), e);
                    }
                    return null;
                });
            } catch (Exception e) {
                logger.error("Error executing cache refresh as system subject for type: " + config.getType(), e);
            }
        };

        schedulerService.newTask("cache-refresh-" + config.getType().getSimpleName())
            .nonPersistent()  // Cache reloads should not be persisted
            .withPeriod(config.getRefreshInterval(), TimeUnit.MILLISECONDS)
            .withFixedDelay() // Sequential execution
            .withSimpleExecutor(task)
            .schedule();
    }

    @SuppressWarnings("unchecked")
    protected <T extends Serializable> void refreshTypeCache(CacheableTypeConfig<T> config) {
        Set<String> tenants = getTenants();

        for (String tenantId : tenants) {
            contextManager.executeAsTenant(tenantId, () -> {
                List<T> items = loadItemsForTenant(tenantId, config);
                processAndCacheItems(tenantId, items, config);
            });
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends Serializable> List<T> loadItemsForTenant(String tenantId, CacheableTypeConfig<T> config) {
        List<T> items = new ArrayList<>();

        if (config.isPersistable()) {
            // Create tenant condition
            Condition tenantCondition = new Condition();
            ConditionType itemPropertyConditionType = new ConditionType();
            itemPropertyConditionType.setItemId("itemPropertyCondition");
            itemPropertyConditionType.setConditionEvaluator("propertyConditionEvaluator");
            itemPropertyConditionType.setQueryBuilder("propertyConditionESQueryBuilder");

            // Set metadata from JSON
            Metadata metadata = new Metadata();
            metadata.setId("itemPropertyCondition");
            metadata.setName("itemPropertyCondition");
            Set<String> systemTags = new HashSet<>(Arrays.asList(
                "availableToEndUser",
                "sessionBased",
                "profileTags",
                "event",
                "condition",
                "sessionCondition"
            ));
            metadata.setSystemTags(systemTags);
            metadata.setReadOnly(true);
            itemPropertyConditionType.setMetadata(metadata);

            // Set parameters from JSON
            List<Parameter> parameters = new ArrayList<>();
            parameters.add(new Parameter("propertyName", "string", false));
            parameters.add(new Parameter("comparisonOperator", "comparisonOperator", false));
            parameters.add(new Parameter("propertyValue", "string", false));
            parameters.add(new Parameter("propertyValueInteger", "integer", false));
            parameters.add(new Parameter("propertyValueDate", "date", false));
            parameters.add(new Parameter("propertyValueDateExpr", "string", false));
            parameters.add(new Parameter("propertyValues", "string", true));
            parameters.add(new Parameter("propertyValuesInteger", "integer", true));
            parameters.add(new Parameter("propertyValuesDate", "date", true));
            parameters.add(new Parameter("propertyValuesDateExpr", "string", true));
            itemPropertyConditionType.setParameters(parameters);

            tenantCondition.setConditionType(itemPropertyConditionType);
            tenantCondition.setConditionTypeId("itemPropertyCondition");
            Map<String, Object> parameterValues = new HashMap<>();
            parameterValues.put("propertyName", "tenantId");
            parameterValues.put("comparisonOperator", "equals");
            parameterValues.put("propertyValue", tenantId);
            tenantCondition.setParameterValues(parameterValues);

            // Load tenant-specific items
            Class<? extends Item> itemClass = (Class<? extends Item>) config.getType();
            List<T> tenantItems = (List<T>) persistenceService.query(tenantCondition, "priority", itemClass);
            items.addAll(tenantItems);

            // If inheritance is enabled and this is not the system tenant, load inherited items
            if (config.isInheritFromSystemTenant() && !SYSTEM_TENANT.equals(tenantId)) {
                parameterValues.put("propertyValue", SYSTEM_TENANT);
                tenantCondition.setParameterValues(parameterValues);
                List<T> systemItems = (List<T>) persistenceService.query(tenantCondition, "priority", itemClass);

                // Only add system items that don't have tenant overrides
                Set<String> tenantItemIds = tenantItems.stream()
                    .map(config.getIdExtractor())
                    .collect(Collectors.toSet());

                systemItems.stream()
                    .filter(item -> !tenantItemIds.contains(config.getIdExtractor().apply(item)))
                    .forEach(items::add);
            }
        }

        return items;
    }

    protected <T extends Serializable> void processAndCacheItems(String tenantId, List<T> items, CacheableTypeConfig<T> config) {
        for (T item : items) {
            // Apply post-processor if defined
            if (config.getPostProcessor() != null) {
                config.getPostProcessor().accept(item);
            }

            String id = config.getIdExtractor().apply(item);
            cacheService.put(config.getItemType(), id, tenantId, item);
        }
    }

    protected Set<String> getTenants() {
        Set<String> tenants = new HashSet<>();
        for (Tenant tenant : tenantService.getAllTenants()) {
            tenants.add(tenant.getItemId());
        }
        tenants.add(SYSTEM_TENANT);
        return tenants;
    }

    protected void loadPredefinedItems(BundleContext bundleContext) {
        if (bundleContext == null) return;

        for (CacheableTypeConfig<?> config : getTypeConfigs()) {
            if (config.hasPredefinedItems()) {
                loadPredefinedItemsForType(bundleContext, config);
            }
        }
    }

    /**
     * Get all items contributed by a specific bundle.
     *
     * @param bundleId the ID of the bundle
     * @return a list of items contributed by that bundle, or an empty list if none
     */
    protected List<Object> getItemsForBundle(long bundleId) {
        return pluginContributions.getOrDefault(bundleId, Collections.emptyList());
    }

    /**
     * Track a new item as being contributed by a specific bundle.
     *
     * @param bundleId the ID of the contributing bundle
     * @param item the item being contributed
     */
    protected void addPluginContribution(long bundleId, Object item) {
        pluginContributions.computeIfAbsent(bundleId, k -> new CopyOnWriteArrayList<>()).add(item);
    }

    @SuppressWarnings("unchecked")
    protected <T extends Serializable> void loadPredefinedItemsForType(BundleContext bundleContext, CacheableTypeConfig<T> config) {
        // Skip if this type doesn't have predefined items
        if (!config.hasPredefinedItems()) {
            return;
        }

        Enumeration<URL> entries = bundleContext.getBundle()
            .findEntries("META-INF/cxs/" + config.getMetaInfPath(), "*.json", true);
        if (entries == null) return;

        // If a URL comparator is defined, sort the URLs
        List<URL> entryList;
        if (config.hasUrlComparator()) {
            entryList = Collections.list(entries);
            entryList.sort(config.getUrlComparator());
        } else {
            entryList = Collections.list(entries);
        }

        for (URL entryURL : entryList) {
            logger.debug("Found predefined {} at {}, loading... ",
                config.getType().getSimpleName(), entryURL);

            try (BufferedInputStream bis = new BufferedInputStream(entryURL.openStream())) {
                T item = CustomObjectMapper.getObjectMapper().readValue(bis, config.getType());

                // Track this item as contributed by this bundle
                final long bundleId = bundleContext.getBundle().getBundleId();

                // Process in system context to ensure permissions
                contextManager.executeAsSystem(() -> {
                    try {
                        // Set plugin ID if item supports it
                        if (item instanceof PluginType) {
                            try {
                                PluginType pluginTypeItem = (PluginType) item;
                                pluginTypeItem.setPluginId(bundleId);
                            } catch (Exception e) {
                                logger.warn("Error setting plugin ID on item {}: {}", item, e.getMessage());
                            }
                        }
                        if (item instanceof Item) {
                            Item itemObj = (Item) item;
                            if (itemObj.getTenantId() == null) {
                                itemObj.setTenantId(SYSTEM_TENANT);
                            }
                        }

                        // Apply the bundle-aware processor if configured
                        if (config.hasBundleItemProcessor()) {
                            config.getBundleItemProcessor().accept(bundleContext, item);
                        }
                        // Apply post-processor if defined
                        else if (config.getPostProcessor() != null) {
                            config.getPostProcessor().accept(item);
                        }

                        // Track contribution
                        addPluginContribution(bundleId, item);

                        // Also track as PluginType if applicable
                        if (item instanceof PluginType) {
                            PluginType pluginTypeItem = (PluginType) item;
                            pluginTypes.computeIfAbsent(bundleId, k -> new CopyOnWriteArrayList<>()).add(pluginTypeItem);
                        }

                        // Add to cache
                        String id = config.getIdExtractor().apply(item);
                        cacheService.put(config.getItemType(), id, SYSTEM_TENANT, item);

                        logger.info("Predefined {} registered: {}",
                            config.getType().getSimpleName(), id);
                    } catch (Exception e) {
                        logger.error("Error processing {} definition {}",
                            config.getType().getSimpleName(), entryURL, e);
                    }
                    return null;
                });
            } catch (IOException e) {
                logger.error("Error loading {} definition {}",
                    config.getType().getSimpleName(), entryURL, e);
            }
        }
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        contextManager.executeAsSystem(() -> {
            switch (event.getType()) {
                case BundleEvent.STARTED:
                    processBundleStartup(event.getBundle().getBundleContext());
                    break;
                case BundleEvent.STOPPING:
                    processBundleStop(event.getBundle());
                    break;
            }
            return null;
        });
    }

    /**
     * Process bundle startup, loading any predefined items from the bundle.
     * Override to add additional processing.
     *
     * @param bundleContext the context of the started bundle
     */
    protected void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext != null) {
            loadPredefinedItems(bundleContext);
        }
    }

    /**
     * Process bundle stop, removing any items contributed by the bundle.
     * Override to add additional processing.
     *
     * @param bundle the stopping bundle
     */
    protected void processBundleStop(Bundle bundle) {
        if (bundle != null) {
            long bundleId = bundle.getBundleId();
            List<Object> bundleItems = getItemsForBundle(bundleId);

            for (Object item : bundleItems) {
                // Handle removal of cached items - details would depend on item type
                if (item instanceof Item) {
                    Item typedItem = (Item) item;
                    removeItemOnBundleStop(typedItem, typedItem.getItemId(), typedItem.getItemType());
                }
            }

            // Allow subclasses to perform additional cleanup
            onBundleStop(bundle);

            // Clean up the tracking maps
            pluginContributions.remove(bundleId);
            pluginTypes.remove(bundleId);
        }
    }

    /**
     * Hook method for subclasses to perform additional cleanup when a bundle stops.
     * Default implementation does nothing.
     *
     * @param bundle the stopping bundle
     */
    protected void onBundleStop(Bundle bundle) {
        // Default implementation does nothing
    }

    /**
     * Remove an item from caches and persistence when its contributing bundle stops.
     * Override in subclasses for type-specific handling as needed.
     *
     * @param item the item to remove
     * @param itemId the ID of the item
     * @param itemType the type of the item
     */
    @SuppressWarnings("unchecked")
    protected void removeItemOnBundleStop(Object item, String itemId, String itemType) {
        if (itemId != null && itemType != null) {
            try {
                // Remove from cache with system tenant (predefined items use system tenant)
                Class<?> itemClass = item.getClass();

                // We need to use raw types here due to Java's type erasure
                // and how the remove method is typed - this is safe because
                // the cache service checks types at runtime
                cacheService.remove(itemType, itemId, SYSTEM_TENANT, (Class) itemClass);

                // If persistable, also remove from persistence
                if (item instanceof Item) {
                    persistenceService.remove(itemId, (Class) itemClass);
                }
            } catch (Exception e) {
                logger.error("Error removing {} with ID {} on bundle stop",
                    item.getClass().getSimpleName(), itemId, e);
            }
        }
    }

    /**
     * Get a map of all plugin types indexed by plugin ID (bundle ID).
     *
     * @return Map where key is the bundle ID, value is the list of plugin types from that bundle
     */
    public Map<Long, List<PluginType>> getTypesByPlugin() {
        return pluginTypes;
    }

    /**
     * Get all items of a specific type for the current tenant.
     *
     * @param <T> the type of items to retrieve
     * @param itemClass the class of the items to retrieve
     * @return a collection of all items of the specified type
     */
    protected <T extends Serializable> Collection<T> getAllItems(Class<T> itemClass, boolean withInherited) {
        String tenantId = contextManager.getCurrentContext().getTenantId();
        if (withInherited) {
            return new ArrayList<>(cacheService.getValuesByPredicateWithInheritance(tenantId, itemClass, t -> true));
        }
        return new ArrayList<>(cacheService.getTenantCache(tenantId, itemClass).values());
    }

    /**
     * Get items of a specific type filtered by tag.
     *
     * @param <T> the type of items to retrieve
     * @param itemClass the class of the items to retrieve
     * @param tag the tag to filter by
     * @return a set of items matching the specified tag
     */
    protected <T extends Item & Serializable> Set<T> getItemsByTag(Class<T> itemClass, String tag) {
        String tenantId = contextManager.getCurrentContext().getTenantId();
        return cacheService.getValuesByPredicateWithInheritance(
            tenantId,
            itemClass,
            item -> item instanceof MetadataItem && ((MetadataItem) item).getMetadata() != null && ((MetadataItem) item).getMetadata().getTags().contains(tag)
        );
    }

    /**
     * Get items of a specific type filtered by system tag.
     *
     * @param <T> the type of items to retrieve
     * @param itemClass the class of the items to retrieve
     * @param systemTag the system tag to filter by
     * @return a set of items matching the specified system tag
     */
    protected <T extends Item & Serializable> Set<T> getItemsBySystemTag(Class<T> itemClass, String systemTag) {
        String tenantId = contextManager.getCurrentContext().getTenantId();
        return cacheService.getValuesByPredicateWithInheritance(
            tenantId,
            itemClass,
            item -> item instanceof MetadataItem && ((MetadataItem) item).getMetadata() != null && ((MetadataItem) item).getMetadata().getSystemTags().contains(systemTag)
        );
    }

    /**
     * Get a specific item by ID.
     *
     * @param <T> the type of item to retrieve
     * @param id the ID of the item
     * @param itemClass the class of the item
     * @return the item with the specified ID, or null if not found
     */
    protected <T extends Serializable> T getItem(String id, Class<T> itemClass) {
        String tenantId = contextManager.getCurrentContext().getTenantId();
        return cacheService.getWithInheritance(id, tenantId, itemClass);
    }

    /**
     * Save an item to the cache and persistence.
     *
     * @param <T> the type of item to save
     * @param item the item to save
     * @param idExtractor function to extract the ID from the item
     * @param itemType the type identifier for the item
     */
    protected <T extends Item & Serializable> void saveItem(T item, Function<T, String> idExtractor, String itemType) {
        if (item instanceof MetadataItem && ((MetadataItem) item).getMetadata() == null || ((MetadataItem) item).getMetadata().getId() == null) {
            logger.warn("Cannot save item without metadata ID");
            return;
        }

        String currentTenant = contextManager.getCurrentContext().getTenantId();
        persistenceService.save(item);
        cacheService.put(itemType, idExtractor.apply(item), currentTenant, item);
    }

    /**
     * Remove an item from the cache and persistence.
     *
     * @param <T> the type of item to remove
     * @param id the ID of the item to remove
     * @param itemClass the class of the item
     * @param itemType the type identifier for the item
     */
    protected <T extends Item & Serializable> void removeItem(String id, Class<T> itemClass, String itemType) {
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        persistenceService.remove(id, itemClass);
        cacheService.remove(itemType, id, currentTenant, itemClass);
    }
}
