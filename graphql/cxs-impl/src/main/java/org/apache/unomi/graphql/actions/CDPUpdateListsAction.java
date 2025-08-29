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
package org.apache.unomi.graphql.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.graphql.utils.EventBuilder;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A rule action that can add a profile to a list
 */
@Component(service = ActionExecutor.class, immediate = true, property = {"actionExecutorId=updateLists"})
public class CDPUpdateListsAction implements ActionExecutor {

    private EventService eventService;

    @Reference
    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public int execute(Action action, Event event) {
        List<String> joinLists = (List<String>) event.getProperty("joinLists");
        List<String> leaveLists = (List<String>) event.getProperty("leaveLists");

        final Profile profile = event.getProfile();
        List<String> existingLists = (List<String>) profile.getSystemProperties().get("lists");
        if (existingLists == null) {
            existingLists = new ArrayList<>();
        }
        if (!existingLists.isEmpty() && leaveLists != null && !leaveLists.isEmpty()) {
            existingLists.removeAll(leaveLists);
        }
        if (joinLists != null && !joinLists.isEmpty()) {
            for (String newListIdentifier : joinLists) {
                if (!existingLists.contains(newListIdentifier)) {
                    existingLists.add(newListIdentifier);
                }
            }
        }

        final Map<String, Object> propertyToUpdate = new HashMap<>();
        propertyToUpdate.put("systemProperties.lists", existingLists);

        final Event updatePropertiesEvent = EventBuilder.create("updateProperties", profile)
                .setPropertiesToUpdate(propertyToUpdate)
                .setPersistent(false)
                .build();

        return eventService.send(updatePropertiesEvent);
    }

}
