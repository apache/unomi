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
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.joda.time.DateTime;
import org.joda.time.Years;

import java.util.Map;

/**
 * An action that sets the age of a profile based on his birth date
 */
public class EvaluateProfileAgeAction implements ActionExecutor {

    private ProfileService profileService;
    private TracerService tracerService;

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    @Override
    public int execute(Action action, Event event) {
        RequestTracer tracer = null;
        if (tracerService != null && tracerService.isTracingEnabled()) {
            tracer = tracerService.getCurrentTracer();
            tracer.startOperation("evaluate-age",
                "Evaluating profile age", action);
        }

        try {
            if (event.getProfile() == null) {
                if (tracer != null) {
                    tracer.endOperation(false, "No profile in event");
                }
                return EventService.NO_CHANGE;
            }

            boolean updated = false;
            if (event.getProfile().getProperty("birthDate") != null) {
                Integer y = Years.yearsBetween(new DateTime(event.getProfile().getProperty("birthDate")), new DateTime()).getYears();
                Integer currentAge = (Integer) event.getProfile().getProperty("age");
                if (currentAge == null || !currentAge.equals(y)) {
                    updated = true;
                    event.getProfile().setProperty("age", y);
                    if (tracer != null) {
                        tracer.trace("Age updated", Map.of(
                            "birthDate", event.getProfile().getProperty("birthDate"),
                            "newAge", y
                        ));
                    }
                }
            } else {
                if (tracer != null) {
                    tracer.trace("No birth date found", Map.of());
                }
            }

            // If this action was triggered by a profileUpdated event, save the profile
            // but don't return PROFILE_UPDATED to prevent loops
            if (updated && "profileUpdated".equals(event.getEventType()) && profileService != null) {
                profileService.save(event.getProfile());
                if (tracer != null) {
                    tracer.trace("Profile saved after age evaluation (preventing loop)", Map.of(
                        "newAge", event.getProfile().getProperty("age")
                    ));
                    tracer.endOperation(true, "Profile age updated and saved (loop prevented)");
                }
                return EventService.NO_CHANGE;
            }

            if (tracer != null) {
                tracer.endOperation(updated,
                    updated ? "Profile age updated" : "No changes needed");
            }
            return updated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
        } catch (Exception e) {
            if (tracer != null) {
                tracer.endOperation(false, "Error evaluating age: " + e.getMessage());
            }
            throw e;
        }
    }
}
