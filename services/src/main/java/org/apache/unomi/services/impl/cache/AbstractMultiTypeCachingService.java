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

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.services.impl.AbstractContextAwareService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
            loadPredefinedItemsForType(bundleContext, config);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends Serializable> void loadPredefinedItemsForType(BundleContext bundleContext, CacheableTypeConfig<T> config) {
        Enumeration<URL> entries = bundleContext.getBundle()
            .findEntries("META-INF/cxs/" + config.getMetaInfPath(), "*.json", true);
        if (entries == null) return;

        while (entries.hasMoreElements()) {
            URL entryURL = entries.nextElement();
            logger.debug("Found predefined {} at {}, loading... ",
                config.getType().getSimpleName(), entryURL);

            try {
                T item = CustomObjectMapper.getObjectMapper().readValue(entryURL, config.getType());
                String id = config.getIdExtractor().apply(item);
                cacheService.put(config.getItemType(), id, SYSTEM_TENANT, item);
                logger.info("Predefined {} registered: {}",
                    config.getType().getSimpleName(), id);
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
                    loadPredefinedItems(event.getBundle().getBundleContext());
                    break;
                case BundleEvent.STOPPING:
                    // Handle bundle stop if needed
                    break;
            }
            return null;
        });
    }
}
