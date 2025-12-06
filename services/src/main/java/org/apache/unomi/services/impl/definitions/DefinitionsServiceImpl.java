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
import org.apache.unomi.api.PropertyMergeStrategyType;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.api.services.ConditionValidationService.ValidationError;
import org.apache.unomi.api.services.ConditionValidationService.ValidationErrorType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.TenantLifecycleListener;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.utils.ConditionBuilder;
import org.apache.unomi.services.common.cache.AbstractMultiTypeCachingService;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

public class DefinitionsServiceImpl extends AbstractMultiTypeCachingService implements DefinitionsService, TenantLifecycleListener, SynchronousBundleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefinitionsServiceImpl.class.getName());

    private volatile boolean isShutdown = false;
    private volatile boolean initialRefreshComplete = false;

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

        // After all bundles are loaded and initial data is loaded, ensure all parent conditions are resolved
        // This is needed because the post-refresh callback only runs if there are changes, but we need
        // to resolve parent conditions even if items were already in cache from bundle loading
        contextManager.executeAsSystem(() -> {
            try {
                resolveAllParentConditions();
            } catch (Exception e) {
                LOGGER.error("Error resolving parent conditions during initialization", e);
            }
            return null;
        });

        LOGGER.info("Definitions service initialized.");
    }

    /**
     * Resolves all parent conditions for all condition types in the cache.
     * This ensures parent conditions are resolved regardless of load order.
     */
    private void resolveAllParentConditions() {
        for (String tenantId : getTenants()) {
            Map<String, ConditionType> cachedTypes = cacheService.getTenantCache(tenantId, ConditionType.class);
            if (cachedTypes != null) {
                for (ConditionType conditionType : cachedTypes.values()) {
                    if (conditionType != null && conditionType.getParentCondition() != null) {
                        // Only resolve if not already resolved
                        if (conditionType.getParentCondition().getConditionType() == null) {
                            boolean resolved = ParserHelper.resolveConditionType(this, conditionType.getParentCondition(),
                                "condition type " + conditionType.getItemId());
                            if (resolved) {
                                // Update the cached item with resolved parent condition
                                cacheService.put(ConditionType.ITEM_TYPE, conditionType.getItemId(),
                                    tenantId, conditionType);
                            } else {
                                LOGGER.debug("Parent condition for {} not yet available, will be resolved on next refresh",
                                    conditionType.getItemId());
                            }
                        }
                    }
                }
            }
        }
    }

    public void setDefinitionsRefreshInterval(long definitionsRefreshInterval) {
        this.definitionsRefreshInterval = definitionsRefreshInterval;
    }

    protected void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null || isShutdown) {
            return;
        }

        // Call the base class implementation which will use our bundle processors
        super.processBundleStartup(bundleContext);
    }

    protected void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }

        // Call the base class implementation which will handle removing items
        super.processBundleStop(bundleContext.getBundle());
    }

    @Override
    protected void onBundleStop(Bundle bundle) {
        if (bundle == null) {
            return;
        }
        final long bundleId = bundle.getBundleId();

        // Remove all plugin types contributed by this bundle (system tenant / inherited)
        // Execute as system to target predefined items
        contextManager.executeAsSystem(() -> {
            try {
                java.util.List<PluginType> types = getTypesByPlugin().get(bundleId);
                if (types != null) {
                    for (PluginType type : types) {
                        if (type instanceof ConditionType) {
                            removeConditionType(((ConditionType) type).getItemId());
                        } else if (type instanceof ActionType) {
                            removeActionType(((ActionType) type).getItemId());
                        } else if (type instanceof ValueType) {
                            removeValueType(((ValueType) type).getId());
                        } else if (type instanceof PropertyMergeStrategyType) {
                            removePropertyMergeStrategyType(((PropertyMergeStrategyType) type).getId());
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Error cleaning up plugin types for bundle {} on stop", bundleId, e);
            }
            return null;
        });
    }

    @Override
    public void preDestroy() {
        super.preDestroy();
        isShutdown = true;
        if (bundleContext != null) {
            bundleContext.removeBundleListener(this);
        }
        LOGGER.info("Definitions service shutdown.");
    }

    @Override
    public Collection<ConditionType> getAllConditionTypes() {
        return getAllItems(ConditionType.class, true);
    }

    @Override
    public Set<ConditionType> getConditionTypesByTag(String tag) {
        return getItemsByTag(ConditionType.class, tag);
    }

    @Override
    public Set<ConditionType> getConditionTypesBySystemTag(String tag) {
        return getItemsBySystemTag(ConditionType.class, tag);
    }

    @Override
    public ConditionType getConditionType(String id) {
        ConditionType conditionType = getItem(id, ConditionType.class);
        if (conditionType != null && conditionType.getParentCondition() != null 
                && conditionType.getParentCondition().getConditionType() == null) {
            // Resolve parent condition on demand if not already resolved
            boolean resolved = ParserHelper.resolveConditionType(this, conditionType.getParentCondition(),
                "condition type " + conditionType.getItemId());
            if (resolved) {
                // Update the cached item with resolved parent condition
                String tenantId = contextManager.getCurrentContext().getTenantId();
                cacheService.put(ConditionType.ITEM_TYPE, conditionType.getItemId(), tenantId, conditionType);
            }
        }
        return conditionType;
    }

    @Override
    public void setConditionType(ConditionType conditionType) {
        saveItem(conditionType, ConditionType::getItemId, ConditionType.ITEM_TYPE);
    }

    @Override
    public void removeConditionType(String id) {
        removeItem(id, ConditionType.class, ConditionType.ITEM_TYPE);
    }

    @Override
    public Collection<ActionType> getAllActionTypes() {
        return getAllItems(ActionType.class, true);
    }

    @Override
    public Set<ActionType> getActionTypeByTag(String tag) {
        return getItemsByTag(ActionType.class, tag);
    }

    @Override
    public Set<ActionType> getActionTypeBySystemTag(String tag) {
        return getItemsBySystemTag(ActionType.class, tag);
    }

    @Override
    public ActionType getActionType(String id) {
        return getItem(id, ActionType.class);
    }

    @Override
    public void setActionType(ActionType actionType) {
        saveItem(actionType, ActionType::getItemId, ActionType.ITEM_TYPE);
    }

    @Override
    public void removeActionType(String id) {
        removeItem(id, ActionType.class, ActionType.ITEM_TYPE);
    }

    @Override
    public Collection<ValueType> getAllValueTypes() {
        return getAllItems(ValueType.class, true);
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
        return getItem(id, ValueType.class);
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
        return getItem(id, PropertyMergeStrategyType.class);
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
        return getAllItems(PropertyMergeStrategyType.class, true);
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
                    warningMessage.append("\n- ").append(warning.getDetailedMessage());
                }
                LOGGER.warn(warningMessage.toString());
            }

            // Only throw exception for actual errors
            if (!errors.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder("Invalid condition:");
                for (ValidationError error : errors) {
                    errorMessage.append("\n- ").append(error.getDetailedMessage());
                }
                throw new IllegalArgumentException(errorMessage.toString());
            }
        }
        return resolved;
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
                    propertyConditionType.setQueryBuilder("propertyConditionQueryBuilder");

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

    /**
     * Creates a base builder with common configuration settings
     * @param type the class of items to cache
     * @param itemType the type identifier
     * @param metaInfPath the path in META-INF/cxs for predefined items
     * @return a builder with common settings applied
     * @param <T> the type of items to cache
     */
    private <T extends Serializable> CacheableTypeConfig.Builder<T> createBaseBuilder(
            Class<T> type,
            String itemType,
            String metaInfPath) {
        return CacheableTypeConfig.<T>builder(type, itemType, metaInfPath)
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(definitionsRefreshInterval)
            .withPredefinedItems(true);
    }

    @Override
    protected Set<CacheableTypeConfig<?>> getTypeConfigs() {
        Set<CacheableTypeConfig<?>> configs = new HashSet<>();

        // Action Type configuration with bundle processor
        BiConsumer<BundleContext, ActionType> actionTypeProcessor = (bundleContext, type) -> {
            type.setPluginId(bundleContext.getBundle().getBundleId());
            type.setTenantId(SYSTEM_TENANT);
            setActionType(type);
        };

        configs.add(createBaseBuilder(ActionType.class, ActionType.ITEM_TYPE, "actions")
            .withIdExtractor(ActionType::getItemId)
            .withBundleItemProcessor(actionTypeProcessor)
            .build());

        // Value Type configuration with bundle processor
        BiConsumer<BundleContext, ValueType> valueTypeProcessor = (bundleContext, type) -> {
            type.setPluginId(bundleContext.getBundle().getBundleId());
            setValueType(type);
        };

        configs.add(createBaseBuilder(ValueType.class, ValueType.class.getSimpleName(), "values")
            .withIdExtractor(ValueType::getId)
            .withBundleItemProcessor(valueTypeProcessor)
            .build());

        // PropertyMergeStrategyType configuration with bundle processor
        BiConsumer<BundleContext, PropertyMergeStrategyType> mergeStrategyProcessor = (bundleContext, type) -> {
            type.setPluginId(bundleContext.getBundle().getBundleId());
            cacheService.put(PropertyMergeStrategyType.class.getSimpleName(), type.getId(), SYSTEM_TENANT, type);
        };

        configs.add(createBaseBuilder(
                PropertyMergeStrategyType.class,
                PropertyMergeStrategyType.class.getSimpleName(),
                "mergers")
            .withIdExtractor(PropertyMergeStrategyType::getId)
            .withBundleItemProcessor(mergeStrategyProcessor)
            .build());

        // Condition Type configuration with bundle processor
        // Resolve parent conditions when loading from bundles, but don't fail if parent isn't available yet
        BiConsumer<BundleContext, ConditionType> conditionTypeProcessor = (bundleContext, type) -> {
            type.setPluginId(bundleContext.getBundle().getBundleId());
            type.setTenantId(SYSTEM_TENANT);
            
            // Try to resolve parent condition if present, but don't fail if parent isn't loaded yet
            // The post-refresh callback will resolve it later once all items are loaded
            if (type.getParentCondition() != null && type.getParentCondition().getConditionType() == null) {
                boolean resolved = ParserHelper.resolveConditionType(this, type.getParentCondition(),
                    "condition type " + type.getItemId());
                if (!resolved) {
                    LOGGER.debug("Parent condition for {} not yet available during bundle load, will resolve after refresh",
                        type.getItemId());
                }
            }
            
            setConditionType(type);
        };

        // Post-refresh callback: Resolve all parent conditions after all items are loaded
        // This ensures parent conditions are resolved during refresh cycles
        // We always resolve parent conditions, even if no changes were detected, to handle
        // cases where items were loaded from bundles before the refresh
        BiConsumer<Map<String, Map<String, ConditionType>>, Map<String, Map<String, ConditionType>>> postRefreshCallback =
            (oldState, newState) -> {
                resolveAllParentConditions();
                if (!initialRefreshComplete) {
                    initialRefreshComplete = true;
                    LOGGER.debug("Initial condition type refresh completed, all parent conditions resolved");
                }
            };

        configs.add(createBaseBuilder(ConditionType.class, ConditionType.ITEM_TYPE, "conditions")
            .withIdExtractor(ConditionType::getItemId)
            .withBundleItemProcessor(conditionTypeProcessor)
            .withPostRefreshCallback(postRefreshCallback)
            .build());

        return configs;
    }

    @Override
    public void refresh() {
        for (CacheableTypeConfig<?> config : getTypeConfigs()) {
            refreshTypeCache(config);
        }
        // After refresh, ensure all parent conditions are resolved
        // This handles cases where the post-refresh callback didn't run due to no changes detected
        if (!initialRefreshComplete) {
            contextManager.executeAsSystem(() -> {
                resolveAllParentConditions();
                initialRefreshComplete = true;
                return null;
            });
        }
    }

    @Override
    public ConditionBuilder getConditionBuilder() {
        return conditionBuilder;
    }
}
