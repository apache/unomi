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
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * A action to copy an event property to a profile property
 */
public class EventToProfilePropertyAction implements ActionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventToProfilePropertyAction.class);
    private TracerService tracerService;

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    public int execute(Action action, Event event) {
        final RequestTracer tracer = (tracerService != null && tracerService.isTracingEnabled())
                ? tracerService.getCurrentTracer() : null;

        String eventPropertyName = (String) action.getParameterValues().get("eventPropertyName");
        String profilePropertyName = (String) action.getParameterValues().get("profilePropertyName");

        if (tracer != null) {
            tracer.startOperation("event-to-profile-property", "Copying event property to profile property", new HashMap<String, Object>() {{
                put("action.type", action.getActionTypeId());
                put("event.type", event.getEventType());
                put("event.property", eventPropertyName);
                put("profile.property", profilePropertyName);
            }});
        }

        try {
            Object currentProfileValue = event.getProfile().getProperty(profilePropertyName);
            Object eventValue = event.getProperty(eventPropertyName);
            boolean needsUpdate = currentProfileValue == null || !currentProfileValue.equals(eventValue);

            if (tracer != null) {
                tracer.trace("Property values", new HashMap<String, Object>() {{
                    put("current.profile.value", currentProfileValue);
                    put("event.value", eventValue);
                    put("needs.update", needsUpdate);
                }});
            }

            if (needsUpdate) {
                event.getProfile().setProperty(profilePropertyName, eventValue);
                if (tracer != null) tracer.trace("Property updated", null);
                return EventService.PROFILE_UPDATED;
            }
            if (tracer != null) tracer.trace("No update needed", null);
            return EventService.NO_CHANGE;
        } catch (Exception e) {
            if (tracer != null) tracer.trace("Error during property copy", e);
            throw e;
        } finally {
            if (tracer != null) tracer.endOperation(null, "Completed event to profile property copy");
        }
    }
}
