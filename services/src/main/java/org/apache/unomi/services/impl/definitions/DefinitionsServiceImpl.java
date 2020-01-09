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

import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.PropertyMergeStrategyType;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.services.impl.ParserHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefinitionsServiceImpl implements DefinitionsService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(DefinitionsServiceImpl.class.getName());

    private PersistenceService persistenceService;
    private SchedulerService schedulerService;

    private Map<String, ConditionType> conditionTypeById = new ConcurrentHashMap<>();
    private Map<String, ActionType> actionTypeById = new ConcurrentHashMap<>();
    private Map<String, ValueType> valueTypeById = new HashMap<>();
    private Map<String, Set<ValueType>> valueTypeByTag = new HashMap<>();
    private Map<Long, List<PluginType>> pluginTypes = new HashMap<>();
    private Map<String, PropertyMergeStrategyType> propertyMergeStrategyTypeById = new HashMap<>();

    private long definitionsRefreshInterval = 10000;

    private BundleContext bundleContext;
    public DefinitionsServiceImpl() {

    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setDefinitionsRefreshInterval(long definitionsRefreshInterval) {
        this.definitionsRefreshInterval = definitionsRefreshInterval;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        processBundleStartup(bundleContext);

        // process already started bundles
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                processBundleStartup(bundle.getBundleContext());
            }
        }

        bundleContext.addBundleListener(this);
        scheduleTypeReloads();
        logger.info("Definitions service initialized.");
    }

    private void scheduleTypeReloads() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                reloadTypes(false);
            }
        };
        schedulerService.getScheduleExecutorService().scheduleAtFixedRate(task, 10000, definitionsRefreshInterval, TimeUnit.MILLISECONDS);
        logger.info("Scheduled task for condition type loading each 10s");
    }

    public void reloadTypes(boolean refresh) {
        try {
            if (refresh) {
                persistenceService.refresh();
            }
            loadConditionTypesFromPersistence();
            loadActionTypesFromPersistence();
        } catch (Throwable t) {
            logger.error("Error loading definitions from persistence back-end", t);
        }
    }

    private void loadConditionTypesFromPersistence() {
        try {
            Map<String, ConditionType> newConditionTypesById = new ConcurrentHashMap<>();
            for (ConditionType conditionType : getAllConditionTypes()) {
                newConditionTypesById.put(conditionType.getItemId(), conditionType);
            }
            this.conditionTypeById = newConditionTypesById;
        } catch (Exception e) {
            logger.error("Error loading condition types from persistence service", e);
        }
    }

    private void loadActionTypesFromPersistence() {
        try {
            Map<String, ActionType> newActionTypesById = new ConcurrentHashMap<>();
            for (ActionType actionType : getAllActionTypes()) {
                newActionTypesById.put(actionType.getItemId(), actionType);
            }
            this.actionTypeById = newActionTypesById;
        } catch (Exception e) {
            logger.error("Error loading action types from persistence service", e);
        }
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }

        pluginTypes.put(bundleContext.getBundle().getBundleId(), new ArrayList<PluginType>());

        loadPredefinedConditionTypes(bundleContext);
        loadPredefinedActionTypes(bundleContext);
        loadPredefinedValueTypes(bundleContext);
        loadPredefinedPropertyMergeStrategies(bundleContext);

    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        List<PluginType> types = pluginTypes.get(bundleContext.getBundle().getBundleId());
        if (types != null) {
            for (PluginType type : types) {
                if (type instanceof ValueType) {
                    ValueType valueType = (ValueType) type;
                    valueTypeById.remove(valueType.getId());
                    for (String tag : valueType.getTags()) {
                        if (valueTypeByTag.containsKey(tag)) {
                            valueTypeByTag.get(tag).remove(valueType);
                        }
                    }
                }
            }
        }
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("Definitions service shutdown.");
    }

    private void loadPredefinedConditionTypes(BundleContext bundleContext) {
        Enumeration<URL> predefinedConditionEntries = bundleContext.getBundle().findEntries("META-INF/cxs/conditions", "*.json", true);
        if (predefinedConditionEntries == null) {
            return;
        }

        while (predefinedConditionEntries.hasMoreElements()) {
            URL predefinedConditionURL = predefinedConditionEntries.nextElement();
            logger.debug("Found predefined condition at " + predefinedConditionURL + ", loading... ");

            try {
                ConditionType conditionType = CustomObjectMapper.getObjectMapper().readValue(predefinedConditionURL, ConditionType.class);
                // Register only if condition type does not exist yet
                if (getConditionType(conditionType.getMetadata().getId()) == null) {
                    setConditionType(conditionType);
                    logger.info("Predefined condition type with id {} registered", conditionType.getMetadata().getId());
                } else {
                    logger.info("The predefined condition type with id {} is already registered, this condition type will be skipped", conditionType.getMetadata().getId());
                }
            } catch (IOException e) {
                logger.error("Error while loading condition definition " + predefinedConditionURL, e);
            }
        }
    }

    private void loadPredefinedActionTypes(BundleContext bundleContext) {
        Enumeration<URL> predefinedActionsEntries = bundleContext.getBundle().findEntries("META-INF/cxs/actions", "*.json", true);
        if (predefinedActionsEntries == null) {
            return;
        }

        ArrayList<PluginType> pluginTypeArrayList = (ArrayList<PluginType>) pluginTypes.get(bundleContext.getBundle().getBundleId());
        while (predefinedActionsEntries.hasMoreElements()) {
            URL predefinedActionURL = predefinedActionsEntries.nextElement();
            logger.debug("Found predefined action at " + predefinedActionURL + ", loading... ");

            try {
                ActionType actionType = CustomObjectMapper.getObjectMapper().readValue(predefinedActionURL, ActionType.class);
                // Register only if action type does not exist yet
                if (getActionType(actionType.getMetadata().getId()) == null) {
                    setActionType(actionType);
                    logger.info("Predefined action type with id {} registered", actionType.getMetadata().getId());
                } else {
                    logger.info("The predefined action type with id {} is already registered, this action type will be skipped", actionType.getMetadata().getId());
                }
            } catch (Exception e) {
                logger.error("Error while loading action definition " + predefinedActionURL, e);
            }
        }

    }

    private void loadPredefinedValueTypes(BundleContext bundleContext) {
        Enumeration<URL> predefinedPropertiesEntries = bundleContext.getBundle().findEntries("META-INF/cxs/values", "*.json", true);
        if (predefinedPropertiesEntries == null) {
            return;
        }
        ArrayList<PluginType> pluginTypeArrayList = (ArrayList<PluginType>) pluginTypes.get(bundleContext.getBundle().getBundleId());
        while (predefinedPropertiesEntries.hasMoreElements()) {
            URL predefinedPropertyURL = predefinedPropertiesEntries.nextElement();
            logger.debug("Found predefined value type at " + predefinedPropertyURL + ", loading... ");

            try {
                ValueType valueType = CustomObjectMapper.getObjectMapper().readValue(predefinedPropertyURL, ValueType.class);
                valueType.setPluginId(bundleContext.getBundle().getBundleId());
                valueTypeById.put(valueType.getId(), valueType);
                pluginTypeArrayList.add(valueType);
                for (String tag : valueType.getTags()) {
                    if (tag != null) {
                        valueType.getTags().add(tag);
                        Set<ValueType> valueTypes = valueTypeByTag.get(tag);
                        if (valueTypes == null) {
                            valueTypes = new LinkedHashSet<ValueType>();
                        }
                        valueTypes.add(valueType);
                        valueTypeByTag.put(tag, valueTypes);
                    } else {
                        // we found a tag that is not defined, we will define it automatically
                        logger.debug("Unknown tag " + tag + " used in property type definition " + predefinedPropertyURL);
                    }
                }
            } catch (Exception e) {
                logger.error("Error while loading property type definition " + predefinedPropertyURL, e);
            }
        }

    }

    public Map<Long, List<PluginType>> getTypesByPlugin() {
        return pluginTypes;
    }

    public Collection<ConditionType> getAllConditionTypes() {
        Collection<ConditionType> all = persistenceService.getAllItems(ConditionType.class);
        for (ConditionType type : all) {
            if (type != null && type.getParentCondition() != null) {
                ParserHelper.resolveConditionType(this, type.getParentCondition());
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
        List<ConditionType> directConditionTypes = persistenceService.query(fieldName, fieldValue,null, ConditionType.class);
        for (ConditionType type : directConditionTypes) {
            if (type.getParentCondition() != null) {
                ParserHelper.resolveConditionType(this, type.getParentCondition());
            }
        }
        conditionTypes.addAll(directConditionTypes);

        return conditionTypes;
    }

    public ConditionType getConditionType(String id) {
        if (id == null) {
            return null;
        }
        ConditionType type = conditionTypeById.get(id);
        if (type == null || type.getVersion() == null) {
            type = persistenceService.load(id, ConditionType.class);
            if (type != null) {
                conditionTypeById.put(id, type);
            }
        }
        if (type != null && type.getParentCondition() != null) {
            ParserHelper.resolveConditionType(this, type.getParentCondition());
        }
        return type;
    }

    public void removeConditionType(String id) {
        persistenceService.remove(id, ConditionType.class);
        conditionTypeById.remove(id);
    }

    public void setConditionType(ConditionType conditionType) {
        conditionTypeById.put(conditionType.getMetadata().getId(), conditionType);
        persistenceService.save(conditionType);
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
        Set<ActionType> actionTypes = new LinkedHashSet<ActionType>();
        List<ActionType> directActionTypes = persistenceService.query(fieldName, fieldValue,null, ActionType.class);
        actionTypes.addAll(directActionTypes);

        return actionTypes;
    }

    public ActionType getActionType(String id) {
        ActionType type = actionTypeById.get(id);
        if (type == null || type.getVersion() == null) {
            type = persistenceService.load(id, ActionType.class);
            if (type != null) {
                actionTypeById.put(id, type);
            }
        }
        return type;
    }

    public void removeActionType(String id) {
        persistenceService.remove(id, ActionType.class);
        actionTypeById.remove(id);
    }

    public void setActionType(ActionType actionType) {
        actionTypeById.put(actionType.getMetadata().getId(), actionType);
        persistenceService.save(actionType);
    }

    public Collection<ValueType> getAllValueTypes() {
        return valueTypeById.values();
    }

    public Set<ValueType> getValueTypeByTag(String tag) {
        Set<ValueType> valueTypes = new LinkedHashSet<ValueType>();
        if (valueTypeByTag.containsKey(tag)) {
            valueTypes.addAll(valueTypeByTag.get(tag));
        }

        return valueTypes;
    }

    public ValueType getValueType(String id) {
        return valueTypeById.get(id);
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                processBundleStartup(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                processBundleStop(event.getBundle().getBundleContext());
                break;
        }
    }

    private void loadPredefinedPropertyMergeStrategies(BundleContext bundleContext) {
        Enumeration<URL> predefinedPropertyMergeStrategyEntries = bundleContext.getBundle().findEntries("META-INF/cxs/mergers", "*.json", true);
        if (predefinedPropertyMergeStrategyEntries == null) {
            return;
        }
        ArrayList<PluginType> pluginTypeArrayList = (ArrayList<PluginType>) pluginTypes.get(bundleContext.getBundle().getBundleId());
        while (predefinedPropertyMergeStrategyEntries.hasMoreElements()) {
            URL predefinedPropertyMergeStrategyURL = predefinedPropertyMergeStrategyEntries.nextElement();
            logger.debug("Found predefined property merge strategy type at " + predefinedPropertyMergeStrategyURL + ", loading... ");

            try {
                PropertyMergeStrategyType propertyMergeStrategyType = CustomObjectMapper.getObjectMapper().readValue(predefinedPropertyMergeStrategyURL, PropertyMergeStrategyType.class);
                propertyMergeStrategyType.setPluginId(bundleContext.getBundle().getBundleId());
                propertyMergeStrategyTypeById.put(propertyMergeStrategyType.getId(), propertyMergeStrategyType);
                pluginTypeArrayList.add(propertyMergeStrategyType);
            } catch (Exception e) {
                logger.error("Error while loading property type definition " + predefinedPropertyMergeStrategyURL, e);
            }
        }

    }

    public PropertyMergeStrategyType getPropertyMergeStrategyType(String id) {
        return propertyMergeStrategyTypeById.get(id);
    }

    public Set<Condition> extractConditionsByType(Condition rootCondition, String typeId) {
        if (rootCondition.containsParameter("subConditions")) {
            @SuppressWarnings("unchecked")
            List<Condition> subConditions = (List<Condition>) rootCondition.getParameter("subConditions");
            Set<Condition> matchingConditions = new HashSet<>();
            for (Condition condition : subConditions) {
                matchingConditions.addAll(extractConditionsByType(condition, typeId));
            }
            return matchingConditions;
        } else if (rootCondition.getConditionTypeId() != null && rootCondition.getConditionTypeId().equals(typeId)) {
            return Collections.singleton(rootCondition);
        } else {
            return Collections.emptySet();
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
        if (rootCondition.containsParameter("subConditions")) {
            @SuppressWarnings("unchecked")
            List<Condition> subConditions = (List<Condition>) rootCondition.getParameter("subConditions");
            List<Condition> matchingConditions = new ArrayList<Condition>();
            for (Condition condition : subConditions) {
                Condition c = extractConditionBySystemTag(condition, systemTag);
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
        } else if (rootCondition.getConditionType() != null && rootCondition.getConditionType().getMetadata().getSystemTags().contains(systemTag)) {
            return rootCondition;
        } else {
            return null;
        }
    }

    @Override
    public boolean resolveConditionType(Condition rootCondition) {
        return ParserHelper.resolveConditionType(this, rootCondition);
    }

    @Override
    public void refresh() {
        reloadTypes(true);
    }
}
