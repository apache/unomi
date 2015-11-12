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

package org.oasis_open.contextserver.plugins.baseplugin.actions;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.EventService;
import org.oasis_open.contextserver.persistence.spi.PropertyHelper;

import java.text.SimpleDateFormat;
import java.util.*;

public class SetPropertyAction implements ActionExecutor {

    public SetPropertyAction() {
    }

    public String getActionId() {
        return "setPropertyAction";
    }

    public int execute(Action action, Event event) {
        Object propertyValue = action.getParameterValues().get("setPropertyValue");
        Object propertyValueInteger = action.getParameterValues().get("setPropertyValueInteger");

        if(propertyValueInteger != null && propertyValue == null) {
            propertyValue = PropertyHelper.getInteger(propertyValueInteger);
        }

        if (propertyValue != null && propertyValue.equals("now")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            propertyValue = format.format(event.getTimeStamp());
        }
        String propertyName = (String) action.getParameterValues().get("setPropertyName");

        boolean storeInSession = Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"));

        Object target = storeInSession ? event.getSession() : event.getProfile();

        if (PropertyHelper.setProperty(target, propertyName, propertyValue, (String) action.getParameterValues().get("setPropertyStrategy"))) {
            return storeInSession ? EventService.SESSION_UPDATED : EventService.PROFILE_UPDATED;
        }
        return EventService.NO_CHANGE;
    }

}
