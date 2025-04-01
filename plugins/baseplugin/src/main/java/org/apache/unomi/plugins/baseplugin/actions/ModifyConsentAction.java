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
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;

import java.text.ParseException;
import java.util.Map;

/**
 * This class will process consent modification actions and update the profile's consents accordingly.
 */
public class ModifyConsentAction implements ActionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModifyConsentAction.class.getName());

    private TracerService tracerService;

    public static final String CONSENT_PROPERTY_NAME = "consent";

    @Override
    public int execute(Action action, Event event) {
        RequestTracer tracer = null;
        if (tracerService != null && tracerService.isTracingEnabled()) {
            tracer = tracerService.getCurrentTracer();
            tracer.startOperation("modify-consent", 
                "Modifying consent", action);
        }

        try {
            Profile profile = event.getProfile();
            boolean isProfileUpdated = false;

            ISO8601DateFormat dateFormat = new ISO8601DateFormat();
            Map consentMap = (Map) event.getProperties().get(CONSENT_PROPERTY_NAME);
            if (consentMap != null) {
                if (consentMap.containsKey("typeIdentifier") && consentMap.containsKey("status")) {
                    Consent consent = null;
                    try {
                        consent = new Consent(consentMap, dateFormat);
                        isProfileUpdated = profile.setConsent(consent);
                        if (tracer != null) {
                            tracer.trace("Consent modified", Map.of(
                                "typeIdentifier", consent.getTypeIdentifier(),
                                "status", consent.getStatus(),
                                "isUpdated", isProfileUpdated
                            ));
                        }
                    } catch (ParseException e) {
                        if (tracer != null) {
                            tracer.endOperation(false, "Error parsing consent dates: " + e.getMessage());
                        }
                        LOGGER.error("Error parsing consent dates (statusDate or revokeDate). See debug log level to have more information");
                        LOGGER.debug("Error parsing consent dates (statusDate or revokeDate).", e);
                        return EventService.NO_CHANGE;
                    }
                } else {
                    if (tracer != null) {
                        tracer.endOperation(false, "Missing required consent properties");
                    }
                    LOGGER.warn("Event properties for modifyConsent is missing typeIdentifier and grant properties. We will ignore this event.");
                    return EventService.NO_CHANGE;
                }
            }

            if (tracer != null) {
                tracer.endOperation(isProfileUpdated, 
                    isProfileUpdated ? "Consent updated successfully" : "No changes needed");
            }
            return isProfileUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
        } catch (Exception e) {
            if (tracer != null) {
                tracer.endOperation(false, "Error modifying consent: " + e.getMessage());
            }
            throw e;
        }
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }
}
