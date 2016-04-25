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

package org.apache.unomi.rest;

import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.Tag;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.conditions.initializers.ChoiceListInitializer;
import org.apache.unomi.api.conditions.initializers.ChoiceListValue;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A helper class to provide localized versions of context server entities.
 */
public class LocalizationHelper {

    private static final Logger logger = LoggerFactory.getLogger(LocalizationHelper.class.getName());

    private BundleContext bundleContext;
    private ResourceBundleHelper resourceBundleHelper;

    /**
     * Creates {@link RESTConditionType}s, localized using the specified language, based on the specified {@link ConditionType}s.
     *
     * @param conditionTypes the {@link ConditionType}s to be localized
     * @param language       the language to use to localize {@link ConditionType}s
     * @return a collection of {@link RESTConditionType}s based on the specified {@link ConditionType}s and localized using the specified language
     */
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

    /**
     * Creates {@link RESTActionType}s, localized using the specified language, based on the specified {@link ActionType}s.
     *
     * @param actionTypes the {@link ActionType}s to be localized
     * @param language    the language to use to localize {@link ActionType}s
     * @return a collection of {@link RESTActionType}s based on the specified {@link ActionType}s and localized using the specified language
     */
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

    /**
     * Creates a {@link RESTConditionType} based on the specified {@link ConditionType} and localized using the specified language.
     *
     * @param conditionType the {@link ConditionType} to be localized
     * @param language      the language to use to localize {@link ConditionType}
     * @return a {@link RESTConditionType} based on the specified {@link ConditionType} and localized using the specified language
     */
    public RESTConditionType generateCondition(ConditionType conditionType, String language) {
        RESTConditionType result = new RESTConditionType();
        result.setId(conditionType.getItemId());

        result.setName(conditionType.getMetadata().getName());
        result.setDescription(conditionType.getMetadata().getDescription());

        result.setTags(conditionType.getMetadata().getTags());

        for (Parameter parameter : conditionType.getParameters()) {
            result.getParameters().add(generateParameter(parameter, language));
        }

        return result;
    }

    /**
     * Creates a {@link RESTActionType} based on the specified {@link ActionType} and localized using the specified language.
     *
     * @param actionType the {@link ActionType} to be localized
     * @param language   the language to use to localize {@link ActionType}
     * @return a {@link RESTActionType} based on the specified {@link ActionType} and localized using the specified language
     */
    public RESTActionType generateAction(ActionType actionType, String language) {
        RESTActionType result = new RESTActionType();
        result.setId(actionType.getItemId());

        result.setName(actionType.getMetadata().getName());
        result.setDescription(actionType.getMetadata().getDescription());

        result.setTags(actionType.getMetadata().getTags());

        List<RESTParameter> parameters = new ArrayList<RESTParameter>();
        for (Parameter parameter : actionType.getParameters()) {
            parameters.add(generateParameter(parameter, language));
        }
        result.setParameters(parameters);

        return result;
    }

    /**
     * Creates a {@link RESTParameter} based on the specified {@link Parameter} and localized using the specified {@link ResourceBundle}.
     *
     * @param parameter the {@link Parameter} to be localized
     * @param language
     * @return a {@link RESTParameter} based on the specified {@link ActionType} and localized using the specified {@link ResourceBundle}
     */
    public RESTParameter generateParameter(Parameter parameter, String language) {
        RESTParameter result = new RESTParameter();
        result.setId(parameter.getId());
        result.setDefaultValue(parameter.getDefaultValue());
        result.setMultivalued(parameter.isMultivalued());
        result.setType(parameter.getType());
        result.setChoiceListValues(generateChoiceListValues(parameter.getChoiceListInitializerFilter(), language));

        return result;
    }

    public List<ChoiceListValue> generateChoiceListValues(String choiceListInitializerFilter, String language) {
        List<ChoiceListValue> result = new ArrayList<ChoiceListValue>();
        if (choiceListInitializerFilter != null && choiceListInitializerFilter.length() > 0) {
            try {
                Collection<ServiceReference<ChoiceListInitializer>> matchingChoiceListInitializerReferences = bundleContext.getServiceReferences(ChoiceListInitializer.class, choiceListInitializerFilter);
                for (ServiceReference<ChoiceListInitializer> choiceListInitializerReference : matchingChoiceListInitializerReferences) {
                    ChoiceListInitializer choiceListInitializer = bundleContext.getService(choiceListInitializerReference);
                    result.addAll(choiceListInitializer.getValues(null));
                }
            } catch (InvalidSyntaxException e) {
                logger.error("Invalid filter", e);
            }
        }
        return result;
    }

    /**
     * Creates {@link RESTValueType}s, localized using the specified language, based on the specified {@link ValueType}s.
     *
     * @param valueTypes the {@link ValueType}s to be localized
     * @param language   the language to use to localize {@link ValueType}s
     * @return a collection of {@link RESTValueType}s based on the specified {@link ValueType}s and localized using the specified language
     */
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

    /**
     * Creates a {@link RESTValueType} based on the specified {@link ValueType} and localized using the specified language.
     *
     * @param valueType the {@link ValueType} to be localized
     * @param language  the language to use to localize {@link ValueType}
     * @return a {@link RESTValueType} based on the specified {@link ValueType} and localized using the specified language
     */
    public RESTValueType generateValueType(ValueType valueType, String language) {
        RESTValueType result = new RESTValueType();
        result.setId(valueType.getId());

        ResourceBundle bundle = resourceBundleHelper.getResourceBundle(valueType, language);
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, valueType.getNameKey()));
        result.setDescription(resourceBundleHelper.getResourceBundleValue(bundle, valueType.getDescriptionKey()));
        result.setTags(generateTags(valueType.getTags(), language));
        return result;
    }

    /**
     * Same as generateTages(tags, language, false).
     */
    public Collection<RESTTag> generateTags(Collection<Tag> tags, String language) {
        return generateTags(tags, language, false);
    }

    /**
     * Creates {@link RESTTag}s, localized using the specified language, based on the specified {@link Tag}s.
     *
     * @param tags         the {@link Tag}s to be localized
     * @param language     the language to use to localize {@link Tag}s
     * @param filterHidden {@code true} to filter out hidden tags, {@code false} otherwise
     * @return a collection of {@link RESTTag}s based on the specified {@link Tag}s and localized using the specified language
     */
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

    /**
     * Same as generateTag(tag, language, false).
     */
    public RESTTag generateTag(Tag tag, String language) {
        return generateTag(tag, language, false);
    }

    /**
     * Creates a {@link RESTTag}, localized using the specified language, based on the specified {@link Tag}.
     *
     * @param tag          the {@link Tag} to be localized
     * @param language     the language to use to localize the {@link Tag}
     * @param filterHidden {@code true} to filter out hidden sub-tags, {@code false} otherwise
     * @return a {@link RESTTag} based on the specified {@link Tag} and localized using the specified language
     */
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
