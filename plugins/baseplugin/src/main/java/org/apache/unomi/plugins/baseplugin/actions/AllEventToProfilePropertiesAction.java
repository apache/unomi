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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllEventToProfilePropertiesAction implements ActionExecutor {

    private ProfileService profileService;

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public int execute(Action action, Event event) {
        boolean changed = false;
        Map<String, Object> properties = getEventPropsToCopy(action, event);

        List<String> mandatoryPropTypeSystemTags = (List<String>) action.getParameterValues().get("mandatoryPropTypeSystemTag");
        for (Map.Entry<String, Object> entry : properties.entrySet()) {

            // propType Check
            if (mandatoryPropTypeSystemTags != null && mandatoryPropTypeSystemTags.size() > 0) {
                PropertyType propertyType = profileService.getPropertyType(entry.getKey());
                if (propertyType == null ||
                        propertyType.getMetadata() == null ||
                        propertyType.getMetadata().getSystemTags() == null ||
                        !propertyType.getMetadata().getSystemTags().containsAll(mandatoryPropTypeSystemTags)) {
                    continue;
                }
            }

            // TODO: handle multiple values = addValue using PropertyHelper
            // TODO: handle single value = alwaysSet using PropertyHelper
            // TODO: check propertyType for value type, string, long, etc...
            if (event.getProfile().getProperty(entry.getKey()) == null || !event.getProfile().getProperty(entry.getKey()).equals(event.getProperty(entry.getKey()))) {
                String propertyMapping = profileService.getPropertyTypeMapping(entry.getKey());
                String propertyName = (propertyMapping != null) ? propertyMapping : entry.getKey();
                event.getProfile().setProperty(propertyName, entry.getValue());
                changed = true;
            }
        }
        return changed ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
    }

    private Map<String, Object> getEventPropsToCopy(Action action, Event event) {
        Map<String, Object> propsToCopy = new HashMap<String, Object>();

        String rootProperty = (String) action.getParameterValues().get("rootProperty");
        boolean copyEventProps = false;

        if (rootProperty == null || rootProperty.length() == 0) {
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
            // Ignore
        }

        return propsToCopy;
    }
}
