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
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.api.services.ConditionValidationService.ValidationError;
import org.apache.unomi.api.services.ConditionValidationService.ValidationErrorType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.TenantLifecycleListener;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.api.tasks.TaskExecutor.TaskStatusCallback;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.utils.ConditionBuilder;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.services.impl.cache.AbstractMultiTypeCachingService;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
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
import java.util.stream.Collectors;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

public class DefinitionsServiceImpl extends AbstractMultiTypeCachingService implements DefinitionsService, TenantLifecycleListener, SynchronousBundleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefinitionsServiceImpl.class.getName());

    private volatile boolean isShutdown = false;
    private volatile ScheduledTask task;

    private final Map<Long, List<PluginType>> pluginTypes = new ConcurrentHashMap<>();

    private long definitionsRefreshInterval = 10000;

    private ConditionBuilder conditionBuilder;

    private static final int MAX_RECURSIVE_CONDITIONS = 1000; // Prevent stack overflow
    private static final String BOOLEAN_CONDITION_TYPE = "booleanCondition";
    private static final String AND_OPERATOR = "and";
    private static final String SUB_CONDITIONS_PARAM = "subConditions";
    private static final String OPERATOR_PARAM = "operator";

    private static final long TASK_TIMEOUT_MS = 60000; // 1 minute timeout for tasks

    private ConditionValidationService conditionValidationService;
    private TracerService tracerService;

    public void setCacheService(MultiTypeCacheService cacheService) {
        super.setCacheService(cacheService);
    }

    public void setConditionValidationService(ConditionValidationService conditionValidationService) {
        this.conditionValidationService = conditionValidationService;
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    public DefinitionsServiceImpl() {
        // Initialize other components
        conditionBuilder = new ConditionBuilder(this);
    }

    @Override
    public void postConstruct() {
        super.postConstruct();

        // Process bundle startup
        if (bundleContext != null) {
            LOGGER.debug("postConstruct {{}}", bundleContext.getBundle());
            processBundleStartup(bundleContext);

            // Process already started bundles
            for (Bundle bundle : bundleContext.getBundles()) {
                if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                    processBundleStartup(bundle.getBundleContext());
                }
            }

            bundleContext.addBundleListener(this);
        }

        scheduleTypeReloads();
        LOGGER.info("Definitions service initialized.");
    }

    public void setDefinitionsRefreshInterval(long definitionsRefreshInterval) {
        this.definitionsRefreshInterval = definitionsRefreshInterval;
    }

    private void scheduleTypeReloads() {
        if (isShutdown || schedulerService == null) {
            return;
        }

        try {
            // Create a recurring task for type reloading
            task = schedulerService.newTask("type-reload")
                .withInitialDelay(10000, TimeUnit.MILLISECONDS)
                .withPeriod(definitionsRefreshInterval, TimeUnit.MILLISECONDS)
                .withFixedRate()
                .withExecutor(new TypeReloadTaskExecutor(this, TASK_TIMEOUT_MS))
                .nonPersistent() // In-memory task since it's node-specific
                .schedule();
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
        if (persistenceService == null || contextManager == null) {
            LOGGER.warn("Cannot load condition types - required services not available");
            return;
        }

        try {
            contextManager.executeAsSystem(() -> {
                try {
                    Collection<ConditionType> types = persistenceService.getAllItems(ConditionType.class);
                    if (types != null) {
                        for (ConditionType conditionType : types) {
                            if (conditionType != null && conditionType.getItemId() != null) {
                                if (conditionType.getParentCondition() != null) {
                                    ParserHelper.resolveConditionType(this, conditionType.getParentCondition(), "condition type " + conditionType.getItemId());
                                }
                                cacheService.put(ConditionType.ITEM_TYPE, conditionType.getItemId(), SYSTEM_TENANT, conditionType);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error loading condition types from persistence service", e);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error executing in system context while loading condition types", e);
        }
    }

    private void loadActionTypesFromPersistence() {
        if (persistenceService == null || contextManager == null) {
            LOGGER.warn("Cannot load action types - required services not available");
            return;
        }

        try {
            contextManager.executeAsSystem(() -> {
                try {
                    Collection<ActionType> types = persistenceService.getAllItems(ActionType.class);
                    if (types != null) {
                        for (ActionType actionType : types) {
                            if (actionType != null && actionType.getItemId() != null) {
                                cacheService.put(ActionType.ITEM_TYPE, actionType.getItemId(), SYSTEM_TENANT, actionType);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error loading action types from persistence service", e);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error executing in system context while loading action types", e);
        }
    }

    protected void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null || isShutdown) {
            return;
        }

        Bundle bundle = bundleContext.getBundle();
        if (bundle == null) {
            LOGGER.warn("No bundle found in context during startup");
            return;
        }

        final Long bundleId = bundle.getBundleId();
        try {
            contextManager.executeAsSystem(() -> {
                try {
                    loadPredefinedConditionTypes(bundleContext);
                    loadPredefinedActionTypes(bundleContext);
                    loadPredefinedValueTypes(bundleContext);
                    loadPredefinedPropertyMergeStrategyTypes(bundleContext);
                } catch (Exception e) {
                    LOGGER.error("Error loading predefined types for bundle: {}", bundleId, e);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error during bundle startup processing for bundle: {}", bundleId, e);
            // Cleanup on failure
            pluginTypes.remove(bundleId);
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
            contextManager.executeAsSystem(() -> {
                // Process each type based on its actual class
                for (PluginType type : types) {
                    if (type instanceof ConditionType) {
                        ConditionType conditionType = (ConditionType) type;
                        cacheService.remove(ConditionType.ITEM_TYPE, conditionType.getItemId(), SYSTEM_TENANT, ConditionType.class);
                    } else if (type instanceof ActionType) {
                        ActionType actionType = (ActionType) type;
                        cacheService.remove(ActionType.ITEM_TYPE, actionType.getItemId(), SYSTEM_TENANT, ActionType.class);
                    } else if (type instanceof ValueType) {
                        ValueType valueType = (ValueType) type;
                        cacheService.remove(ValueType.class.getSimpleName(), valueType.getId(), SYSTEM_TENANT, ValueType.class);
                    } else if (type instanceof PropertyMergeStrategyType) {
                        PropertyMergeStrategyType strategyType = (PropertyMergeStrategyType) type;
                        cacheService.remove(PropertyMergeStrategyType.class.getSimpleName(), strategyType.getId(), SYSTEM_TENANT, PropertyMergeStrategyType.class);
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error during bundle stop processing for bundle ID: {}", bundleId, e);
        }
    }

    @Override
    public void preDestroy() {
        super.preDestroy();
        isShutdown = true;
        if (task != null) {
            schedulerService.cancelTask(task.getItemId());
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
                        // Ensure type processing happens in system context
                        contextManager.executeAsSystem(() -> {
                            try {
                                typeProcessor.accept(type);
                                LOGGER.info("Predefined {} registered", typeClass.getSimpleName());
                            } catch (Exception e) {
                                LOGGER.error("Error processing {} definition {}", typeClass.getSimpleName(), entryURL, e);
                            }
                        });
                    } catch (Exception e) {
                        LOGGER.error("Error in system context execution for {} definition {}", typeClass.getSimpleName(), entryURL, e);
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
                type.setTenantId(SYSTEM_TENANT);
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
                type.setTenantId(SYSTEM_TENANT);
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
                cacheService.put(PropertyMergeStrategyType.class.getSimpleName(), type.getId(), SYSTEM_TENANT, type);
            }
        });
    }

    @Override
    public Map<Long, List<PluginType>> getTypesByPlugin() {
        return pluginTypes;
    }

    @Override
    public Collection<ConditionType> getAllConditionTypes() {
        return new ArrayList<>(cacheService.getTenantCache(contextManager.getCurrentContext().getTenantId(), ConditionType.class).values());
    }

    @Override
    public Set<ConditionType> getConditionTypesByTag(String tag) {
        return cacheService.getValuesByPredicateWithInheritance(
            contextManager.getCurrentContext().getTenantId(),
            ConditionType.class,
            conditionType -> conditionType.getMetadata() != null && conditionType.getMetadata().getTags().contains(tag)
        );
    }

    @Override
    public Set<ConditionType> getConditionTypesBySystemTag(String tag) {
        return cacheService.getValuesByPredicateWithInheritance(
            contextManager.getCurrentContext().getTenantId(),
            ConditionType.class,
            conditionType -> conditionType.getMetadata() != null && conditionType.getMetadata().getSystemTags().contains(tag)
        );
    }

    @Override
    public ConditionType getConditionType(String id) {
        return cacheService.getWithInheritance(id, contextManager.getCurrentContext().getTenantId(), ConditionType.class);
    }

    @Override
    public void setConditionType(ConditionType conditionType) {
        if (conditionType.getMetadata().getId() == null) {
            return;
        }

        String currentTenant = contextManager.getCurrentContext().getTenantId();
        persistenceService.save(conditionType);
        cacheService.put(ConditionType.ITEM_TYPE, conditionType.getItemId(), currentTenant, conditionType);
    }

    @Override
    public Collection<ActionType> getAllActionTypes() {
        return new ArrayList<>(cacheService.getTenantCache(contextManager.getCurrentContext().getTenantId(), ActionType.class).values());
    }

    @Override
    public Set<ActionType> getActionTypeByTag(String tag) {
        return cacheService.getValuesByPredicateWithInheritance(
            contextManager.getCurrentContext().getTenantId(),
            ActionType.class,
            actionType -> actionType.getMetadata() != null && actionType.getMetadata().getTags().contains(tag)
        );
    }

    @Override
    public Set<ActionType> getActionTypeBySystemTag(String tag) {
        return cacheService.getValuesByPredicateWithInheritance(
            contextManager.getCurrentContext().getTenantId(),
            ActionType.class,
            actionType -> actionType.getMetadata() != null && actionType.getMetadata().getSystemTags().contains(tag)
        );
    }

    @Override
    public ActionType getActionType(String id) {
        return cacheService.getWithInheritance(id, contextManager.getCurrentContext().getTenantId(), ActionType.class);
    }

    @Override
    public void setActionType(ActionType actionType) {
        if (actionType.getMetadata().getId() == null) {
            return;
        }

        String currentTenant = contextManager.getCurrentContext().getTenantId();
        persistenceService.save(actionType);
        cacheService.put(ActionType.ITEM_TYPE, actionType.getItemId(), currentTenant, actionType);
    }

    @Override
    public void removeActionType(String id) {
        cacheService.remove(ActionType.ITEM_TYPE, id, contextManager.getCurrentContext().getTenantId(), ActionType.class);
        persistenceService.remove(id, ActionType.class);
    }

    @Override
    public Collection<ValueType> getAllValueTypes() {
        return new ArrayList<>(cacheService.getTenantCache(contextManager.getCurrentContext().getTenantId(), ValueType.class).values());
    }

    @Override
    public Set<ValueType> getValueTypeByTag(String tag) {
        return cacheService.getValuesByPredicateWithInheritance(
            contextManager.getCurrentContext().getTenantId(),
            ValueType.class,
            valueType -> valueType.getTags() != null && valueType.getTags().contains(tag)
        );
    }

    @Override
    public ValueType getValueType(String id) {
        return cacheService.getWithInheritance(id, contextManager.getCurrentContext().getTenantId(), ValueType.class);
    }

    @Override
    public void setValueType(ValueType valueType) {
        if (valueType.getId() == null) {
            return;
        }
        cacheService.put(ValueType.class.getSimpleName(), valueType.getId(), contextManager.getCurrentContext().getTenantId(), valueType);
    }

    @Override
    public void removeValueType(String id) {
        if (id == null) {
            return;
        }
        ValueType valueType = getValueType(id);
        if (valueType != null) {
            cacheService.remove(ValueType.class.getSimpleName(), id, contextManager.getCurrentContext().getTenantId(), ValueType.class);
        }
    }

    @Override
    public PropertyMergeStrategyType getPropertyMergeStrategyType(String id) {
        return cacheService.getWithInheritance(id, contextManager.getCurrentContext().getTenantId(), PropertyMergeStrategyType.class);
    }

    @Override
    public void setPropertyMergeStrategyType(PropertyMergeStrategyType propertyMergeStrategyType) {
        if (propertyMergeStrategyType.getId() == null) {
            return;
        }

        cacheService.put(PropertyMergeStrategyType.class.getSimpleName(), propertyMergeStrategyType.getId(), contextManager.getCurrentContext().getTenantId(), propertyMergeStrategyType);
    }

    @Override
    public void removePropertyMergeStrategyType(String id) {
        if (id == null) {
            return;
        }
        PropertyMergeStrategyType strategyType = getPropertyMergeStrategyType(id);
        if (strategyType != null) {
            cacheService.remove(PropertyMergeStrategyType.class.getSimpleName(), id, contextManager.getCurrentContext().getTenantId(), PropertyMergeStrategyType.class);
        }
    }

    @Override
    public Collection<PropertyMergeStrategyType> getAllPropertyMergeStrategyTypes() {
        return new ArrayList<>(cacheService.getTenantCache(contextManager.getCurrentContext().getTenantId(), PropertyMergeStrategyType.class).values());
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

        contextManager.executeAsSystem(() -> {
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
        });
    }

    @Override
    public List<Condition> extractConditionsByType(Condition rootCondition, String typeId) {
        if (rootCondition == null || typeId == null) {
            return Collections.emptyList();
        }

        List<Condition> result = new ArrayList<>();
        extractConditionsRecursively(rootCondition, typeId, result, 0);
        return result;
    }

    private void extractConditionsRecursively(Condition condition, String typeId, List<Condition> result, int depth) {
        if (condition == null || depth > MAX_RECURSIVE_CONDITIONS) {
            return;
        }

        // Check if current condition matches the type
        if (typeId.equals(condition.getConditionTypeId())) {
            result.add(condition);
        }

        // Process sub-conditions if they exist
        List<Condition> subConditions = getSubConditions(condition);
        if (subConditions != null) {
            for (Condition subCondition : subConditions) {
                extractConditionsRecursively(subCondition, typeId, result, depth + 1);
            }
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
            return conditions.get(0);  // Return single condition directly
        }
        Condition res = new Condition();
        res.setConditionType(getConditionType(BOOLEAN_CONDITION_TYPE));
        res.setParameter(OPERATOR_PARAM, AND_OPERATOR);
        res.setParameter(SUB_CONDITIONS_PARAM, new ArrayList<>(conditions));
        return res;
    }

    @Override
    public boolean resolveConditionType(Condition rootCondition) {
        boolean resolved = ParserHelper.resolveConditionType(this, rootCondition, (rootCondition != null ? "condition type " + rootCondition.getConditionTypeId() : "unknown"));
        if (resolved) {
            // Start validation operation in tracer
            if (tracerService != null) {
                RequestTracer tracer = tracerService.getCurrentTracer();
                if (tracer != null && tracer.isEnabled()) {
                    tracer.startOperation("condition-validation", "Validating condition: " + rootCondition.getConditionTypeId(), rootCondition);
                }
            }

            // Validate the condition after resolving its type
            List<ValidationError> validationErrors = conditionValidationService.validate(rootCondition);

            // Add validation info to tracer
            if (tracerService != null) {
                RequestTracer tracer = tracerService.getCurrentTracer();
                if (tracer != null && tracer.isEnabled()) {
                    tracer.addValidationInfo(validationErrors, "condition-validation");
                    tracer.endOperation(!validationErrors.isEmpty(), String.format("Validation completed with %d errors", validationErrors.size()));
                }
            }

            // Separate errors and warnings
            List<ValidationError> errors = validationErrors.stream()
                .filter(error -> error.getType() != ValidationErrorType.MISSING_RECOMMENDED_PARAMETER)
                .collect(Collectors.toList());

            List<ValidationError> warnings = validationErrors.stream()
                .filter(error -> error.getType() == ValidationErrorType.MISSING_RECOMMENDED_PARAMETER)
                .collect(Collectors.toList());

            // Log warnings but don't block the operation
            if (!warnings.isEmpty()) {
                StringBuilder warningMessage = new StringBuilder("Condition has warnings:");
                for (ValidationError warning : warnings) {
                    warningMessage.append("\n- ").append(warning.getMessage());
                }
                LOGGER.warn(warningMessage.toString());
            }

            // Only throw exception for actual errors
            if (!errors.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder("Invalid condition:");
                for (ValidationError error : errors) {
                    errorMessage.append("\n- ").append(error.getMessage());
                }
                throw new IllegalArgumentException(errorMessage.toString());
            }
        }
        return resolved;
    }

    @Override
    public void removeConditionType(String id) {
        cacheService.remove(ConditionType.ITEM_TYPE, id, contextManager.getCurrentContext().getTenantId(), ConditionType.class);
        persistenceService.remove(id, ConditionType.class);
    }

    @Override
    public void onTenantRemoved(String tenantId) {
        if (tenantId == null || SYSTEM_TENANT.equals(tenantId)) {
            LOGGER.warn("Invalid tenant removal attempt: {}", tenantId);
            return;
        }

        try {
            contextManager.executeAsSystem(() -> {
                try {
                    // Clear all caches for this tenant
                    cacheService.clear(tenantId);

                    // Create a basic property condition type for persistence cleanup
                    ConditionType propertyConditionType = new ConditionType();
                    propertyConditionType.setItemId("propertyCondition");
                    Metadata metadata = new Metadata();
                    metadata.setId("propertyCondition");
                    propertyConditionType.setMetadata(metadata);
                    propertyConditionType.setConditionEvaluator("propertyConditionEvaluator");
                    propertyConditionType.setQueryBuilder("propertyConditionESQueryBuilder");

                    // Create tenant condition
                    Condition tenantCondition = new Condition(propertyConditionType);
                    tenantCondition.setParameter("propertyName", "tenantId");
                    tenantCondition.setParameter("comparisonOperator", "equals");
                    tenantCondition.setParameter("propertyValue", tenantId);

                    // Remove tenant-specific items from persistence service
                    persistenceService.removeByQuery(tenantCondition, ConditionType.class);
                    persistenceService.removeByQuery(tenantCondition, ActionType.class);

                    LOGGER.info("Successfully removed all caches and persistent data for tenant: {}", tenantId);
                } catch (Exception e) {
                    LOGGER.error("Error removing data for tenant: {}", tenantId, e);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error executing in system context while removing tenant: {}", tenantId, e);
        }
    }

    @Override
    protected Set<CacheableTypeConfig<?>> getTypeConfigs() {
        Set<CacheableTypeConfig<?>> configs = new HashSet<>();
        CacheableTypeConfig<ActionType> actionTypeConfig = new CacheableTypeConfig<>(
            ActionType.class,
            ActionType.ITEM_TYPE,
            "actions",
            true,
            true,
            definitionsRefreshInterval,
            item -> item.getItemId()
        );
        configs.add(actionTypeConfig);

        CacheableTypeConfig<ValueType> valueTypeConfig = new CacheableTypeConfig<>(
            ValueType.class,
            ValueType.class.getSimpleName(),
            "values",
            true,
            true,
            definitionsRefreshInterval,
            item -> item.getId()
        );
        configs.add(valueTypeConfig);

        CacheableTypeConfig<PropertyMergeStrategyType> propertyMergeStrategyTypeConfig = new CacheableTypeConfig<>(
            PropertyMergeStrategyType.class,
            PropertyMergeStrategyType.class.getSimpleName(),
            "mergers",
            true,
            true,
            definitionsRefreshInterval,
            item -> item.getId()
        );
        configs.add(propertyMergeStrategyTypeConfig);

        CacheableTypeConfig<ConditionType> conditionTypeConfig = new CacheableTypeConfig<>(
            ConditionType.class,
            ConditionType.ITEM_TYPE,
            "conditions",
            true,
            true,
            definitionsRefreshInterval,
            item -> item.getItemId()
        );
        configs.add(conditionTypeConfig);

        return configs;
    }

    @Override
    public void refresh() {
        for (CacheableTypeConfig<?> config : getTypeConfigs()) {
            refreshTypeCache(config);
        }
    }

    @Override
    public ConditionBuilder getConditionBuilder() {
        return conditionBuilder;
    }

    private static class TypeReloadTaskExecutor implements TaskExecutor {
        private final DefinitionsServiceImpl definitionsService;
        private final long taskTimeoutMs;

        public TypeReloadTaskExecutor(DefinitionsServiceImpl definitionsService, long taskTimeoutMs) {
            this.definitionsService = definitionsService;
            this.taskTimeoutMs = taskTimeoutMs;
        }

        @Override
        public String getTaskType() {
            return "type-reload";
        }

        @Override
        public void execute(ScheduledTask task, TaskStatusCallback statusCallback) throws Exception {
            Thread currentThread = Thread.currentThread();
            String originalName = currentThread.getName();
            try {
                currentThread.setName("TypeReloadTask-" + System.currentTimeMillis());
                ScheduledTask timeoutTask = null;
                if (!definitionsService.isShutdown) {
                    timeoutTask = definitionsService.schedulerService.newTask("type-reload-timeout")
                        .withInitialDelay(taskTimeoutMs, TimeUnit.MILLISECONDS)
                        .asOneShot()
                        .withSimpleExecutor(() -> {
                            LOGGER.warn("Type reload task timed out after {} ms", taskTimeoutMs);
                            currentThread.interrupt();
                        })
                        .nonPersistent()
                        .schedule();
                    try {
                        definitionsService.contextManager.executeAsSystem(() -> definitionsService.reloadTypes(false));
                        statusCallback.complete();
                    } finally {
                        if (timeoutTask != null) {
                            definitionsService.schedulerService.cancelTask(timeoutTask.getItemId());
                        }
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    LOGGER.warn("Type reload task was interrupted");
                    Thread.currentThread().interrupt();
                    statusCallback.fail("Task was interrupted");
                } else {
                    LOGGER.error("Error in scheduled type reload task", e);
                    statusCallback.fail(e.getMessage());
                }
                throw e;
            } finally {
                currentThread.setName(originalName);
            }
        }
    }
}
