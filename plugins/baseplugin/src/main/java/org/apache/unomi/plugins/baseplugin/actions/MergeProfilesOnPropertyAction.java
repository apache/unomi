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

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.actions.ActionPostExecutor;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MergeProfilesOnPropertyAction implements ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MergeProfilesOnPropertyAction.class.getName());

    private final int MAX_COOKIE_AGE_IN_SECONDS = 60 * 60 * 24 * 365 * 10; // 10-years
    private int cookieAgeInSeconds = MAX_COOKIE_AGE_IN_SECONDS;
    private String profileIdCookieName = "context-profile-id";

    private ProfileService profileService;

    private PersistenceService persistenceService;

    private EventService eventService;

    private DefinitionsService definitionsService;

    public void setCookieAgeInSeconds(int cookieAgeInSeconds) {
        this.cookieAgeInSeconds = cookieAgeInSeconds;
    }

    public void setProfileIdCookieName(String profileIdCookieName) {
        this.profileIdCookieName = profileIdCookieName;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public EventService getEventService() {
        return eventService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public DefinitionsService getDefinitionsService() {
        return definitionsService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public int execute(Action action, Event event) {

        Profile profile = event.getProfile();
        if (profile instanceof Persona) {
            return EventService.NO_CHANGE;
        }

        final String mergeProfilePropertyName = (String) action.getParameterValues().get("mergeProfilePropertyName");
        if (StringUtils.isEmpty(mergeProfilePropertyName)) {
            return EventService.NO_CHANGE;
        }

        final String mergeProfilePropertyValue = (String) action.getParameterValues().get("mergeProfilePropertyValue");
        if (StringUtils.isEmpty(mergeProfilePropertyValue)) {
            return EventService.NO_CHANGE;
        }

        final String mergeProfilePreviousPropertyValue = profile.getSystemProperties().get(mergeProfilePropertyName) != null ? profile.getSystemProperties().get(mergeProfilePropertyName).toString() : "";

        final Session currentSession = event.getSession();

        // store the profile id in case the merge change it to a previous one
        String profileId = profile.getItemId();

        Condition propertyCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        propertyCondition.setParameter("comparisonOperator", "equals");
        propertyCondition.setParameter("propertyName", "systemProperties." + mergeProfilePropertyName);
        propertyCondition.setParameter("propertyValue", mergeProfilePropertyValue);

        Condition excludeMergedProfilesCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        excludeMergedProfilesCondition.setParameter("comparisonOperator", "missing");
        excludeMergedProfilesCondition.setParameter("propertyName", "mergedWith");

        Condition c = new Condition(definitionsService.getConditionType("booleanCondition"));
        c.setParameter("operator", "and");
        c.setParameter("subConditions", Arrays.asList(propertyCondition, excludeMergedProfilesCondition));

        final List<Profile> profiles = persistenceService.query(c, "properties.firstVisit", Profile.class);

        // Check if the user switched to another profile
        if (!StringUtils.isEmpty(mergeProfilePreviousPropertyValue) && !mergeProfilePreviousPropertyValue.equals(mergeProfilePropertyValue)) {
            if (profiles.size() > 0) {
                // Take existing profile
                profile = profiles.get(0);
            } else {
                // Create a new profile
                profile = new Profile(UUID.randomUUID().toString());
                profile.setProperty("firstVisit", currentSession.getTimeStamp());
                profile.getSystemProperties().put(mergeProfilePropertyName, mergeProfilePropertyValue);
            }

            logger.info("Different users, switch to " + profile.getItemId());

            HttpServletResponse httpServletResponse = (HttpServletResponse) event.getAttributes().get(Event.HTTP_RESPONSE_ATTRIBUTE);
            sendProfileCookie(profile, httpServletResponse);

            // At the end of the merge, we must set the merged profile as profile event to process other Actions
            event.setProfileId(profile.getItemId());
            event.setProfile(profile);

            event.getSession().setProfile(profile);

            eventService.send(new Event("sessionReassigned", event.getSession(), profile, event.getScope(), event, event.getSession(), event.getTimeStamp()));

            return EventService.PROFILE_UPDATED + EventService.SESSION_UPDATED;
        } else {
            // Store the merge property identifier in the profile
            profile.getSystemProperties().put(mergeProfilePropertyName, mergeProfilePropertyValue);

            // add current Profile to profiles to be merged
            boolean add = true;
            for (Profile p : profiles) {
                add = add && !StringUtils.equals(p.getItemId(), profile.getItemId());
            }
            if (add) {
                profiles.add(profile);
            }

            if (profiles.size() == 1) {
                return StringUtils.isEmpty(mergeProfilePreviousPropertyValue) ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
            }

            // Use oldest profile for master profile
            Profile masterProfile = profileService.mergeProfiles(profiles.get(0), profiles);

            // Profile has changed
            if (!masterProfile.getItemId().equals(profileId)) {
                HttpServletResponse httpServletResponse = (HttpServletResponse) event.getAttributes().get(Event.HTTP_RESPONSE_ATTRIBUTE);
                sendProfileCookie(event.getSession().getProfile(), httpServletResponse);
                final String masterProfileId = masterProfile.getItemId();

                // At the end of the merge, we must set the merged profile as profile event to process other Actions
                event.setProfileId(masterProfileId);
                event.setProfile(masterProfile);

                event.getSession().setProfile(masterProfile);

                event.getActionPostExecutors().add(new ActionPostExecutor() {
                    @Override
                    public boolean execute() {
                        try {
                            for (Profile profile : profiles) {
                                String profileId = profile.getItemId();
                                if (!StringUtils.equals(profileId, masterProfileId)) {
                                    List<Session> sessions = persistenceService.query("profileId", profileId, null, Session.class);
                                    if (currentSession.getProfileId().equals(profileId) && !sessions.contains(currentSession)) {
                                        sessions.add(currentSession);
                                    }
                                    for (Session session : sessions) {
                                        persistenceService.update(session.getItemId(), session.getTimeStamp(), Session.class, "profileId", masterProfileId);
                                    }

                                    List<Event> events = persistenceService.query("profileId", profileId, null, Event.class);
                                    for (Event event : events) {
                                        persistenceService.update(event.getItemId(), event.getTimeStamp(), Event.class, "profileId", masterProfileId);
                                    }
                                    // we must mark all the profiles that we merged into the master as merged with the master, and they will
                                    // be deleted upon next load
                                    profile.setMergedWith(masterProfileId);
                                    persistenceService.update(profile.getItemId(), null, Profile.class, "mergedWith", masterProfileId);
                                }
                            }
                        } catch (Exception e) {
                            logger.error("unable to execute callback action, profile and session will not be saved", e);
                            return false;
                        }
                        return true;
                    }
                });
                return EventService.PROFILE_UPDATED + EventService.SESSION_UPDATED;
            } else {
                return StringUtils.isEmpty(mergeProfilePreviousPropertyValue) ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
            }
        }
    }

    public void sendProfileCookie(Profile profile, ServletResponse response) {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            Cookie profileIdCookie = new Cookie(profileIdCookieName, profile.getItemId());
            profileIdCookie.setPath("/");
            profileIdCookie.setMaxAge(cookieAgeInSeconds);
            httpServletResponse.addCookie(profileIdCookie);
        }
    }

}
