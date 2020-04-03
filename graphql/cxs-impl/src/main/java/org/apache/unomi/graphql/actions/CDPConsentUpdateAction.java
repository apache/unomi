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

import org.apache.unomi.api.Consent;
import org.apache.unomi.api.ConsentStatus;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.graphql.utils.DateUtils;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Objects;

public class CDPConsentUpdateAction implements ActionExecutor {

    EventService eventService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public int execute(final Action action, final Event event) {
        final String typeIdentifier = (String) event.getProperty("type");
        final String consentStatus = (String) event.getProperty("status");
        final OffsetDateTime lastUpdate = (OffsetDateTime) event.getProperty("lastUpdate");
        final OffsetDateTime expiration = (OffsetDateTime) event.getProperty("expiration");

        final Profile profile = event.getProfile();

        if (profile == null) {
            return EventService.NO_CHANGE;
        }

        if (profile.getConsents() != null && !profile.getConsents().isEmpty()) {
            boolean profileUpdated = false;

            for (final Consent consent : profile.getConsents().values()) {
                if (Objects.equals(typeIdentifier, consent.getTypeIdentifier())) {
                    if (consentStatus != null) {
                        consent.setStatus(ConsentStatus.valueOf(consentStatus));

                        profileUpdated = true;
                    }
                    if (lastUpdate != null) {
                        consent.setStatusDate(DateUtils.toDate(lastUpdate));

                        profileUpdated = true;
                    }
                    if (expiration != null) {
                        consent.setRevokeDate(DateUtils.toDate(expiration));

                        profileUpdated = true;
                    }

                    if (profileUpdated) {
                        return sendEvent(profile, event);
                    }
                }
            }
        }

        return EventService.NO_CHANGE;
    }

    private int sendEvent(final Profile profile, final Event event) {
        final Event profileUpdated =
                new Event("profileUpdated", event.getSession(), profile, event.getScope(), event.getSource(), profile, new Date());

        profileUpdated.setPersistent(false);

        return eventService.send(profileUpdated);
    }

}
