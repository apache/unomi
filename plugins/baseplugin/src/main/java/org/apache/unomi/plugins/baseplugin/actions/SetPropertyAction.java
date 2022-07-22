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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class SetPropertyAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SetPropertyAction.class.getName());

    private EventService eventService;

    private boolean useEventToUpdateProfile = false;

    public void setUseEventToUpdateProfile(boolean useEventToUpdateProfile) {
        this.useEventToUpdateProfile = useEventToUpdateProfile;
    }

    public int execute(Action action, Event event) {
        boolean storeInSession = Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"));
        if (storeInSession && event.getSession() == null) {
            return EventService.NO_CHANGE;
        }

        String propertyName = (String) action.getParameterValues().get("setPropertyName");
        Object propertyValue = getPropertyValue(action, event);

        if (storeInSession) {
            // in the case of session storage we directly update the session
            if (PropertyHelper.setProperty(event.getSession(), propertyName, propertyValue, (String) action.getParameterValues().get("setPropertyStrategy"))) {
                return EventService.SESSION_UPDATED;
            }
        } else {
            if (useEventToUpdateProfile) {
                // in the case of profile storage we use the update profile properties event instead.
                Map<String, Object> propertyToUpdate = new HashMap<>();
                propertyToUpdate.put(propertyName, propertyValue);

                Event updateProperties = new Event("updateProperties", event.getSession(), event.getProfile(), event.getScope(), null, null, new Date());
                updateProperties.setPersistent(false);

                updateProperties.setProperty(UpdatePropertiesAction.PROPS_TO_UPDATE, propertyToUpdate);
                int changes = eventService.send(updateProperties);

                if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                    return EventService.PROFILE_UPDATED;
                }
            } else {
                if (PropertyHelper.setProperty(event.getProfile(), propertyName, propertyValue, (String) action.getParameterValues().get("setPropertyStrategy"))) {
                    return EventService.PROFILE_UPDATED;
                }
            }
        }

        return EventService.NO_CHANGE;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    private Object getPropertyValue(Action action, Event event) {
        Object propertyValue = action.getParameterValues().get("setPropertyValue");
        if (propertyValue == null) {
            propertyValue = action.getParameterValues().get("setPropertyValueMultiple");
        }
        Object propertyValueInteger = action.getParameterValues().get("setPropertyValueInteger");
        Object setPropertyValueMultiple = action.getParameterValues().get("setPropertyValueMultiple");
        Object setPropertyValueBoolean = action.getParameterValues().get("setPropertyValueBoolean");
        Object setPropertyValueCurrentEventTimestamp = action.getParameterValues().get("setPropertyValueCurrentEventTimestamp");
        Object setPropertyValueCurrentDate = action.getParameterValues().get("setPropertyValueCurrentDate");

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        if (propertyValue == null) {
            if (propertyValueInteger != null) {
                propertyValue = PropertyHelper.getInteger(propertyValueInteger);
            }
            if (setPropertyValueMultiple != null) {
                propertyValue = setPropertyValueMultiple;
            }
            if (setPropertyValueBoolean != null) {
                propertyValue = PropertyHelper.getBooleanValue(setPropertyValueBoolean);
            }
            if (setPropertyValueCurrentEventTimestamp != null && PropertyHelper.getBooleanValue(setPropertyValueCurrentEventTimestamp)) {
                propertyValue = format.format(event.getTimeStamp());
            }
            if (setPropertyValueCurrentDate != null && PropertyHelper.getBooleanValue(setPropertyValueCurrentDate)) {
                propertyValue = format.format(new Date());
            }
        }

        if (propertyValue != null && propertyValue.equals("now")) {
            logger.warn("SetPropertyAction with setPropertyValue: 'now' is deprecated, " +
                    "please use 'setPropertyValueCurrentEventTimestamp' or 'setPropertyValueCurrentDate' instead of 'setPropertyValue'");
            propertyValue = format.format(event.getTimeStamp());
        }

        return propertyValue;
    }

}
