package org.oasis_open.wemi.context.server.impl.services;

import org.apache.cxf.helpers.IOUtils;
import org.oasis_open.wemi.context.server.api.PluginType;
import org.oasis_open.wemi.context.server.api.Tag;
import org.oasis_open.wemi.context.server.api.ValueType;
import org.oasis_open.wemi.context.server.api.actions.ActionType;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.persistence.spi.CustomObjectMapper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
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

    private Map<String, Tag> tags = new HashMap<String, Tag>();
    private Set<Tag> rootTags = new LinkedHashSet<Tag>();
    private Map<String, ConditionType> conditionTypeById = new HashMap<String, ConditionType>();
    private Map<String, ActionType> actionTypeById = new HashMap<String, ActionType>();
    private Map<String, ValueType> valueTypeById = new HashMap<String, ValueType>();
    private Map<Tag, Set<ConditionType>> conditionTypeByTag = new HashMap<Tag, Set<ConditionType>>();
    private Map<Tag, Set<ActionType>> actionTypeByTag = new HashMap<Tag, Set<ActionType>>();
    private Map<Tag, Set<ValueType>> valueTypeByTag = new HashMap<Tag, Set<ValueType>>();
    private Map<Long, List<PluginType>> pluginTypes = new HashMap<Long, List<PluginType>>();

    private BundleContext bundleContext;
    private PersistenceService persistenceService;

    public DefinitionsServiceImpl() {
        System.out.println("Instantiating definitions service...");
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        processBundleStartup(bundleContext);

        bundleContext.addBundleListener(this);
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }

        pluginTypes.put(bundleContext.getBundle().getBundleId(), new ArrayList<PluginType>());

        loadPredefinedMappings(bundleContext);

        loadPredefinedTags(bundleContext);

        loadPredefinedConditionTypes(bundleContext);
        loadPredefinedActionTypes(bundleContext);
        loadPredefinedValueTypes(bundleContext);

    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        List<PluginType> types = pluginTypes.get(bundleContext.getBundle().getBundleId());
        if (types != null) {
            for (PluginType type : types) {
                if (type instanceof ActionType) {
                    ActionType actionType = (ActionType) type;
                    actionTypeById.remove(actionType.getId());
                    for (Tag tag : actionType.getTags()) {
                        actionTypeByTag.get(tag).remove(actionType);
                    }
                } else if (type instanceof ConditionType) {
                    ConditionType conditionType = (ConditionType) type;
                    conditionTypeById.remove(conditionType.getId());
                    for (Tag tag : conditionType.getTags()) {
                        conditionTypeByTag.get(tag).remove(conditionType);
                    }
                } else if (type instanceof ValueType) {
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
    }

    private void loadPredefinedMappings(BundleContext bundleContext) {
        Enumeration<URL> predefinedMappings = bundleContext.getBundle().findEntries("META-INF/wemi/mappings", "*.json", true);
        if (predefinedMappings == null) {
            return;
        }
        while (predefinedMappings.hasMoreElements()) {
            URL predefinedMappingURL = predefinedMappings.nextElement();
            logger.debug("Found mapping at " + predefinedMappingURL + ", loading... ");
            try {
                final String path = predefinedMappingURL.getPath();
                String name = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                String content = IOUtils.readStringFromStream(predefinedMappingURL.openStream());
                persistenceService.createMapping(name, content);
            } catch (Exception e) {
                logger.error("Error while loading segment definition " + predefinedMappingURL, e);
            }
        }
    }

    private void loadPredefinedTags(BundleContext bundleContext) {
        Enumeration<URL> predefinedTagEntries = bundleContext.getBundle().findEntries("META-INF/wemi/tags", "*.json", true);
        if (predefinedTagEntries == null) {
            return;
        }
        while (predefinedTagEntries.hasMoreElements()) {
            URL predefinedTagURL = predefinedTagEntries.nextElement();
            logger.debug("Found predefined tags at " + predefinedTagURL + ", loading... ");

            try {
                Tag tag = CustomObjectMapper.getObjectMapper().readValue(predefinedTagURL, Tag.class);
                tags.put(tag.getId(), tag);
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedTagEntries, e);
            }
        }

        // now let's resolve all the children.
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
        Enumeration<URL> predefinedConditionEntries = bundleContext.getBundle().findEntries("META-INF/wemi/conditions", "*.json", true);
        if (predefinedConditionEntries == null) {
            return;
        }
        ArrayList<PluginType> pluginTypeArrayList = (ArrayList<PluginType>) pluginTypes.get(bundleContext.getBundle().getBundleId());
        while (predefinedConditionEntries.hasMoreElements()) {
            URL predefinedConditionURL = predefinedConditionEntries.nextElement();
            logger.debug("Found predefined conditions at " + predefinedConditionURL + ", loading... ");

            try {
                ConditionType conditionType = CustomObjectMapper.getObjectMapper().readValue(predefinedConditionURL, ConditionType.class);
                ParserHelper.populatePluginType(conditionType, bundleContext.getBundle(), "conditions", conditionType.getId());
                conditionTypeById.put(conditionType.getId(), conditionType);
                pluginTypeArrayList.add(conditionType);
                for (String tagId : conditionType.getTagIDs()) {
                    Tag tag = tags.get(tagId);
                    if (tag != null) {
                        conditionType.getTags().add(tag);
                        Set<ConditionType> conditionTypes = conditionTypeByTag.get(tag);
                        if (conditionTypes == null) {
                            conditionTypes = new LinkedHashSet<ConditionType>();
                        }
                        conditionTypes.add(conditionType);
                        conditionTypeByTag.put(tag, conditionTypes);
                    } else {
                        // we found a tag that is not defined, we will define it automatically
                        logger.warn("Unknown tag " + tagId + " used in condition definition " + predefinedConditionURL);
                    }
                }
            } catch (Exception e) {
                logger.error("Error while loading condition definition " + predefinedConditionURL, e);
            }
        }
    }

    private void loadPredefinedActionTypes(BundleContext bundleContext) {
        Enumeration<URL> predefinedActionsEntries = bundleContext.getBundle().findEntries("META-INF/wemi/actions", "*.json", true);
        if (predefinedActionsEntries == null) {
            return;
        }
        ArrayList<PluginType> pluginTypeArrayList = (ArrayList<PluginType>) pluginTypes.get(bundleContext.getBundle().getBundleId());
        while (predefinedActionsEntries.hasMoreElements()) {
            URL predefinedActionURL = predefinedActionsEntries.nextElement();
            logger.debug("Found predefined action at " + predefinedActionURL + ", loading... ");

            try {
                ActionType actionType = CustomObjectMapper.getObjectMapper().readValue(predefinedActionURL, ActionType.class);
                ParserHelper.populatePluginType(actionType, bundleContext.getBundle(), "actions", actionType.getId());
                actionTypeById.put(actionType.getId(), actionType);
                pluginTypeArrayList.add(actionType);
                for (String tagId : actionType.getTagIds()) {
                    Tag tag = tags.get(tagId);
                    if (tag != null) {
                        actionType.getTags().add(tag);
                        Set<ActionType> actionTypes = actionTypeByTag.get(tag);
                        if (actionTypes == null) {
                            actionTypes = new LinkedHashSet<ActionType>();
                        }
                        actionTypes.add(actionType);
                        actionTypeByTag.put(tag, actionTypes);
                    } else {
                        // we found a tag that is not defined, we will define it automatically
                        logger.warn("Unknown tag " + tagId + " used in action definition " + predefinedActionURL);
                    }
                }
            } catch (Exception e) {
                logger.error("Error while loading action definition " + predefinedActionURL, e);
            }
        }

    }

    private void loadPredefinedValueTypes(BundleContext bundleContext) {
        Enumeration<URL> predefinedPropertiesEntries = bundleContext.getBundle().findEntries("META-INF/wemi/values", "*.json", true);
        if (predefinedPropertiesEntries == null) {
            return;
        }
        ArrayList<PluginType> pluginTypeArrayList = (ArrayList<PluginType>) pluginTypes.get(bundleContext.getBundle().getBundleId());
        while (predefinedPropertiesEntries.hasMoreElements()) {
            URL predefinedPropertyURL = predefinedPropertiesEntries.nextElement();
            logger.debug("Found predefined property type at " + predefinedPropertyURL + ", loading... ");

            try {
                ValueType valueType = CustomObjectMapper.getObjectMapper().readValue(predefinedPropertyURL, ValueType.class);
                ParserHelper.populatePluginType(valueType, bundleContext.getBundle(), "values", valueType.getId());
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

    public Tag getTag(Tag tag) {
        Tag completeTag = tags.get(tag.getId());
        if (completeTag == null) {
            return null;
        }
        return completeTag;
    }

    public Collection<ConditionType> getAllConditionTypes() {
        return conditionTypeById.values();
    }

    public Map<Long, List<PluginType>> getTypesByPlugin() {
        return pluginTypes;
    }

    public Set<ConditionType> getConditionTypesByTag(Tag tag, boolean recursive) {
        Set<ConditionType> conditionTypes = new LinkedHashSet<ConditionType>();
        Set<ConditionType> directConditionTypes = conditionTypeByTag.get(tag);
        if (directConditionTypes != null) {
            conditionTypes.addAll(directConditionTypes);
        }
        if (recursive) {
            Tag completeTag = getTag(tag);
            for (Tag subTag : completeTag.getSubTags()) {
                Set<ConditionType> childConditionTypes = getConditionTypesByTag(subTag, true);
                conditionTypes.addAll(childConditionTypes);
            }
        }
        return conditionTypes;
    }

    public ConditionType getConditionType(String id) {
        return conditionTypeById.get(id);
    }

    public Collection<ActionType> getAllActionTypes() {
        return actionTypeById.values();
    }

    public Set<ActionType> getActionTypeByTag(Tag tag, boolean recursive) {
        Set<ActionType> actionTypes = new LinkedHashSet<ActionType>();
        Set<ActionType> directActionTypes = actionTypeByTag.get(tag);
        if (directActionTypes != null) {
            actionTypes.addAll(directActionTypes);
        }
        if (recursive) {
            Tag completeTag = getTag(tag);
            for (Tag subTag : completeTag.getSubTags()) {
                Set<ActionType> childActionTypes = getActionTypeByTag(subTag, true);
                actionTypes.addAll(childActionTypes);
            }
        }
        return actionTypes;
    }

    public ActionType getActionType(String id) {
        return actionTypeById.get(id);
    }

    public Collection<ValueType> getAllValueTypes() {
        return valueTypeById.values();
    }

    public Set<ValueType> getValueTypeByTag(Tag tag, boolean recursive) {
        Set<ValueType> valueTypes = new LinkedHashSet<ValueType>();
        Set<ValueType> directValueTypes = valueTypeByTag.get(tag);
        if (directValueTypes != null) {
            valueTypes.addAll(directValueTypes);
        }
        if (recursive) {
            Tag completeTag = getTag(tag);
            for (Tag subTag : completeTag.getSubTags()) {
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

}
