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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class SetPropertyAction implements ActionExecutor {

    private EventService eventService;

    public int execute(Action action, Event event) {
        boolean storeInSession = Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"));

        String propertyName = (String) action.getParameterValues().get("setPropertyName");

        Object propertyValue = action.getParameterValues().get("setPropertyValue");
        if (propertyValue == null) {
            propertyValue = action.getParameterValues().get("setPropertyValueMultiple");
        }
        Object propertyValueInteger = action.getParameterValues().get("setPropertyValueInteger");
        Object setPropertyValueMultiple = action.getParameterValues().get("setPropertyValueMultiple");
        Object setPropertyValueBoolean = action.getParameterValues().get("setPropertyValueBoolean");

        Event updateProfileProperties = new Event("updateProfileProperties", null, event.getProfile(), null, null, event.getProfile(), new Date());
        updateProfileProperties.setPersistent(false);

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
        }
        if (propertyValue != null && propertyValue.equals("now")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            propertyValue = format.format(event.getTimeStamp());
        }

        if (storeInSession) {
            // in the case of session storage we directly update the session
            Object target =  event.getSession();

            if (PropertyHelper.setProperty(target, propertyName, propertyValue, (String) action.getParameterValues().get("setPropertyStrategy"))) {
                return EventService.SESSION_UPDATED;
            }
        } else {
            // in the case of profile storage we use the update profile properties event instead.
            Map<String, Object> propertyToUpdate = new HashMap<>();
            propertyToUpdate.put(propertyName, propertyValue);

            updateProfileProperties.setProperty(UpdateProfilePropertiesAction.PROPS_TO_UPDATE, propertyToUpdate);
            int changes = eventService.send(updateProfileProperties);

            if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                return EventService.PROFILE_UPDATED;
            }
        }

        return EventService.NO_CHANGE;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

}
