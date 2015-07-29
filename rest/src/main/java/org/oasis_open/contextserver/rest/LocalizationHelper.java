package org.oasis_open.contextserver.rest;

/*
 * #%L
 * context-server-rest
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.Parameter;
import org.oasis_open.contextserver.api.PluginType;
import org.oasis_open.contextserver.api.Tag;
import org.oasis_open.contextserver.api.ValueType;
import org.oasis_open.contextserver.api.actions.ActionType;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.conditions.initializers.I18nSupport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;

public class LocalizationHelper {

    private static final Logger logger = LoggerFactory.getLogger(LocalizationHelper.class.getName());

    private BundleContext bundleContext;
    private ResourceBundleHelper resourceBundleHelper;

    public Collection<RESTConditionType> generateConditions(Collection<ConditionType> conditionTypes, String language) {
        List<RESTConditionType> result = new ArrayList<RESTConditionType>();
        if (conditionTypes == null) {
            return result;
        }
        for (ConditionType conditionType : conditionTypes) {
            result.add(generateCondition(conditionType, language));
        }
        return result;
    }

    public Collection<RESTActionType> generateActions(Collection<ActionType> actionTypes, String language) {
        List<RESTActionType> result = new ArrayList<RESTActionType>();
        if (actionTypes == null) {
            return result;
        }
        for (ActionType actionType : actionTypes) {
            result.add(generateAction(actionType, language));
        }
        return result;
    }

    public RESTConditionType generateCondition(ConditionType conditionType, String language) {
        RESTConditionType result = new RESTConditionType();
        result.setId(conditionType.getId());

        ResourceBundle bundle = resourceBundleHelper.getResourceBundle(conditionType, language);
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, conditionType.getNameKey()));
        result.setDescription(resourceBundleHelper.getResourceBundleValue(bundle, conditionType.getDescriptionKey()));

        result.setTags(conditionType.getTagIDs());

        for (Parameter parameter : conditionType.getParameters()) {
            result.getParameters().add(generateParameter(parameter, bundle));
        }

        return result;
    }

    public RESTActionType generateAction(ActionType actionType, String language) {
        RESTActionType result = new RESTActionType();
        result.setId(actionType.getId());

        ResourceBundle bundle = resourceBundleHelper.getResourceBundle(actionType, language);
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, actionType.getNameKey()));
        result.setDescription(resourceBundleHelper.getResourceBundleValue(bundle, actionType.getDescriptionKey()));

        result.setTags(actionType.getTagIds());

        List<RESTParameter> parameters = new ArrayList<RESTParameter>();
        for (Parameter parameter : actionType.getParameters()) {
            parameters.add(generateParameter(parameter, bundle));
        }
        result.setParameters(parameters);

        return result;
    }

    public RESTParameter generateParameter(Parameter parameter, ResourceBundle bundle) {
        RESTParameter result = new RESTParameter();
        result.setId(parameter.getId());
        result.setDefaultValue(parameter.getDefaultValue());
        result.setMultivalued(parameter.isMultivalued());
        result.setType(parameter.getType());

        localizeChoiceListValues(bundle, result.getChoiceListValues(), parameter.getChoiceListInitializerFilter());

        return result;
    }

    public void localizeChoiceListValues(ResourceBundle bundle, List<ChoiceListValue> result, String choiceListInitializerFilter) {
        if (choiceListInitializerFilter != null && choiceListInitializerFilter.length() > 0) {
            try {
                Collection<ServiceReference<ChoiceListInitializer>> matchingChoiceListInitializerReferences = bundleContext.getServiceReferences(ChoiceListInitializer.class, choiceListInitializerFilter);
                for (ServiceReference<ChoiceListInitializer> choiceListInitializerReference : matchingChoiceListInitializerReferences) {
                    ChoiceListInitializer choiceListInitializer = bundleContext.getService(choiceListInitializerReference);
                    List<ChoiceListValue> options = choiceListInitializer.getValues(bundle.getLocale());
                    if (choiceListInitializer instanceof I18nSupport) {
                        for (ChoiceListValue value : options) {
                            if (value instanceof PluginType) {
                                result.add(value.localizedCopy(resourceBundleHelper.getResourceBundleValue(resourceBundleHelper.getResourceBundle((PluginType) value, bundle.getLocale().getLanguage()), value.getName())));
                            } else {
                                result.add(value.localizedCopy(resourceBundleHelper.getResourceBundleValue(bundle, value.getName())));
                            }
                        }
                    } else {
                        result.addAll(options);
                    }
                }
            } catch (InvalidSyntaxException e) {
                logger.error("Invalid filter",e);
            }
        }
    }

    public Collection<RESTValueType> generateValueTypes(Collection<ValueType> valueTypes, String language) {
        List<RESTValueType> result = new ArrayList<RESTValueType>();
        if (valueTypes == null) {
            return result;
        }
        for (ValueType valueType : valueTypes) {
            result.add(generateValueType(valueType, language));
        }
        return result;
    }

    public RESTValueType generateValueType(ValueType valueType, String language) {
        RESTValueType result = new RESTValueType();
        result.setId(valueType.getId());

        ResourceBundle bundle = resourceBundleHelper.getResourceBundle(valueType, language);
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, valueType.getNameKey()));
        result.setDescription(resourceBundleHelper.getResourceBundleValue(bundle, valueType.getDescriptionKey()));
        result.setTags(generateTags(valueType.getTags(), language));
        return result;
    }

    public Collection<RESTTag> generateTags(Collection<Tag> tags, String language) {
        return generateTags(tags, language, false);
    }

    public Collection<RESTTag> generateTags(Collection<Tag> tags, String language, boolean filterHidden) {
        List<RESTTag> result = new ArrayList<RESTTag>();
        for (Tag tag : tags) {
            RESTTag subTag = generateTag(tag, language, filterHidden);
            if (subTag != null) {
                result.add(subTag);
            }
        }
        return result;
    }

    public RESTTag generateTag(Tag tag, String language) {
        return generateTag(tag, language, false);
    }

    public RESTTag generateTag(Tag tag, String language, boolean filterHidden) {
        if (filterHidden && tag.isHidden()) {
            return null;
        }
        RESTTag result = new RESTTag();
        result.setId(tag.getId());
        ResourceBundle bundle = resourceBundleHelper.getResourceBundle(tag, language);
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, tag.getNameKey()));
        result.setDescription(resourceBundleHelper.getResourceBundleValue(bundle, tag.getDescriptionKey()));
        result.setParentId(tag.getParentId());
        result.setRank(tag.getRank());
        result.setSubTags(generateTags(tag.getSubTags(), language, filterHidden));
        return result;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setResourceBundleHelper(ResourceBundleHelper resourceBundleHelper) {
        this.resourceBundleHelper = resourceBundleHelper;
    }
}
