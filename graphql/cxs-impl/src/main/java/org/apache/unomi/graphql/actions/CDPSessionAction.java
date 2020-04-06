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
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.types.output.CDPSessionState;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

public class CDPSessionAction implements ActionExecutor {

    private EventService eventService;

    private ProfileService profileService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public int execute(Action action, Event event) {
        final CDPSessionState state = CDPSessionState.valueOf((String) event.getProperty("state"));
        final String sessionId = (String) event.getProperty("sessionId");
        final String scope = (String) event.getProperty("scope");

        final Profile profile = event.getProfile();

        if (profile == null) {
            return EventService.NO_CHANGE;
        }

        if (state == CDPSessionState.START) {
            final Session session = new Session(sessionId, profile, new Date(), scope);
            session.setProperty("state", state);

            final Event sessionCreated =
                    new Event("sessionCreated", session, profile, scope, null, session, event.getTimeStamp());

            int eventCode = eventService.send(sessionCreated);

            profileService.saveSession(session);

            return eventCode;
        } else {
            final PartialList<Session> sessionList = profileService.findProfileSessions(profile.getItemId());

            if (sessionList != null) {
                final Optional<Session> sessionOp = sessionList.getList().stream()
                        .filter(session -> Objects.equals(session.getItemId(), sessionId) && Objects.equals(session.getScope(), scope))
                        .findFirst();

                if (sessionOp.isPresent()) {
                    final Session sessionToUpdate = sessionOp.get();

                    sessionToUpdate.setProperty("state", state);
                    profileService.saveSession(sessionToUpdate);

                    return EventService.SESSION_UPDATED;
                }
            }
        }

        return EventService.NO_CHANGE;
    }

}
