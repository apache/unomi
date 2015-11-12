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

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.EventService;
import java.util.Map;

public class SendEventAction implements ActionExecutor {

    private EventService eventService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public int execute(Action action, Event event) {
        String eventType = (String) action.getParameterValues().get("eventType");
        @SuppressWarnings("unchecked")
        Map<String, Object> eventProperties = (Map<String, Object>) action.getParameterValues().get("eventProperties");
        Item target = (Item) action.getParameterValues().get("eventTarget");
//        String type = (String) target.get("type");

//            Item targetItem = new CustomItem();
//            BeanUtils.populate(targetItem, target);

        Event subEvent = new Event(eventType, event.getSession(), event.getProfile(), event.getScope(), event, target, event.getTimeStamp());
        subEvent.getAttributes().putAll(event.getAttributes());
        subEvent.getProperties().putAll(eventProperties);

        return eventService.send(subEvent);
    }
}
