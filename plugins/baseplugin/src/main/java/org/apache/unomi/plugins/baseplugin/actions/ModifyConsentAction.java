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

import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import org.apache.unomi.api.Consent;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Map;

/**
 * This class will process consent modification actions and update the profile's consents accordingly.
 */
public class ModifyConsentAction implements ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ModifyConsentAction.class.getName());

    public static final String CONSENT_PROPERTY_NAME = "consent";

    @Override
    public int execute(Action action, Event event) {
        Profile profile = event.getProfile();
        boolean isProfileUpdated = false;

        ISO8601DateFormat dateFormat = new ISO8601DateFormat();
        Map consentMap = (Map) event.getProperties().get(CONSENT_PROPERTY_NAME);
        if (consentMap != null) {
            if (consentMap.containsKey("typeIdentifier") && consentMap.containsKey("grant")) {
                Consent consent = null;
                try {
                    consent = new Consent(consentMap, dateFormat);
                    isProfileUpdated = profile.setConsent(consent);
                } catch (ParseException e) {
                    logger.error("Error parsing date format", e);
                }
            } else {
                logger.warn("Event properties for modifyConsent is missing typeIdentifier and grant properties. We will ignore this event.");
            }
        }
        return isProfileUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
    }
}
