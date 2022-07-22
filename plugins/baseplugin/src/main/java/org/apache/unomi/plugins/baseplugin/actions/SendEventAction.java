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

import java.util.Map;

public class SendEventAction implements ActionExecutor {

    private EventService eventService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public int execute(Action action, Event event) {
        String eventType = (String) action.getParameterValues().get("eventType");
        Boolean toBePersisted = (Boolean) action.getParameterValues().get("toBePersisted");

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

        return eventService.send(subEvent);
    }
}
