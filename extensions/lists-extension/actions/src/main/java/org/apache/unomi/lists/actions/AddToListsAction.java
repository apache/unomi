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
package org.apache.unomi.lists.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;

import java.util.Date;
import java.util.List;

/**
 * A rule action that can add a profile to a list
 */
public class AddToListsAction implements ActionExecutor {

    ProfileService profileService;
    EventService eventService;

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public int execute(Action action, Event event) {
        List<String> listIdentifiers = (List<String>) action.getParameterValues().get("listIdentifiers");
        Profile profile = event.getProfile();

        profile.getSystemProperties().put("lists", listIdentifiers);
        Event profileUpdated = new Event("profileUpdated", null, profile, event.getScope(), null, profile, new Date());
        profileUpdated.setPersistent(false);
        eventService.send(profileUpdated);
        profileService.save(profile);

        return EventService.PROFILE_UPDATED;
    }
}
