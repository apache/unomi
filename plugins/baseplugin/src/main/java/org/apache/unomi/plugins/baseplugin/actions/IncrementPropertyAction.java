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

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class IncrementPropertyAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(IncrementPropertyAction.class.getName());

    @Override
    public int execute(final Action action, final Event event) {
        boolean storeInSession = Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"));
        if (storeInSession && event.getSession() == null) {
            return EventService.NO_CHANGE;
        }

        String propertyName = (String) action.getParameterValues().get("propertyName");
        Profile profile = event.getProfile();
        Session session = event.getSession();

        try {
            Map<String, Object> properties = storeInSession ? session.getProperties() : profile.getProperties();
            Object propertyValue = getPropertyValue(action, event, propertyName, properties);

            if (PropertyHelper.setProperty(properties, propertyName, propertyValue, "alwaysSet")) {
                return storeInSession ? EventService.SESSION_UPDATED : EventService.PROFILE_UPDATED;
            }
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            logger.warn("Error resolving nested property of object. See debug log level for more information");
            if (logger.isDebugEnabled()) {
                logger.error("Error resolving nested property of item: {}", storeInSession ? session : profile, e);
            }
        }

        return EventService.NO_CHANGE;
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
                    if (nestedProperty instanceof Integer) {
                        propertyValue = (int) propertyTargetValue + (int) nestedProperty;
                    }
                } else {
                    propertyValue = propertyTargetValue;
                }
            } else if (propertyTargetValue instanceof Map) {
                if (properties.containsKey(rootPropertyName)) {
                    Map<String, Object> nestedProperty = (Map<String, Object>) PropertyUtils.getNestedProperty(properties, propertyName);
                    if (nestedProperty != null) {
                        ((Map<String, Object>) propertyTargetValue).forEach((k, v) -> {
                            if ((v instanceof Integer && (nestedProperty.containsKey(k) && nestedProperty.get(k) instanceof Integer)) ||
                                    (v instanceof Integer && !nestedProperty.containsKey(k))) {
                                nestedProperty.put(k, nestedProperty.containsKey(k) ? (int) nestedProperty.get(k) + (int) v : v);
                            }
                        });
                        propertyValue = nestedProperty;
                    } else {
                        propertyValue = propertyTargetValue;
                    }
                } else {
                    propertyValue = propertyTargetValue;
                }
            }
        } else {
            if (properties.containsKey(rootPropertyName)) {
                Object nestedProperty = PropertyUtils.getNestedProperty(properties, propertyName);
                if (nestedProperty instanceof Integer) {
                    propertyValue = (int) nestedProperty + 1;
                } else if (nestedProperty instanceof Map) {
                    ((Map<String, Object>) nestedProperty).forEach((k, v) -> {
                        if (v instanceof Integer) {
                            ((Map<String, Integer>) nestedProperty).merge(k, 1, Integer::sum);
                        }
                    });
                    propertyValue = nestedProperty;
                }
            }
        }

        return propertyValue;
    }
}
