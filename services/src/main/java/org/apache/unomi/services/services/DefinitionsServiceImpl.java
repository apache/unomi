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

package org.apache.unomi.services.services;

import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.PropertyMergeStrategyType;
import org.apache.unomi.api.Tag;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class DefinitionsServiceImpl implements DefinitionsService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(DefinitionsServiceImpl.class.getName());

    private PersistenceService persistenceService;

    private Map<String, Tag> tags = new HashMap<>();
    private Set<Tag> rootTags = new LinkedHashSet<>();
    private Map<String, ConditionType> conditionTypeById = new HashMap<>();
    private Map<String, ActionType> actionTypeById = new HashMap<>();
    private Map<String, ValueType> valueTypeById = new HashMap<>();
    private Map<Tag, Set<ValueType>> valueTypeByTag = new HashMap<>();
    private Map<Long, List<PluginType>> pluginTypes = new HashMap<>();
    private Map<String, PropertyMergeStrategyType> propertyMergeStrategyTypeById = new HashMap<>();

    private BundleContext bundleContext;
    public DefinitionsServiceImpl() {

    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        processBundleStartup(bundleContext);

        // process already started bundles
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                processBundleStartup(bundle.getBundleContext());
            }
        }

        bundleContext.addBundleListener(this);
        logger.info("Definitions service initialized.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }

        pluginTypes.put(bundleContext.getBundle().getBundleId(), new ArrayList<PluginType>());

        loadPredefinedTags(bundleContext);

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
                    for (Tag tag : valueType.getTags()) {
                        valueTypeByTag.get(tag).remove(valueType);
                    }
                }
            }
        }
    }


    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("Definitions service shutdown.");
    }

    private void loadPredefinedTags(BundleContext bundleContext) {
        Enumeration<URL> predefinedTagEntries = bundleContext.getBundle().findEntries("META-INF/cxs/tags", "*.json", true);
        if (predefinedTagEntries == null) {
            return;
        }
        while (predefinedTagEntries.hasMoreElements()) {
            URL predefinedTagURL = predefinedTagEntries.nextElement();
            logger.debug("Found predefined tags at " + predefinedTagURL + ", loading... ");

            try {
                Tag tag = CustomObjectMapper.getObjectMapper().readValue(predefinedTagURL, Tag.class);
                tag.setPluginId(bundleContext.getBundle().getBundleId());
                tags.put(tag.getId(), tag);
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedTagEntries, e);
            }
        }

        // now let's resolve all the children.
        resolveTagsChildren();
    }

    private void resolveTagsChildren() {
        for (Tag tag : tags.values()) {
            if (tag.getParentId() != null && tag.getParentId().length() > 0) {
                Tag parentTag = tags.get(tag.getParentId());
                if (parentTag != null) {
                    parentTag.getSubTags().add(tag);
                }
            } else {
                rootTags.add(tag);
            }
        }
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
                setConditionType(conditionType);
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
                setActionType(actionType);
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
            logger.debug("Found predefined property type at " + predefinedPropertyURL + ", loading... ");

            try {
                ValueType valueType = CustomObjectMapper.getObjectMapper().readValue(predefinedPropertyURL, ValueType.class);
                valueType.setPluginId(bundleContext.getBundle().getBundleId());
                valueTypeById.put(valueType.getId(), valueType);
                pluginTypeArrayList.add(valueType);
                for (String tagId : valueType.getTagIds()) {
                    Tag tag = tags.get(tagId);
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
                        logger.warn("Unknown tag " + tagId + " used in property type definition " + predefinedPropertyURL);
                    }
                }
            } catch (Exception e) {
                logger.error("Error while loading property type definition " + predefinedPropertyURL, e);
            }
        }

    }

    public Set<Tag> getAllTags() {
        return new HashSet<Tag>(tags.values());
    }

    public Set<Tag> getRootTags() {
        return rootTags;
    }

    public Tag getTag(String tagId) {
        Tag completeTag = tags.get(tagId);
        return completeTag;
    }

    public void addTag(Tag tag) {
        tag.setPluginId(bundleContext.getBundle().getBundleId());
        tags.put(tag.getId(), tag);
        // now let's resolve all the children.
        resolveTagsChildren();
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

    public Set<ConditionType> getConditionTypesByTag(Tag tag, boolean includeFromSubtags) {
        Set<ConditionType> conditionTypes = new LinkedHashSet<ConditionType>();
        List<ConditionType> directConditionTypes = persistenceService.query("metadata.tags",tag.getId(),null, ConditionType.class);
        for (ConditionType type : directConditionTypes) {
            if (type.getParentCondition() != null) {
                ParserHelper.resolveConditionType(this, type.getParentCondition());
            }
        }
        conditionTypes.addAll(directConditionTypes);
        if (includeFromSubtags) {
            for (Tag subTag : tag.getSubTags()) {
                Set<ConditionType> childConditionTypes = getConditionTypesByTag(subTag, true);
                conditionTypes.addAll(childConditionTypes);
            }
        }
        return conditionTypes;
    }

    public ConditionType getConditionType(String id) {
        ConditionType type = conditionTypeById.get(id);
        if (type == null) {
            type = persistenceService.load(id, ConditionType.class);
            conditionTypeById.put(id, type);
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

    public Set<ActionType> getActionTypeByTag(Tag tag, boolean includeFromSubtags) {
        Set<ActionType> actionTypes = new LinkedHashSet<ActionType>();
        List<ActionType> directActionTypes = persistenceService.query("metadata.tags",tag.getId(),null, ActionType.class);
        actionTypes.addAll(directActionTypes);
        if (includeFromSubtags) {
            for (Tag subTag : tag.getSubTags()) {
                Set<ActionType> childActionTypes = getActionTypeByTag(subTag, true);
                actionTypes.addAll(childActionTypes);
            }
        }
        return actionTypes;
    }

    public ActionType getActionType(String id) {
        ActionType type = actionTypeById.get(id);
        if (type == null) {
            type = persistenceService.load(id, ActionType.class);
            actionTypeById.put(id, type);
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

    public Set<ValueType> getValueTypeByTag(Tag tag, boolean includeFromSubtags) {
        Set<ValueType> valueTypes = new LinkedHashSet<ValueType>();
        Set<ValueType> directValueTypes = valueTypeByTag.get(tag);
        if (directValueTypes != null) {
            valueTypes.addAll(directValueTypes);
        }
        if (includeFromSubtags) {
            for (Tag subTag : tag.getSubTags()) {
                Set<ValueType> childValueTypes = getValueTypeByTag(subTag, true);
                valueTypes.addAll(childValueTypes);
            }
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

    public Condition extractConditionByTag(Condition rootCondition, String tagId) {
        if (rootCondition.containsParameter("subConditions")) {
            @SuppressWarnings("unchecked")
            List<Condition> subConditions = (List<Condition>) rootCondition.getParameter("subConditions");
            List<Condition> matchingConditions = new ArrayList<Condition>();
            for (Condition condition : subConditions) {
                Condition c = extractConditionByTag(condition, tagId);
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
        } else if (rootCondition.getConditionType() != null && rootCondition.getConditionType().getMetadata().getTags().contains(tagId)) {
            return rootCondition;
        } else {
            return null;
        }
    }

    @Override
    public boolean resolveConditionType(Condition rootCondition) {
        return ParserHelper.resolveConditionType(this, rootCondition);
    }
}
