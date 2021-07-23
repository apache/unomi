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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class ManageLastVisitPropertyAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ManageLastVisitPropertyAction.class.getName());

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

        Object propertyValue = action.getParameterValues().get("setPropertyValue");

        if (propertyValue != null && propertyValue.equals("now")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date date = new Date();

            Object propertyFirstVisit = event.getProfile().getProperties().get("firstVisit");
            try {
                if (propertyFirstVisit != null) {
                    if (propertyFirstVisit instanceof String) {
                        date = format.parse((String) propertyFirstVisit);
                    } else if (propertyFirstVisit instanceof Date) {
                        date = (Date) propertyFirstVisit;
                    } else {
                        date = format.parse(propertyFirstVisit.toString());
                    }
                }

                if (event.getTimeStamp().after(date)) {
                    date = event.getTimeStamp();
                }
            } catch (ParseException e) {
                logger.error("Error parsing firstVisit date property. See debug log level for more information");
                if (logger.isDebugEnabled()) {
                    logger.debug("Error parsing date: {}", propertyFirstVisit, e);
                }
            }
            event.setProperty("lastVisit", format.format(date));
        }

        return new SetPropertyAction().execute(action, event);
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

}
