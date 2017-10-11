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

/**
 * A action to copy an event property to a profile property
 */
public class EventToProfilePropertyAction implements ActionExecutor {

    public int execute(Action action, Event event) {
        String eventPropertyName = (String) action.getParameterValues().get("eventPropertyName");
        String profilePropertyName = (String) action.getParameterValues().get("profilePropertyName");

        if (event.getProfile().getProperty(profilePropertyName) == null || !event.getProfile().getProperty(profilePropertyName).equals(event.getProperty(eventPropertyName))) {
            event.getProfile().setProperty(profilePropertyName, event.getProperty(eventPropertyName));
            return EventService.PROFILE_UPDATED;
        }
        return EventService.NO_CHANGE;
    }
}
