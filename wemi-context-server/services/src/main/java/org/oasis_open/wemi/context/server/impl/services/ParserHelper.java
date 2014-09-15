package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.PluginType;
import org.oasis_open.wemi.context.server.api.PropertyType;
import org.oasis_open.wemi.context.server.api.TemplateablePluginType;
import org.oasis_open.wemi.context.server.api.ValueType;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionType;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;

public class ParserHelper {

    private static final Logger logger = LoggerFactory.getLogger(ParserHelper.class);

    public static boolean resolveConditionType(DefinitionsService definitionsService, Condition rootCondition) {
        boolean result = false;
        if (rootCondition.getConditionType() == null) {
            ConditionType conditionType = definitionsService.getConditionType(rootCondition.getConditionTypeId());
            if (conditionType != null) {
                rootCondition.setConditionType(conditionType);
                result = true;
            }
        }
        // recursive call for sub-conditions as parameters
        for (Object parameterValue : rootCondition.getParameterValues().values()) {
            if (parameterValue instanceof Condition) {
                Condition parameterValueCondition = (Condition) parameterValue;
                result &= resolveConditionType(definitionsService, parameterValueCondition);
                if (!result) {
                    logger.warn("Couldn't resolve condition type " + parameterValueCondition.getConditionTypeId() + " for parameter value " + parameterValueCondition.getParameterValues() + " from parent condition " + rootCondition.getConditionTypeId());
                }
            } else if (parameterValue instanceof Collection) {
                Collection<Object> valueList = (Collection<Object>) parameterValue;
                for (Object value : valueList) {
                    if (value instanceof Condition) {
                        Condition valueCondition = (Condition) value;
                        result &= resolveConditionType(definitionsService, valueCondition);
                        if (!result) {
                            logger.warn("Couldn't resolve condition type " + valueCondition.getConditionTypeId() + " for parameter value " + valueCondition.getParameterValues() + " from parent condition " + rootCondition.getConditionTypeId());
                        }
                    }
                }
            }
        }
        return result;
    }

    public static void resolveActionType(DefinitionsService definitionsService, Action action) {
        if (action.getActionType() == null) {
            ActionType actionType = definitionsService.getActionType(action.getActionTypeId());
            if (actionType != null) {
                action.setActionType(actionType);
            }
        }
    }

    public static void resolveValueType(DefinitionsService definitionsService, PropertyType propertyType) {
        if (propertyType.getValueType() == null) {
            ValueType valueType = definitionsService.getValueType(propertyType.getValueTypeId());
            if (valueType != null) {
                propertyType.setValueType(valueType);
            }
        }
    }

    public static void populatePluginType(PluginType pluginType, Bundle bundle) {
        populatePluginType(pluginType, bundle, null, null);
    }

    public static void populatePluginType(PluginType pluginType, Bundle bundle, String path, String typeId) {
        if (pluginType.getPluginId() == null) {
            pluginType.setPluginId(bundle.getSymbolicName());
        }
        if (pluginType.getResourceBundle() == null) {
            Enumeration<URL> resourceBundles = bundle.findEntries("/web", "messages*.json", false);
            if (resourceBundles != null) {
                String resourceBundle = "/plugins/" + bundle.getSymbolicName() + "/messages";
                pluginType.setResourceBundle(resourceBundle);
            }
        }
        if (pluginType instanceof TemplateablePluginType) {
            TemplateablePluginType templateablePluginType = (TemplateablePluginType) pluginType;
            if (templateablePluginType.getTemplate() == null && path != null && typeId != null) {
                URL templateURL = bundle.getEntry("/web/" + path + "/" + typeId + ".html");
                if (templateURL != null) {
                    templateablePluginType.setTemplate("/plugins/" + bundle.getSymbolicName() + "/" + path + "/" + typeId + ".html");
                }
            }
        }
    }
}
