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

import org.apache.unomi.api.ConsentStatus;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.graphql.utils.DateUtils;
import org.apache.unomi.graphql.utils.EventBuilder;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Component(service = ActionExecutor.class, immediate = true, property = {"actionType=updateConsent"})
public class CDPConsentUpdateAction implements ActionExecutor {

    private EventService eventService;

    @Reference
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

        profile.getConsents().forEach((key, consent) -> {
            if (key.endsWith("/" + typeIdentifier)) {
                if (consentStatus != null) {
                    consent.setStatus(ConsentStatus.valueOf(consentStatus));
                }
                if (lastUpdate != null) {
                    consent.setStatusDate(DateUtils.toDate(lastUpdate));
                }
                if (expiration != null) {
                    consent.setRevokeDate(DateUtils.toDate(expiration));
                }
            }
        });

        final Map<String, Object> propertiesToUpdate = new HashMap<>();
        propertiesToUpdate.put("consents", profile.getConsents());

        final Event updatePropertiesEvent = EventBuilder.create("updateProperties", profile)
                .setPropertiesToUpdate(propertiesToUpdate)
                .setPersistent(false)
                .build();

        return eventService.send(updatePropertiesEvent);
    }

}
