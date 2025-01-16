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

package org.apache.unomi.services.impl.definitions;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.PropertyMergeStrategyType;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.TenantLifecycleListener;
import org.apache.unomi.api.utils.ConditionBuilder;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.services.impl.AbstractTenantAwareService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component(service = {DefinitionsService.class, TenantLifecycleListener.class})
public class DefinitionsServiceImpl extends AbstractTenantAwareService implements DefinitionsService, TenantLifecycleListener, SynchronousBundleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefinitionsServiceImpl.class.getName());

    private SchedulerService schedulerService;
    private volatile boolean isShutdown = false;
    private volatile java.util.concurrent.ScheduledFuture<?> reloadTaskFuture;

    // Tenant-aware caches
    private final Map<String, Map<String, ConditionType>> conditionTypeByTenantId = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ActionType>> actionTypeByTenantId = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ValueType>> valueTypeByTenantId = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<ValueType>>> valueTypeByTagByTenantId = new ConcurrentHashMap<>();
    private final Map<String, Map<String, PropertyMergeStrategyType>> propertyMergeStrategyTypeByTenantId = new ConcurrentHashMap<>();
    private final Map<Long, List<PluginType>> pluginTypes = new ConcurrentHashMap<>();

    // Generic cache management with better synchronization
    private <T> Map<String, T> getTenantCache(Map<String, Map<String, T>> tenantMap, String tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        synchronized (cacheLock) {
            return tenantMap.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        }
    }

    private <T> T getFromCacheWithInheritance(String id, String tenantId, Map<String, Map<String, T>> cache) {
        if (id == null) {
            return null;
        }
        T value = getTenantCache(cache, tenantId).get(id);
        if (value == null && !SYSTEM_TENANT.equals(tenantId)) {
            value = getTenantCache(cache, SYSTEM_TENANT).get(id);
        }
        return value;
    }

    private <T> void updateCache(Map<String, Map<String, T>> cacheMap, String tenantId, Map<String, T> newItems) {
        synchronized(cacheLock) {
            Map<String, T> cache = getTenantCache(cacheMap, tenantId);
            cache.clear();
            cache.putAll(newItems);
        }
    }

    private <T> void removeFromCache(Map<String, Map<String, T>> cacheMap, String tenantId, String itemId) {
        if (tenantId == null || itemId == null) {
            return;
        }
        synchronized(cacheLock) {
            Map<String, T> cache = getTenantCache(cacheMap, tenantId);
            cache.remove(itemId);
        }
    }

    // Simplified helper methods using generic cache management
    private Map<String, ConditionType> getConditionTypeCache(String tenantId) {
        return getTenantCache(conditionTypeByTenantId, tenantId);
    }

    private Map<String, ActionType> getActionTypeCache(String tenantId) {
        return getTenantCache(actionTypeByTenantId, tenantId);
    }

    private Map<String, ValueType> getValueTypeCache(String tenantId) {
        return getTenantCache(valueTypeByTenantId, tenantId);
    }

    private Map<String, Set<ValueType>> getValueTypeByTagCache(String tenantId) {
        return getTenantCache(valueTypeByTagByTenantId, tenantId);
    }

    private Map<String, PropertyMergeStrategyType> getPropertyMergeStrategyTypeCache(String tenantId) {
        return getTenantCache(propertyMergeStrategyTypeByTenantId, tenantId);
    }

    private long definitionsRefreshInterval = 10000;

    private ConditionBuilder conditionBuilder;
    private BundleContext bundleContext;

    private static final int MAX_RECURSIVE_CONDITIONS = 1000; // Prevent stack overflow
    private static final String SYSTEM_TENANT = "system";
    private static final String BOOLEAN_CONDITION_TYPE = "booleanCondition";
    private static final String AND_OPERATOR = "and";
    private static final String SUB_CONDITIONS_PARAM = "subConditions";
    private static final String OPERATOR_PARAM = "operator";

    private static final long TASK_TIMEOUT_MS = 60000; // 1 minute timeout for tasks
    private final Object cacheLock = new Object(); // Dedicated lock object

    public DefinitionsServiceImpl() {
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setDefinitionsRefreshInterval(long definitionsRefreshInterval) {
        this.definitionsRefreshInterval = definitionsRefreshInterval;
    }

    public void postConstruct() {
        LOGGER.debug("postConstruct {{}}", bundleContext.getBundle());

        processBundleStartup(bundleContext);

        // process already started bundles
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                processBundleStartup(bundle.getBundleContext());
            }
        }

        bundleContext.addBundleListener(this);
        scheduleTypeReloads();
        conditionBuilder = new ConditionBuilder(this);
        LOGGER.info("Definitions service initialized.");
    }

    private void scheduleTypeReloads() {
        if (isShutdown || schedulerService == null) {
            return;
        }

        Runnable task = new TypeReloadTask();
        try {
            reloadTaskFuture = schedulerService.getScheduleExecutorService()
                .scheduleAtFixedRate(task, 10000, definitionsRefreshInterval, TimeUnit.MILLISECONDS);
            LOGGER.info("Scheduled type reload task with interval: {}ms", definitionsRefreshInterval);
        } catch (Exception e) {
            LOGGER.error("Failed to schedule type reload task", e);
        }
    }

    public void reloadTypes(boolean refresh) {
        if (isShutdown) {
            return;
        }

        try {
            if (refresh && persistenceService != null) {
                persistenceService.refreshIndex(ConditionType.class);
                persistenceService.refreshIndex(ActionType.class);
            }
            loadConditionTypesFromPersistence();
            loadActionTypesFromPersistence();
        } catch (Throwable t) {
            LOGGER.error("Error loading definitions from persistence back-end", t);
        }
    }

    private void loadConditionTypesFromPersistence() {
        if (persistenceService == null || tenantService == null) {
            LOGGER.warn("Cannot load condition types - required services not available");
            return;
        }

        try {
            String currentTenant = tenantService.getCurrentTenantId();
            Map<String, ConditionType> newConditionTypes = new ConcurrentHashMap<>();
            Collection<ConditionType> types = getAllConditionTypes();
            if (types != null) {
                for (ConditionType conditionType : types) {
                    if (conditionType != null && conditionType.getItemId() != null) {
                        newConditionTypes.put(conditionType.getItemId(), conditionType);
                    }
                }
            }

            // Atomic update of cache
            synchronized(cacheLock) {
                Map<String, ConditionType> cache = getConditionTypeCache(currentTenant);
                cache.clear();  // Clear old entries
                cache.putAll(newConditionTypes);
            }
        } catch (Exception e) {
            LOGGER.error("Error loading condition types from persistence service", e);
        }
    }

    private void loadActionTypesFromPersistence() {
        if (persistenceService == null || tenantService == null) {
            LOGGER.warn("Cannot load action types - required services not available");
            return;
        }

        try {
            String currentTenant = tenantService.getCurrentTenantId();
            Map<String, ActionType> newActionTypes = new ConcurrentHashMap<>();
            Collection<ActionType> types = getAllActionTypes();
            if (types != null) {
                for (ActionType actionType : types) {
                    if (actionType != null && actionType.getItemId() != null) {
                        newActionTypes.put(actionType.getItemId(), actionType);
                    }
                }
            }
            updateCache(actionTypeByTenantId, currentTenant, newActionTypes);
        } catch (Exception e) {
            LOGGER.error("Error loading action types from persistence service", e);
        }
    }

    protected void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null || isShutdown) {
            return;
        }

        Long bundleId = null;
        try {
            Bundle bundle = bundleContext.getBundle();
            if (bundle == null) {
                LOGGER.warn("No bundle found in context during startup");
                return;
            }
            bundleId = bundle.getBundleId();

            loadPredefinedConditionTypes(bundleContext);
            loadPredefinedActionTypes(bundleContext);
            loadPredefinedValueTypes(bundleContext);
            loadPredefinedPropertyMergeStrategyTypes(bundleContext);

        } catch (Exception e) {
            LOGGER.error("Error during bundle startup processing for bundle: {}", bundleId, e);
            // Cleanup on failure
            if (bundleId != null) {
                pluginTypes.remove(bundleId);
            }
        }
    }

    protected void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }

        Long bundleId = bundleContext.getBundle().getBundleId();
        List<PluginType> types = pluginTypes.remove(bundleId);
        if (types == null || types.isEmpty()) {
            return;
        }

        try {
            synchronized(cacheLock) {
                // Process each type based on its actual class
                for (PluginType type : types) {
                    if (type instanceof ConditionType) {
                        removeConditionTypeFromAllTenants((ConditionType) type);
                    } else if (type instanceof ActionType) {
                        removeActionTypeFromAllTenants((ActionType) type);
                    } else if (type instanceof ValueType) {
                        removeValueTypeFromAllTenants((ValueType) type);
                    } else if (type instanceof PropertyMergeStrategyType) {
                        removePropertyMergeStrategyFromAllTenants((PropertyMergeStrategyType) type);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error during bundle stop processing for bundle ID: {}", bundleId, e);
        }
    }

    private void removeConditionTypeFromAllTenants(ConditionType conditionType) {
        if (conditionType == null || conditionType.getItemId() == null) {
            return;
        }

        // Remove from persistence service
        persistenceService.remove(conditionType.getItemId(), ConditionType.class);

        // Remove from all tenant caches
        conditionTypeByTenantId.values().forEach(cache ->
            cache.remove(conditionType.getItemId()));
    }

    private void removeActionTypeFromAllTenants(ActionType actionType) {
        if (actionType == null || actionType.getItemId() == null) {
            return;
        }

        // Remove from persistence service
        persistenceService.remove(actionType.getItemId(), ActionType.class);

        // Remove from all tenant caches
        actionTypeByTenantId.values().forEach(cache ->
            cache.remove(actionType.getItemId()));
    }

    private void removeValueTypeFromAllTenants(ValueType valueType) {
        if (valueType == null || valueType.getId() == null) {
            return;
        }

        // Remove from value type caches
        valueTypeByTenantId.values().forEach(cache -> cache.remove(valueType.getId()));

        // Remove from tag caches
        Set<String> tags = valueType.getTags();
        if (tags != null) {
            valueTypeByTagByTenantId.values().forEach(tagCache ->
                tags.forEach(tag -> {
                    Set<ValueType> valueTypes = tagCache.get(tag);
                    if (valueTypes != null) {
                        synchronized(valueTypes) {
                            valueTypes.remove(valueType);
                            if (valueTypes.isEmpty()) {
                                tagCache.remove(tag);
                            }
                        }
                    }
                }));
        }
    }

    private void removePropertyMergeStrategyFromAllTenants(PropertyMergeStrategyType strategyType) {
        if (strategyType == null || strategyType.getId() == null) {
            return;
        }
        propertyMergeStrategyTypeByTenantId.values()
            .forEach(cache -> cache.remove(strategyType.getId()));
    }

    private void removeSystemTenantTypes() {
        // Remove condition types
        conditionTypeByTenantId.values().forEach(cache -> {
            new ArrayList<>(cache.values()).stream()
                .filter(type -> type != null && SYSTEM_TENANT.equals(type.getTenantId()))
                .forEach(type -> {
                    cache.remove(type.getItemId());
                    persistenceService.remove(type.getItemId(), ConditionType.class);
                });
        });

        // Remove action types
        actionTypeByTenantId.values().forEach(cache -> {
            new ArrayList<>(cache.values()).stream()
                .filter(type -> type != null && SYSTEM_TENANT.equals(type.getTenantId()))
                .forEach(type -> {
                    cache.remove(type.getItemId());
                    persistenceService.remove(type.getItemId(), ActionType.class);
                });
        });
    }

    public void preDestroy() {
        isShutdown = true;
        if (reloadTaskFuture != null) {
            reloadTaskFuture.cancel(false);
            reloadTaskFuture = null;
        }
        if (bundleContext != null) {
            bundleContext.removeBundleListener(this);
        }
        LOGGER.info("Definitions service shutdown.");
    }

    // Generic method for loading predefined types
    private <T> void loadPredefinedTypes(BundleContext bundleContext, String path, Class<T> typeClass,
            Consumer<T> typeProcessor) {
        if (bundleContext == null || path == null || typeClass == null || typeProcessor == null) {
            LOGGER.warn("Invalid parameters provided for loading predefined types");
            return;
        }

        Bundle bundle = bundleContext.getBundle();
        if (bundle == null) {
            LOGGER.warn("No bundle found in context");
            return;
        }

        Enumeration<URL> entries = bundle.findEntries(path, "*.json", true);
        if (entries == null) {
            return;
        }

        while (entries.hasMoreElements()) {
            URL entryURL = entries.nextElement();
            if (entryURL == null) {
                continue;
            }

            LOGGER.debug("Found predefined {} at {}, loading... ", typeClass.getSimpleName(), entryURL);

            try (BufferedInputStream bis = new BufferedInputStream(entryURL.openStream())) {
                T type = CustomObjectMapper.getObjectMapper().readValue(bis, typeClass);
                if (type != null) {
                    try {
                        typeProcessor.accept(type);
                        LOGGER.info("Predefined {} registered", typeClass.getSimpleName());
                    } catch (Exception e) {
                        LOGGER.error("Error processing {} definition {}", typeClass.getSimpleName(), entryURL, e);
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Error while loading {} definition {}", typeClass.getSimpleName(), entryURL, e);
            }
        }
    }

    private void loadPredefinedConditionTypes(BundleContext bundleContext) {
        loadPredefinedTypes(bundleContext, "META-INF/cxs/conditions", ConditionType.class, type -> {
            synchronized(pluginTypes) {
                type.setPluginId(bundleContext.getBundle().getBundleId());
                type.setTenantId("system");
                setConditionType(type);
                List<PluginType> bundlePluginTypes = pluginTypes.computeIfAbsent(
                    bundleContext.getBundle().getBundleId(), k -> new CopyOnWriteArrayList<>());
                bundlePluginTypes.add(type);
            }
        });
    }

    private void loadPredefinedActionTypes(BundleContext bundleContext) {
        loadPredefinedTypes(bundleContext, "META-INF/cxs/actions", ActionType.class, type -> {
            synchronized(pluginTypes) {
                type.setPluginId(bundleContext.getBundle().getBundleId());
                type.setTenantId("system");
                setActionType(type);
                List<PluginType> bundlePluginTypes = pluginTypes.computeIfAbsent(
                    bundleContext.getBundle().getBundleId(), k -> new CopyOnWriteArrayList<>());
                bundlePluginTypes.add(type);
            }
        });
    }

    private void loadPredefinedValueTypes(BundleContext bundleContext) {
        loadPredefinedTypes(bundleContext, "META-INF/cxs/values", ValueType.class, type -> {
            synchronized(pluginTypes) {
                type.setPluginId(bundleContext.getBundle().getBundleId());
                setValueType(type);
                List<PluginType> bundlePluginTypes = pluginTypes.computeIfAbsent(
                    bundleContext.getBundle().getBundleId(), k -> new CopyOnWriteArrayList<>());
                bundlePluginTypes.add(type);
            }
        });
    }

    private void loadPredefinedPropertyMergeStrategyTypes(BundleContext bundleContext) {
        loadPredefinedTypes(bundleContext, "META-INF/cxs/mergers", PropertyMergeStrategyType.class, type -> {
            synchronized(pluginTypes) {
                type.setPluginId(bundleContext.getBundle().getBundleId());
                List<PluginType> bundlePluginTypes = pluginTypes.computeIfAbsent(
                    bundleContext.getBundle().getBundleId(), k -> new CopyOnWriteArrayList<>());
                bundlePluginTypes.add(type);
                getPropertyMergeStrategyTypeCache("system").put(type.getId(), type);
            }
        });
    }

    public Map<Long, List<PluginType>> getTypesByPlugin() {
        return pluginTypes;
    }

    public Collection<ConditionType> getAllConditionTypes() {
        Collection<ConditionType> all = persistenceService.getAllItems(ConditionType.class);
        for (ConditionType type : all) {
            if (type != null && type.getParentCondition() != null) {
                ParserHelper.resolveConditionType(this, type.getParentCondition(), "condition type " + type.getItemId());
            }
        }
        return all;
    }

    public Set<ConditionType> getConditionTypesByTag(String tag) {
        return getConditionTypesBy("metadata.tags", tag);
    }

    public Set<ConditionType> getConditionTypesBySystemTag(String tag) {
        return getConditionTypesBy("metadata.systemTags", tag);
    }

    private Set<ConditionType> getConditionTypesBy(String fieldName, String fieldValue) {
        Set<ConditionType> conditionTypes = new LinkedHashSet<ConditionType>();
        String currentTenant = tenantService.getCurrentTenantId();

        // Get types from current tenant
        List<ConditionType> directConditionTypes = persistenceService.query(fieldName, fieldValue, null, ConditionType.class);
        Map<String, ConditionType> tenantSpecificTypes = new HashMap<>();
        for (ConditionType type : directConditionTypes) {
            if (type.getTenantId() != null && type.getTenantId().equals(currentTenant)) {
                if (type.getParentCondition() != null) {
                    ParserHelper.resolveConditionType(this, type.getParentCondition(), "condition type " + type.getItemId());
                }
                tenantSpecificTypes.put(type.getItemId(), type);
            }
        }

        // If not in system tenant, also get inherited types from system tenant
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            List<ConditionType> systemConditionTypes = persistenceService.query(fieldName, fieldValue, null, ConditionType.class);
            for (ConditionType type : systemConditionTypes) {
                if (type.getTenantId() != null && type.getTenantId().equals(SYSTEM_TENANT)) {
                    // Only add system type if no tenant-specific type exists with the same ID
                    if (!tenantSpecificTypes.containsKey(type.getItemId())) {
                        if (type.getParentCondition() != null) {
                            ParserHelper.resolveConditionType(this, type.getParentCondition(), "condition type " + type.getItemId());
                        }
                        tenantSpecificTypes.put(type.getItemId(), type);
                    }
                }
            }
        }

        return new LinkedHashSet<>(tenantSpecificTypes.values());
    }

    public ConditionType getConditionType(String id) {
        if (id == null) {
            return null;
        }
        String currentTenant = tenantService.getCurrentTenantId();
        ConditionType type = getConditionTypeCache(currentTenant).get(id);
        if (type == null || type.getVersion() == null) {
            type = loadWithInheritance(id, ConditionType.class);
            if (type != null) {
                getConditionTypeCache(type.getTenantId()).put(id, type);
            }
        }
        if (type != null && type.getParentCondition() != null) {
            String typeId = type.getItemId();
            if (typeId != null) {
                ParserHelper.resolveConditionType(this, type.getParentCondition(),
                    "condition type " + typeId);
            } else {
                LOGGER.warn("Found condition type with null itemId");
            }
        }
        return type;
    }

    public void removeConditionType(String conditionTypeId) {
        String currentTenant = tenantService.getCurrentTenantId();
        getConditionTypeCache(currentTenant).remove(conditionTypeId);
        persistenceService.remove(conditionTypeId, ConditionType.class);
    }

    public void setConditionType(ConditionType conditionType) {
        if (conditionType == null) {
            LOGGER.warn("Attempt to set null condition type");
            return;
        }

        String tenantId = tenantService.getCurrentTenantId();

        if (conditionType.getTenantId() == null) {
            conditionType.setTenantId(tenantId);
        }

        if (conditionType.getMetadata() == null) {
            LOGGER.warn("Condition type has null metadata");
            return;
        }

        String metadataId = conditionType.getMetadata().getId();
        if (metadataId == null) {
            LOGGER.warn("Condition type has null metadata ID");
            return;
        }

        try {
            saveWithTenant(conditionType);
            getConditionTypeCache(tenantId).put(metadataId, conditionType);
        } catch (Exception e) {
            LOGGER.error("Error setting condition type: {}", metadataId, e);
        }
    }

    public Collection<ActionType> getAllActionTypes() {
        return persistenceService.getAllItems(ActionType.class);
    }

    public Set<ActionType> getActionTypeByTag(String tag) {
        return getActionTypesBy("metadata.tags", tag);
    }

    public Set<ActionType> getActionTypeBySystemTag(String tag) {
        return getActionTypesBy("metadata.systemTags", tag);
    }

    private Set<ActionType> getActionTypesBy(String fieldName, String fieldValue) {
        Map<String, ActionType> actionTypes = new LinkedHashMap<>();
        String currentTenant = tenantService.getCurrentTenantId();

        // Get types from current tenant
        List<ActionType> directActionTypes = persistenceService.query(fieldName, fieldValue, null, ActionType.class);
        for (ActionType type : directActionTypes) {
            if (type.getTenantId() != null && type.getTenantId().equals(currentTenant)) {
                actionTypes.put(type.getItemId(), type);
            }
        }

        // If not in system tenant, also get inherited types from system tenant
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            List<ActionType> systemActionTypes = persistenceService.query(fieldName, fieldValue, null, ActionType.class);
            for (ActionType type : systemActionTypes) {
                if (type.getTenantId() != null && type.getTenantId().equals(SYSTEM_TENANT)) {
                    // Only add system type if no tenant-specific type exists with the same ID
                    if (!actionTypes.containsKey(type.getItemId())) {
                        actionTypes.put(type.getItemId(), type);
                    }
                }
            }
        }

        return new LinkedHashSet<>(actionTypes.values());
    }

    public ActionType getActionType(String id) {
        if (id == null) {
            return null;
        }
        String currentTenant = tenantService.getCurrentTenantId();
        ActionType type = getActionTypeCache(currentTenant).get(id);
        if (type == null || type.getVersion() == null) {
            type = loadWithInheritance(id, ActionType.class);
            if (type != null) {
                getActionTypeCache(type.getTenantId()).put(id, type);
            }
        }
        return type;
    }

    public void removeActionType(String actionTypeId) {
        String currentTenant = tenantService.getCurrentTenantId();
        getActionTypeCache(currentTenant).remove(actionTypeId);
        persistenceService.remove(actionTypeId, ActionType.class);
    }

    public void setActionType(ActionType actionType) {
        if (actionType == null) {
            LOGGER.warn("Attempt to set null action type");
            return;
        }

        String tenantId = tenantService.getCurrentTenantId();
        if (tenantId == null) {
            actionType.setTenantId(tenantId);
            return;
        }

        if (actionType.getMetadata() == null) {
            LOGGER.warn("Action type has null metadata");
            return;
        }

        String metadataId = actionType.getMetadata().getId();
        if (metadataId == null) {
            LOGGER.warn("Action type has null metadata ID");
            return;
        }

        try {
            saveWithTenant(actionType);
            getActionTypeCache(tenantId).put(metadataId, actionType);
        } catch (Exception e) {
            LOGGER.error("Error setting action type: {}", metadataId, e);
        }
    }

    public Collection<ValueType> getAllValueTypes() {
        // Use ConcurrentHashMap for thread safety
        Map<String, ValueType> allValueTypes = new ConcurrentHashMap<>();
        for (Map.Entry<String, Map<String, ValueType>> entry : valueTypeByTenantId.entrySet()) {
            if (entry.getValue() != null) {
                allValueTypes.putAll(new HashMap<>(entry.getValue())); // Create defensive copy
            }
        }
        return Collections.unmodifiableCollection(allValueTypes.values());
    }

    public Set<ValueType> getValueTypeByTag(String tag) {
        if (tag == null) {
            return Collections.emptySet();
        }

        Map<String, ValueType> valueTypes = new LinkedHashMap<>();
        String currentTenant = tenantService.getCurrentTenantId();

        // Get types from current tenant's cache
        Map<String, Set<ValueType>> currentTenantTagCache = getValueTypeByTagCache(currentTenant);
        Set<ValueType> currentTenantTypes = currentTenantTagCache.get(tag);
        if (currentTenantTypes != null) {
            for (ValueType type : currentTenantTypes) {
                valueTypes.put(type.getId(), type);
            }
        }

        // If not in system tenant, also get inherited types from system tenant's cache
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            Map<String, Set<ValueType>> systemTenantTagCache = getValueTypeByTagCache(SYSTEM_TENANT);
            Set<ValueType> systemTenantTypes = systemTenantTagCache.get(tag);
            if (systemTenantTypes != null) {
                for (ValueType type : systemTenantTypes) {
                    // Only add system type if no tenant-specific type exists with the same ID
                    if (!valueTypes.containsKey(type.getId())) {
                        valueTypes.put(type.getId(), type);
                    }
                }
            }
        }

        return new LinkedHashSet<>(valueTypes.values());
    }

    public ValueType getValueType(String id) {
        if (id == null) {
            return null;
        }
        String currentTenant = tenantService.getCurrentTenantId();
        return getFromCacheWithInheritance(id, currentTenant, valueTypeByTenantId);
    }

    public void bundleChanged(BundleEvent event) {
        if (event == null) {
            return;
        }

        Bundle bundle = event.getBundle();
        if (bundle == null) {
            LOGGER.warn("Received bundle event with null bundle");
            return;
        }

        try {
            BundleContext context = bundle.getBundleContext();
            if (context == null) {
                LOGGER.warn("Bundle {} has no context", bundle.getBundleId());
                return;
            }

            switch (event.getType()) {
                case BundleEvent.STARTED:
                    processBundleStartup(context);
                    break;
                case BundleEvent.STOPPING:
                    processBundleStop(context);
                    break;
                default:
                    // Ignore other event types
            }
        } catch (Exception e) {
            LOGGER.error("Error handling bundle event for bundle: {}", bundle.getBundleId(), e);
        }
    }

    public PropertyMergeStrategyType getPropertyMergeStrategyType(String id) {
        if (id == null) {
            return null;
        }
        String currentTenant = tenantService.getCurrentTenantId();
        PropertyMergeStrategyType type = getPropertyMergeStrategyTypeCache(currentTenant).get(id);
        if (type == null) {
            type = getPropertyMergeStrategyTypeCache("system").get(id);
        }
        return type;
    }

    public Set<Condition> extractConditionsByType(Condition rootCondition, String typeId) {
        if (rootCondition == null || typeId == null) {
            return Collections.emptySet();
        }

        Set<Condition> result = new HashSet<>();
        extractConditionsByTypeRecursive(rootCondition, typeId, result, 0);
        return Collections.unmodifiableSet(result);
    }

    private void extractConditionsByTypeRecursive(Condition condition, String typeId, Set<Condition> accumulator, int depth) {
        if (condition == null || depth > MAX_RECURSIVE_CONDITIONS) {
            return;
        }

        if (condition.containsParameter(SUB_CONDITIONS_PARAM)) {
            List<Condition> subConditions = getSubConditions(condition);
            for (Condition subCondition : subConditions) {
                extractConditionsByTypeRecursive(subCondition, typeId, accumulator, depth + 1);
            }
        } else if (typeId != null && typeId.equals(condition.getConditionTypeId())) {
            accumulator.add(condition);
        }
    }

    /**
     * @deprecated As of version 1.2.0-incubating, use {@link #extractConditionBySystemTag(Condition, String)} instead
     */
    @Deprecated
    public Condition extractConditionByTag(Condition rootCondition, String tag) {
        if (rootCondition.containsParameter("subConditions")) {
            @SuppressWarnings("unchecked")
            List<Condition> subConditions = (List<Condition>) rootCondition.getParameter("subConditions");
            List<Condition> matchingConditions = new ArrayList<Condition>();
            for (Condition condition : subConditions) {
                Condition c = extractConditionByTag(condition, tag);
                if (c != null) {
                    matchingConditions.add(c);
                }
            }
            if (matchingConditions.size() == 0) {
                return null;
            } else if (matchingConditions.equals(subConditions)) {
                return rootCondition;
            } else if (rootCondition.getConditionTypeId().equals("booleanCondition") && "and".equals(rootCondition.getParameter("operator"))) {
                if (matchingConditions.size() == 1) {
                    return matchingConditions.get(0);
                } else {
                    Condition res = new Condition();
                    res.setConditionType(getConditionType("booleanCondition"));
                    res.setParameter("operator", "and");
                    res.setParameter("subConditions", matchingConditions);
                    return res;
                }
            }
            throw new IllegalArgumentException();
        } else if (rootCondition.getConditionType() != null && rootCondition.getConditionType().getMetadata().getTags().contains(tag)) {
            return rootCondition;
        } else {
            return null;
        }
    }

    public Condition extractConditionBySystemTag(Condition rootCondition, String systemTag) {
        if (rootCondition == null || systemTag == null) {
            return null;
        }

        try {
            if (rootCondition.containsParameter(SUB_CONDITIONS_PARAM)) {
                List<Condition> subConditions = getSubConditions(rootCondition);
                if (subConditions.isEmpty()) {
                    return null;
                }

                List<Condition> matchingConditions = new ArrayList<>();
                for (Condition condition : subConditions) {
                    Condition c = extractConditionBySystemTag(condition, systemTag);
                    if (c != null) {
                        matchingConditions.add(c);
                    }
                }

                if (matchingConditions.isEmpty()) {
                    return null;
                } else if (matchingConditions.equals(subConditions)) {
                    return rootCondition;
                } else if (BOOLEAN_CONDITION_TYPE.equals(rootCondition.getConditionTypeId()) &&
                          AND_OPERATOR.equals(rootCondition.getParameter(OPERATOR_PARAM))) {
                    return createBooleanCondition(matchingConditions);
                }
                throw new IllegalArgumentException(String.format(
                    "Cannot extract condition with system tag: %s from condition: %s",
                    systemTag, rootCondition.getConditionTypeId()));
            }

            return isConditionMatchingSystemTag(rootCondition, systemTag) ? rootCondition : null;
        } catch (Exception e) {
            LOGGER.error("Error extracting condition by system tag: {} from condition: {}",
                systemTag, rootCondition.getConditionTypeId(), e);
            return null;
        }
    }

    private boolean isConditionMatchingSystemTag(Condition condition, String systemTag) {
        return condition.getConditionType() != null &&
               condition.getConditionType().getMetadata() != null &&
               condition.getConditionType().getMetadata().getSystemTags() != null &&
               condition.getConditionType().getMetadata().getSystemTags().contains(systemTag);
    }

    private Condition createBooleanCondition(List<Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        if (conditions.size() == 1) {
            return conditions.get(0);
        }
        Condition res = new Condition();
        res.setConditionType(getConditionType(BOOLEAN_CONDITION_TYPE));
        res.setParameter(OPERATOR_PARAM, AND_OPERATOR);
        res.setParameter(SUB_CONDITIONS_PARAM, new ArrayList<>(conditions)); // Defensive copy
        return res;
    }

    @Override
    public boolean resolveConditionType(Condition rootCondition) {
        return ParserHelper.resolveConditionType(this, rootCondition, (rootCondition != null ? "condition type " + rootCondition.getConditionTypeId() : "unknown"));
    }

    @Override
    public void refresh() {
        if (isShutdown) {
            LOGGER.warn("Attempt to refresh after shutdown was prevented");
            return;
        }

        Thread currentThread = Thread.currentThread();
        String originalName = currentThread.getName();
        try {
            currentThread.setName("ManualRefresh-" + System.currentTimeMillis());
            reloadTypes(true);
        } catch (Exception e) {
            LOGGER.error("Error refreshing definitions", e);
        } finally {
            currentThread.setName(originalName);
        }
    }

    @Override
    public ConditionBuilder getConditionBuilder() {
        return conditionBuilder;
    }

    public void setValueType(ValueType valueType) {
        if (valueType == null) {
            LOGGER.warn("Attempt to set null value type");
            return;
        }

        String valueTypeId = valueType.getId();
        if (valueTypeId == null) {
            LOGGER.warn("Value type has null ID");
            return;
        }

        Set<String> tags = valueType.getTags();
        // Create defensive copy of tags if present
        Set<String> tagsCopy = tags != null ? new HashSet<>(tags) : null;
        valueType.setTags(tagsCopy);

        try {
            String currentTenant = tenantService.getCurrentTenantId();
            if (currentTenant == null) {
                currentTenant = SYSTEM_TENANT;
            }
            Map<String, ValueType> cache = getValueTypeCache(currentTenant);
            cache.put(valueTypeId, valueType);

            // Update tag cache
            if (tagsCopy != null) {
                Map<String, Set<ValueType>> tagCache = getValueTypeByTagCache(currentTenant);
                for (String tag : tagsCopy) {
                    updateTagCache(tag, valueType, tagCache, false);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error setting value type: {}", valueTypeId, e);
        }
    }

    public void removeValueType(String valueTypeId) {
        if (valueTypeId == null) {
            return;
        }

        try {
            String currentTenant = tenantService.getCurrentTenantId();
            if (currentTenant == null) {
                currentTenant = SYSTEM_TENANT;
            }
            ValueType valueType = getValueTypeCache(currentTenant).remove(valueTypeId);
            if (valueType != null) {
                // Clean up tag cache
                Set<String> tags = valueType.getTags();
                if (tags != null) {
                    Map<String, Set<ValueType>> tagCache = getValueTypeByTagCache(currentTenant);
                    for (String tag : tags) {
                        updateTagCache(tag, valueType, tagCache, true);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error removing value type: {}", valueTypeId, e);
        }
    }

    public void setPropertyMergeStrategyType(PropertyMergeStrategyType propertyMergeStrategyType) {
        if (propertyMergeStrategyType == null) {
            LOGGER.warn("Attempt to set null property merge strategy type");
            return;
        }

        String id = propertyMergeStrategyType.getId();
        if (id == null) {
            LOGGER.warn("Property merge strategy type has null ID");
            return;
        }

        try {
            getPropertyMergeStrategyTypeCache(SYSTEM_TENANT).put(id, propertyMergeStrategyType);
            LOGGER.debug("Property merge strategy type {} set successfully", id);
        } catch (Exception e) {
            LOGGER.error("Error setting property merge strategy type: {}", id, e);
        }
    }

    public void removePropertyMergeStrategyType(String propertyMergeStrategyTypeId) {
        if (propertyMergeStrategyTypeId == null) {
            LOGGER.warn("Attempt to remove null property merge strategy type ID");
            return;
        }

        try {
            getPropertyMergeStrategyTypeCache(SYSTEM_TENANT).remove(propertyMergeStrategyTypeId);
            LOGGER.debug("Property merge strategy type {} removed successfully", propertyMergeStrategyTypeId);
        } catch (Exception e) {
            LOGGER.error("Error removing property merge strategy type: {}", propertyMergeStrategyTypeId, e);
        }
    }

    @Override
    public void onTenantRemoved(String tenantId) {
        if (tenantId == null || SYSTEM_TENANT.equals(tenantId)) {
            LOGGER.warn("Invalid tenant removal attempt: {}", tenantId);
            return;
        }

        try {
            synchronized(cacheLock) {
                // Remove from cache
                Map<String, Map<?, ?>> removedCaches = new HashMap<>();
                removedCaches.put("conditions", conditionTypeByTenantId.remove(tenantId));
                removedCaches.put("actions", actionTypeByTenantId.remove(tenantId));
                removedCaches.put("values", valueTypeByTenantId.remove(tenantId));
                removedCaches.put("valueTags", valueTypeByTagByTenantId.remove(tenantId));
                removedCaches.put("strategies", propertyMergeStrategyTypeByTenantId.remove(tenantId));

                if (LOGGER.isDebugEnabled()) {
                    removedCaches.forEach((type, cache) ->
                        LOGGER.debug("Removed {} cache for tenant {}: {} entries",
                            type, tenantId, cache != null ? ((Map<?,?>)cache).size() : 0));
                }

                // Create a basic property condition type
                ConditionType propertyConditionType = new ConditionType();
                propertyConditionType.setItemId("propertyCondition");
                Metadata metadata = new Metadata();
                metadata.setId("propertyCondition");
                propertyConditionType.setMetadata(metadata);
                propertyConditionType.setConditionEvaluator("propertyCondition");

                // Create tenant condition
                Condition tenantCondition = new Condition(propertyConditionType);
                tenantCondition.setParameter("propertyName", "tenantId");
                tenantCondition.setParameter("comparisonOperator", "equals");
                tenantCondition.setParameter("propertyValue", tenantId);

                // Remove tenant-specific items from persistence service
                persistenceService.removeByQuery(tenantCondition, ConditionType.class);
                persistenceService.removeByQuery(tenantCondition, ActionType.class);
            }
            LOGGER.info("Successfully removed all caches and persistent data for tenant: {}", tenantId);
        } catch (Exception e) {
            LOGGER.error("Error removing data for tenant: {}", tenantId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Condition> getSubConditions(Condition condition) {
        if (condition == null) {
            return Collections.emptyList();
        }

        Object subConditionsObj = condition.getParameter(SUB_CONDITIONS_PARAM);
        if (subConditionsObj == null) {
            return Collections.emptyList();
        }

        if (!(subConditionsObj instanceof List<?>)) {
            LOGGER.warn("Invalid sub-conditions type: expected List but got {}",
                subConditionsObj.getClass().getName());
            return Collections.emptyList();
        }

        List<?> subConditions = (List<?>) subConditionsObj;
        for (Object obj : subConditions) {
            if (!(obj instanceof Condition)) {
                LOGGER.warn("Invalid condition type in list: expected Condition but got {}",
                    obj != null ? obj.getClass().getName() : "null");
                return Collections.emptyList();
            }
        }

        return (List<Condition>) subConditions;
    }

    private void updateTagCache(String tag, ValueType valueType, Map<String, Set<ValueType>> tagCache, boolean isRemove) {
        if (tag == null || valueType == null || tagCache == null) {
            return;
        }

        Set<ValueType> valueTypes = tagCache.computeIfAbsent(tag,
            k -> Collections.synchronizedSet(new LinkedHashSet<>()));

        synchronized(valueTypes) {
            try {
                if (isRemove) {
                    valueTypes.remove(valueType);
                    if (valueTypes.isEmpty()) {
                        tagCache.remove(tag);
                    }
                } else {
                    valueTypes.add(valueType);
                }
            } catch (Exception e) {
                LOGGER.error("Error updating tag cache for tag: {} and value type: {}", tag, valueType.getId(), e);
            }
        }
    }

    private class TypeReloadTask implements Runnable {
        @Override
        public void run() {
            Thread currentThread = Thread.currentThread();
            String originalName = currentThread.getName();
            try {
                currentThread.setName("TypeReloadTask-" + System.currentTimeMillis());
                if (!isShutdown) {
                    java.util.concurrent.Future<?> timeoutFuture = createTimeoutFuture(currentThread);
                    try {
                        reloadTypes(false);
                    } finally {
                        timeoutFuture.cancel(false);
                    }
                }
            } catch (Exception e) {
                handleReloadError(e);
            } finally {
                currentThread.setName(originalName);
            }
        }

        private java.util.concurrent.Future<?> createTimeoutFuture(Thread taskThread) {
            return schedulerService.getScheduleExecutorService().schedule(() -> {
                LOGGER.warn("Type reload task timed out after {} ms", TASK_TIMEOUT_MS);
                taskThread.interrupt();
            }, TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        private void handleReloadError(Exception e) {
            if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                LOGGER.warn("Type reload task was interrupted");
                Thread.currentThread().interrupt();
            } else {
                LOGGER.error("Error in scheduled type reload task", e);
            }
        }
    }

}
