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

package org.apache.unomi.plugins.baseplugin.actions;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CopyPropertiesAction implements ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CopyPropertiesAction.class);
    private ProfileService profileService;

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public int execute(Action action, Event event) {
        boolean atLeastOnechanged = false;
        List<String> mandatoryPropTypeSystemTags = (List<String>) action.getParameterValues().get("mandatoryPropTypeSystemTag");
        String singleValueStrategy = (String) action.getParameterValues().get("singleValueStrategy");
        for (Map.Entry<String, Object> entry : getEventPropsToCopy(action, event).entrySet()) {
            String mappedProperty = resolvePropertyName(entry.getKey());

            // propType Check
            PropertyType propertyType = profileService.getPropertyType(mappedProperty);
            Object previousValue = event.getProfile().getProperty(mappedProperty);
            if (mandatoryPropTypeSystemTags != null && mandatoryPropTypeSystemTags.size() > 0) {
                if (propertyType == null || propertyType.getMetadata() == null || propertyType.getMetadata().getSystemTags() == null
                        || !propertyType.getMetadata().getSystemTags().containsAll(mandatoryPropTypeSystemTags)) {
                    continue;
                }
            }
            String propertyName = "properties." + mappedProperty;
            boolean changed = false;
            if (previousValue == null && propertyType == null) {
                changed = PropertyHelper.setProperty(event.getProfile(), propertyName, entry.getValue(), "alwaysSet");
            } else {
                boolean propertyTypeIsMultiValued =
                        propertyType != null && propertyType.isMultivalued() != null && propertyType.isMultivalued();
                boolean multipleIsExpected = previousValue != null ? previousValue instanceof List : propertyTypeIsMultiValued;

                if (multipleIsExpected) {
                    changed = PropertyHelper.setProperty(event.getProfile(), propertyName, entry.getValue(), "addValues");
                } else if (entry.getValue() instanceof List) {
                    logger.error(
                            "Impossible to copy the property of type List to the profile, either a single value already exist on the profile or the property type is declared as a single value property. Enable debug log level for more information");
                    if (logger.isDebugEnabled()) {
                        logger.debug("cannot copy property {}, because it's a List", mappedProperty);
                    }
                } else {
                    changed = PropertyHelper.setProperty(event.getProfile(), propertyName, entry.getValue(), singleValueStrategy);
                }
            }
            atLeastOnechanged = atLeastOnechanged || changed;
        }
        return atLeastOnechanged ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
    }

    private Map<String, Object> getEventPropsToCopy(Action action, Event event) {
        Map<String, Object> propsToCopy = new HashMap<String, Object>();

        String rootProperty = (String) action.getParameterValues().get("rootProperty");
        boolean copyEventProps = false;

        if (StringUtils.isEmpty(rootProperty)) {
            copyEventProps = true;
            rootProperty = "target.properties";
        }

        // copy props from the event.properties
        if (copyEventProps && event.getProperties() != null) {
            propsToCopy.putAll(event.getProperties());
        }

        // copy props from the specified level (default is: target.properties)
        try {
            Object targetProperties = BeanUtilsBean.getInstance().getPropertyUtils().getProperty(event, rootProperty);
            if (targetProperties instanceof Map) {
                propsToCopy.putAll((Map) targetProperties);
            }
        } catch (Exception e) {
            logger.error("Unable to extract properties to be copied from the event to the profile using root property: {}", rootProperty,
                    e);
        }

        return propsToCopy;
    }

    private String resolvePropertyName(String propertyName) {
        String propertyMapping = profileService.getPropertyTypeMapping(propertyName);
        return (propertyMapping != null) ? propertyMapping : propertyName;
    }
}
