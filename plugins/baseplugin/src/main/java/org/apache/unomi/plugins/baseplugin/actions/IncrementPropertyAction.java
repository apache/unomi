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

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class IncrementPropertyAction implements ActionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementPropertyAction.class.getName());
    private TracerService tracerService;

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    @Override
    public int execute(final Action action, final Event event) {
        RequestTracer tracer = null;
        if (tracerService != null && tracerService.isTracingEnabled()) {
            tracer = tracerService.getCurrentTracer();
            tracer.startOperation("increment-property", 
                "Incrementing property", action);
        }

        try {
            boolean storeInSession = Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"));
            if (storeInSession && event.getSession() == null) {
                if (tracer != null) {
                    tracer.endOperation(false, "No session available for storing property");
                }
                return EventService.NO_CHANGE;
            }

            String propertyName = (String) action.getParameterValues().get("propertyName");
            Profile profile = event.getProfile();
            Session session = event.getSession();

            if (tracer != null) {
                tracer.trace("Processing property increment", Map.of(
                    "propertyName", propertyName,
                    "storeInSession", storeInSession,
                    "hasTarget", event.getTarget() != null
                ));
            }

            try {
                Map<String, Object> properties = storeInSession ? session.getProperties() : profile.getProperties();
                Object propertyValue = getPropertyValue(action, event, propertyName, properties);
                boolean updated = PropertyHelper.setProperty(properties, propertyName, propertyValue, "alwaysSet");
                
                if (tracer != null) {
                    tracer.trace("Property increment result", Map.of(
                        "newValue", propertyValue,
                        "isUpdated", updated
                    ));
                    tracer.endOperation(updated, 
                        updated ? "Property incremented successfully" : "No changes needed");
                }
                return updated ? (storeInSession ? EventService.SESSION_UPDATED : EventService.PROFILE_UPDATED) : EventService.NO_CHANGE;
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                if (tracer != null) {
                    tracer.endOperation(false, "Error resolving nested property: " + e.getMessage());
                }
                LOGGER.warn("Error resolving nested property of object. See debug log level for more information");
                LOGGER.debug("Error resolving nested property of item: {}", storeInSession ? session : profile, e);
            } catch (IllegalStateException ee) {
                if (tracer != null) {
                    tracer.endOperation(false, "Error incrementing property: " + ee.getMessage());
                }
                LOGGER.warn("Error increment existing property, because existing property doesn't have expected type. See debug log level for more information");
                LOGGER.debug("{}", ee.getMessage(), ee);
            }

            return EventService.NO_CHANGE;
        } catch (Exception e) {
            if (tracer != null) {
                tracer.endOperation(false, "Error in property increment: " + e.getMessage());
            }
            throw e;
        }
    }

    private Object getPropertyValue(Action action, Event event, String propertyName, Map<String, Object> properties)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        String propertyTarget = (String) action.getParameterValues().get("propertyTarget");
        String rootPropertyName = propertyName.split("\\.")[0];
        Object propertyValue = 1;

        Object propertyTargetValue = null;

        if (StringUtils.isNotEmpty(propertyTarget)) {
            propertyTargetValue = PropertyUtils.getNestedProperty(((CustomItem) event.getTarget()).getProperties(), propertyTarget);
        }

        if (propertyTargetValue != null) {
            if (propertyTargetValue instanceof Integer) {
                if (properties.containsKey(rootPropertyName)) {
                    Object nestedProperty = PropertyUtils.getNestedProperty(properties, propertyName);
                    if (nestedProperty == null) {
                        propertyValue = propertyTargetValue;
                    } else if (nestedProperty instanceof Integer) {
                        propertyValue = (int) propertyTargetValue + (int) nestedProperty;
                    } else {
                        throw new IllegalStateException("Property: " + propertyName + " already exist, can not increment the property because the exiting property is not integer");
                    }
                } else {
                    propertyValue = propertyTargetValue;
                }
            } else if (propertyTargetValue instanceof Map) {
                if (properties.containsKey(rootPropertyName)) {
                    Object nestedPropertyValue = PropertyUtils.getNestedProperty(properties, propertyName);
                    if (nestedPropertyValue == null) {
                        propertyValue = propertyTargetValue;
                    } else if (nestedPropertyValue instanceof Map) {
                        // Create a new map to avoid modifying the original Object
                        Map<String, Object> newPropertyValue = new HashMap<>();
                        Map<String, Object> nestedProperty = (Map<String, Object>) nestedPropertyValue;

                        // increment with target
                        ((Map<String, Object>) propertyTargetValue).forEach((key, targetValue) -> {
                            if ((targetValue instanceof Integer && (nestedProperty.containsKey(key) && nestedProperty.get(key) instanceof Integer)) ||
                                    (targetValue instanceof Integer && !nestedProperty.containsKey(key))) {
                                newPropertyValue.put(key, nestedProperty.containsKey(key) ? (int) nestedProperty.get(key) + (int) targetValue : targetValue);
                            }
                        });

                        // add original props that was not incremented
                        nestedProperty.forEach((key, nestedValue) -> {
                            if (!newPropertyValue.containsKey(key)) {
                                newPropertyValue.put(key, nestedValue);
                            }
                        });
                        propertyValue = newPropertyValue;
                    } else {
                        throw new IllegalStateException("Property: " + propertyName + " already exist, can not increment the properties from the map because the exiting property is not map");
                    }
                } else {
                    propertyValue = propertyTargetValue;
                }
            }
        } else {
            if (properties.containsKey(rootPropertyName)) {
                Object nestedPropertyValue = PropertyUtils.getNestedProperty(properties, propertyName);
                if (nestedPropertyValue == null) {
                    propertyValue = 1;
                } else if (nestedPropertyValue instanceof Integer) {
                    propertyValue = (int) nestedPropertyValue + 1;
                } else if (nestedPropertyValue instanceof Map) {
                    // Create a new map to avoid modifying the original object
                    Map<String, Object> newPropertyValue = new HashMap<>();
                    Map<String, Object> nestedProperty = (Map<String, Object>) nestedPropertyValue;
                    nestedProperty.forEach((key, propValue) -> newPropertyValue.put(key, propValue instanceof Integer ? (int) propValue + 1 : propValue));
                    propertyValue = newPropertyValue;
                } else {
                    throw new IllegalStateException("Property: " + propertyName + " already exist, can not increment the property because the exiting property is not integer or map");
                }
            }
        }

        return propertyValue;
    }
}
