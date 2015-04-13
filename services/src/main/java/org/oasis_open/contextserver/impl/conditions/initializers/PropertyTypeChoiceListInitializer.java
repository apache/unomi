package org.oasis_open.contextserver.impl.conditions.initializers;

/*
 * #%L
 * context-server-services
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

import org.oasis_open.contextserver.api.PropertyType;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.conditions.initializers.I18nSupport;
import org.oasis_open.contextserver.api.services.DefinitionsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Initializer for the set of available profile properties.
 */
public class PropertyTypeChoiceListInitializer implements ChoiceListInitializer, I18nSupport {

    DefinitionsService definitionsService;
    private String tagId;

    @Override
    public List<ChoiceListValue> getValues(Object context) {
        Set<PropertyType> profileProperties = definitionsService.getPropertyTypeByTag(definitionsService.getTag(tagId), true);
        List<ChoiceListValue> choiceListValues = new ArrayList<>(profileProperties.size());
        for (PropertyType propertyType : profileProperties) {
            PropertyTypeChoiceListValue value = new PropertyTypeChoiceListValue("properties." + propertyType.getId(), propertyType.getNameKey(),
                    propertyType.getValueTypeId(), propertyType.isMultivalued(), propertyType.getPluginId());
            choiceListValues.add(value);
        }
        return choiceListValues;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }
}
