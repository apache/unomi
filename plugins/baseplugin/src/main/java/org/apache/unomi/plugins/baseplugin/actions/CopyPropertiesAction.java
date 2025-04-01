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
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CopyPropertiesAction implements ActionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CopyPropertiesAction.class);
    private ProfileService profileService;
    private TracerService tracerService;

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public int execute(Action action, Event event) {
        RequestTracer tracer = tracerService.getCurrentTracer();
        if (!tracer.isEnabled()) {
            tracer.setEnabled(true);
        }

        tracer.startOperation("copy-properties", "Copying properties from event to profile", new HashMap<String, Object>() {{
            put("action.id", action.getActionTypeId());
            put("event.type", event.getEventType());
        }});

        try {
            boolean atLeastOnechanged = false;
            List<String> mandatoryPropTypeSystemTags = (List<String>) action.getParameterValues().get("mandatoryPropTypeSystemTag");
            String singleValueStrategy = (String) action.getParameterValues().get("singleValueStrategy");

            Map<String, Object> propsToCopy = getEventPropsToCopy(action, event);
            tracer.trace("Found properties to copy", new HashMap<String, Object>() {{
                put("properties.count", propsToCopy.size());
            }});

            for (Map.Entry<String, Object> entry : propsToCopy.entrySet()) {
                String mappedProperty = resolvePropertyName(entry.getKey());
                tracer.startOperation("copy-property", "Copying single property", new HashMap<String, Object>() {{
                    put("property.name", mappedProperty);
                }});

                try {
                    // propType Check
                    PropertyType propertyType = profileService.getPropertyType(mappedProperty);
                    Object previousValue = event.getProfile().getProperty(mappedProperty);
                    
                    if (mandatoryPropTypeSystemTags != null && mandatoryPropTypeSystemTags.size() > 0) {
                        if (propertyType == null || propertyType.getMetadata() == null || propertyType.getMetadata().getSystemTags() == null
                                || !propertyType.getMetadata().getSystemTags().containsAll(mandatoryPropTypeSystemTags)) {
                            tracer.trace("Skipping property due to missing required system tags", null);
                            continue;
                        }
                    }

                    String propertyName = "properties." + mappedProperty;
                    final boolean changed;
                    if (previousValue == null && propertyType == null) {
                        changed = PropertyHelper.setProperty(event.getProfile(), propertyName, entry.getValue(), "alwaysSet");
                    } else {
                        boolean propertyTypeIsMultiValued =
                                propertyType != null && propertyType.isMultivalued() != null && propertyType.isMultivalued();
                        boolean multipleIsExpected = previousValue != null ? previousValue instanceof List : propertyTypeIsMultiValued;

                        if (multipleIsExpected) {
                            changed = PropertyHelper.setProperty(event.getProfile(), propertyName, entry.getValue(), "addValues");
                        } else if (entry.getValue() instanceof List) {
                            LOGGER.error("Impossible to copy the property of type List to the profile, either a single value already exist on the profile or the property type is declared as a single value property. Enable debug log level for more information");
                            LOGGER.debug("cannot copy property {}, because it's a List", mappedProperty);
                            tracer.trace("Error: Cannot copy List to single value property", null);
                            changed = false;
                        } else {
                            changed = PropertyHelper.setProperty(event.getProfile(), propertyName, entry.getValue(), singleValueStrategy);
                        }
                    }
                    
                    tracer.trace("Property copy result", new HashMap<String, Object>() {{
                        put("changed", changed);
                    }});
                    atLeastOnechanged = atLeastOnechanged || changed;
                } finally {
                    tracer.endOperation(null, "Completed property copy operation");
                }
            }

            final boolean finalAtLeastOneChanged = atLeastOnechanged;
            tracer.trace("Overall copy operation result", new HashMap<String, Object>() {{
                put("profile_updated", finalAtLeastOneChanged);
            }});
            return finalAtLeastOneChanged ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
        } catch (Exception e) {
            tracer.trace("Error during property copy operation", e);
            throw e;
        } finally {
            tracer.endOperation(null, "Completed all property copy operations");
        }
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
            LOGGER.error("Unable to extract properties to be copied from the event to the profile using root property: {}", rootProperty, e);
        }

        return propsToCopy;
    }

    private String resolvePropertyName(String propertyName) {
        String propertyMapping = profileService.getPropertyTypeMapping(propertyName);
        return (propertyMapping != null) ? propertyMapping : propertyName;
    }
}
