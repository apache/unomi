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
import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class IncrementPropertyAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(IncrementPropertyAction.class.getName());
    private EventService eventService;

    @Override
    public int execute(final Action action, final Event event) {
        boolean storeInSession = Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"));
        if (storeInSession && event.getSession() == null) {
            return EventService.NO_CHANGE;
        }

        Profile profile = event.getProfile();
        String propertyName = (String) action.getParameterValues().get("propertyName");
        String propertyTarget = (String) action.getParameterValues().get("propertyTarget");
        String rootPropertyName = propertyName.split("\\.")[0];
        Object value = null;
        Object propertyValue = 1;

        try {
            if (StringUtils.isNotEmpty(propertyTarget)) {
                value = PropertyUtils.getNestedProperty(((CustomItem) event.getTarget()).getProperties(), propertyTarget);
            }

            if (value != null) {
                if (value instanceof Integer) {
                    if (profile.getProperty(rootPropertyName) != null) {
                        propertyValue = (int) value + (int) PropertyUtils.getNestedProperty(profile.getProperties(), propertyName);
                    } else {
                        propertyValue = value;
                    }
                } else if (value instanceof Map) {
                    if (profile.getProperty(rootPropertyName) != null) {
                        Map<String, Integer> p = (Map<String, Integer>) PropertyUtils.getNestedProperty(profile.getProperties(), propertyName);
                        ((Map<String, Integer>) value).forEach((k, v) -> p.put(k, p.containsKey(k) ? p.get(k) + v : v));

                        propertyValue = p;
                    } else {
                        propertyValue = value;
                    }
                }
            } else {
                if (profile.getProperty(rootPropertyName) != null) {
                    Object p = PropertyUtils.getNestedProperty(profile.getProperties(), propertyName);
                    if (p instanceof Integer) {
                        propertyValue = (int) p + 1;
                    } else if (p instanceof Map) {
                        ((Map<String, Integer>) p).forEach((k, v) -> ((Map<String, Integer>) p).merge(k, 1, Integer::sum));
                        propertyValue = p;
                    }
                }
            }

            PropertyHelper.setProperty(profile.getProperties(), propertyName, propertyValue, "alwaysSet");

            Object rootPropertyValue = PropertyUtils.getNestedProperty(profile.getProperties(), rootPropertyName);
            if (storeInSession) {
                if (PropertyHelper.setProperty(event.getSession(), rootPropertyName, rootPropertyValue, "alwaysSet")) {
                    return EventService.SESSION_UPDATED;
                }
            } else {
                Event updatePropertiesEvent = new Event("updateProperties", event.getSession(), profile, event.getSourceId(), null, event.getTarget(), new Date());
                Map<String, Object> propertyToUpdate = new HashMap<>();
                propertyToUpdate.put("properties." + rootPropertyName, rootPropertyValue);
                updatePropertiesEvent.setProperty(UpdatePropertiesAction.PROPS_TO_UPDATE, propertyToUpdate);

                return eventService.send(updatePropertiesEvent);
            }
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            logger.error("Error resolving nested property of profile: {}", profile, e);
        }

        return EventService.NO_CHANGE;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }
}
