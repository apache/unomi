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

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;

import java.util.HashMap;
import java.util.Map;

public class SendEventAction implements ActionExecutor {

    private EventService eventService;
    private TracerService tracerService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    @Override
    public int execute(Action action, Event event) {
        RequestTracer tracer = null;
        if (tracerService != null && tracerService.isTracingEnabled()) {
            tracer = tracerService.getCurrentTracer();
            tracer.startOperation("send-event", 
                "Sending event", action);
        }

        try {
            String eventType = (String) action.getParameterValues().get("eventType");
            Boolean toBePersisted = (Boolean) action.getParameterValues().get("toBePersisted");

            if (tracer != null) {
                Map<String, Object> traceData = new HashMap<>();
                traceData.put("eventType", eventType);
                traceData.put("toBePersisted", toBePersisted);
                traceData.put("hasTarget", action.getParameterValues().get("eventTarget") != null);
                tracer.trace("Preparing event", traceData);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> eventProperties = (Map<String, Object>) action.getParameterValues().get("eventProperties");
            Item target = (Item) action.getParameterValues().get("eventTarget");

            Event subEvent = new Event(eventType, event.getSession(), event.getProfile(), event.getScope(), event, target, event.getTimeStamp());
            subEvent.setProfileId(event.getProfileId());
            subEvent.getAttributes().putAll(event.getAttributes());
            subEvent.getProperties().putAll(eventProperties);
            if (toBePersisted != null && !toBePersisted) {
                subEvent.setPersistent(false);
            }

            int result = eventService.send(subEvent);
            if (tracer != null) {
                Map<String, Object> traceData = new HashMap<>();
                traceData.put("eventId", subEvent.getItemId());
                traceData.put("result", result);
                tracer.trace("Event sent", traceData);
                tracer.endOperation(true, "Event sent successfully");
            }
            return result;
        } catch (Exception e) {
            if (tracer != null) {
                tracer.endOperation(false, "Error sending event: " + e.getMessage());
            }
            throw e;
        }
    }
}
