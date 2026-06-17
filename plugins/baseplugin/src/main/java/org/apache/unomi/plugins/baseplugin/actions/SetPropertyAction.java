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
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SetPropertyAction implements ActionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetPropertyAction.class.getName());

    private EventService eventService;
    private TracerService tracerService;
    // TODO Temporary solution that should be handle by: https://issues.apache.org/jira/browse/UNOMI-630 (Implement a global solution to avoid multiple same log pollution.)
    private static final AtomicLong nowDeprecatedLogTimestamp = new AtomicLong();

    private boolean useEventToUpdateProfile = false;

    public void setUseEventToUpdateProfile(boolean useEventToUpdateProfile) {
        this.useEventToUpdateProfile = useEventToUpdateProfile;
    }

    public int execute(Action action, Event event) {
        RequestTracer tracer = null;
        if (tracerService != null && tracerService.isTracingEnabled()) {
            tracer = tracerService.getCurrentTracer();
            tracer.startOperation("set-property", 
                "Setting property value", action);
        }

        try {
            boolean storeInSession = Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"));
            if (storeInSession && event.getSession() == null) {
                if (tracer != null) {
                    tracer.endOperation(false, "No session available for session storage");
                }
                return EventService.NO_CHANGE;
            }

            String propertyName = (String) action.getParameterValues().get("setPropertyName");
            Object propertyValue = getPropertyValue(action, event);

            if (tracer != null) {
                Map<String, Object> traceData = new HashMap<>();
                traceData.put("propertyName", propertyName);
                traceData.put("propertyValue", propertyValue);
                traceData.put("storeInSession", storeInSession);
                tracer.trace("Setting property", traceData);
            }

            int result = EventService.NO_CHANGE;
            if (storeInSession) {
                // in the case of session storage we directly update the session
                if (PropertyHelper.setProperty(event.getSession(), propertyName, propertyValue, (String) action.getParameterValues().get("setPropertyStrategy"))) {
                    result = EventService.SESSION_UPDATED;
                }
            } else {
                if (useEventToUpdateProfile) {
                    // in the case of profile storage we use the update profile properties event instead.
                    Map<String, Object> propertyToUpdate = new HashMap<>();
                    propertyToUpdate.put(propertyName, propertyValue);

                    Event updateProperties = new Event("updateProperties", event.getSession(), event.getProfile(), event.getScope(), null, null, new Date());
                    updateProperties.setPersistent(false);

                    updateProperties.setProperty(UpdatePropertiesAction.PROPS_TO_UPDATE, propertyToUpdate);
                    result = eventService.send(updateProperties);
                    if ((result & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                        result = EventService.PROFILE_UPDATED;
                    }
                    } else {
                    if (PropertyHelper.setProperty(event.getProfile(), propertyName, propertyValue, (String) action.getParameterValues().get("setPropertyStrategy"))) {
                        result = EventService.PROFILE_UPDATED;
                    }
                }
            }

            if (tracer != null) {
                tracer.endOperation(result != EventService.NO_CHANGE, 
                    result != EventService.NO_CHANGE ? "Property set successfully" : "No changes needed");
            }
            return result;
        } catch (Exception e) {
            if (tracer != null) {
                tracer.endOperation(false, "Error setting property: " + e.getMessage());
            }
            throw e;
        }
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
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
            // TODO Temporary solution that should be handle by: https://issues.apache.org/jira/browse/UNOMI-630 (Implement a global solution to avoid multiple same log pollution.)
            // warn every 6 hours to avoid log pollution
            long timeStamp = nowDeprecatedLogTimestamp.get();
            long currentTimeStamp = new Date().getTime();
            if (timeStamp == 0 || (timeStamp + TimeUnit.HOURS.toMillis(6) < currentTimeStamp)) {
                LOGGER.warn("SetPropertyAction with setPropertyValue: 'now' is deprecated, " +
                        "please use 'setPropertyValueCurrentEventTimestamp' or 'setPropertyValueCurrentDate' instead of 'setPropertyValue'");
                nowDeprecatedLogTimestamp.set(currentTimeStamp);
            }

            propertyValue = format.format(event.getTimeStamp());
        }

        return propertyValue;
    }

}
