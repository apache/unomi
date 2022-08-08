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
import org.apache.unomi.api.services.*;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class MergeProfilesOnPropertyAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(MergeProfilesOnPropertyAction.class.getName());

    private ProfileService profileService;
    private PersistenceService persistenceService;
    private EventService eventService;
    private DefinitionsService definitionsService;
    private PrivacyService privacyService;
    private int maxProfilesInOneMerge = -1;

    public int execute(Action action, Event event) {

        Profile profile = event.getProfile();
        if (profile instanceof Persona || profile.isAnonymousProfile()) {
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

        boolean forceEventProfileAsMaster = action.getParameterValues().containsKey("forceEventProfileAsMaster") ?
                (boolean) action.getParameterValues().get("forceEventProfileAsMaster") : false;

        // store the profile id in case the merge change it to a previous one
        String profileId = profile.getItemId();

        Condition propertyCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        propertyCondition.setParameter("comparisonOperator", "equals");
        propertyCondition.setParameter("propertyName", "systemProperties." + mergeProfilePropertyName);
        propertyCondition.setParameter("propertyValue", mergeProfilePropertyValue);

        final List<Profile> profiles = persistenceService.query(propertyCondition, "properties.firstVisit", Profile.class, 0, maxProfilesInOneMerge).getList();

        // Check if the user switched to another profile
        if (StringUtils.isNotEmpty(mergeProfilePreviousPropertyValue) && !mergeProfilePreviousPropertyValue.equals(mergeProfilePropertyValue)) {
            if (profiles.size() > 0) {
                // Take existing profile
                profile = profiles.get(0);
            } else {
                if (forceEventProfileAsMaster) {
                    profile = event.getProfile();
                } else {
                    // Create a new profile
                    profile = new Profile(UUID.randomUUID().toString());
                    profile.setProperty("firstVisit", event.getTimeStamp());
                }
                profile.getSystemProperties().put(mergeProfilePropertyName, mergeProfilePropertyValue);
            }

            logger.info("Different users, switch to " + profile.getItemId());
            // At the end of the merge, we must set the merged profile as profile event to process other Actions
            event.setProfileId(profile.getItemId());
            event.setProfile(profile);

            if (currentSession != null) {
                currentSession.setProfile(profile);
                eventService.send(new Event("sessionReassigned", currentSession, profile, event.getScope(), event, currentSession,
                        null, event.getTimeStamp(), false));
            }

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

            Profile markedMasterProfile;
            if (forceEventProfileAsMaster)
                markedMasterProfile = event.getProfile();
            else
                markedMasterProfile = profiles.get(0);// Use oldest profile for master profile

            final Profile masterProfile = profileService.mergeProfiles(markedMasterProfile, profiles);

            // Profile has changed
            if (forceEventProfileAsMaster || !masterProfile.getItemId().equals(profileId)) {

                final String masterProfileId = masterProfile.getItemId();
                // At the end of the merge, we must set the merged profile as profile event to process other Actions
                event.setProfileId(masterProfileId);
                event.setProfile(masterProfile);

                final Boolean anonymousBrowsing = privacyService.isRequireAnonymousBrowsing(masterProfileId);

                if (currentSession != null) {
                    currentSession.setProfile(masterProfile);
                    if (privacyService.isRequireAnonymousBrowsing(profile)) {
                        privacyService.setRequireAnonymousBrowsing(masterProfileId, true, event.getScope());
                    }

                    if (anonymousBrowsing) {
                        currentSession.setProfile(privacyService.getAnonymousProfile(masterProfile));
                        event.setProfileId(null);
                        persistenceService.save(event);
                    }
                }

                event.getActionPostExecutors().add(new ActionPostExecutor() {
                    @Override
                    public boolean execute() {
                        try {
                            Event currentEvent = event;
                            // Update current event explicitly, as it might not return from search query if there wasn't a refresh in ES
                            if (!StringUtils.equals(profileId, masterProfileId)) {
                                if (currentEvent.isPersistent()) {
                                    persistenceService.update(currentEvent, currentEvent.getTimeStamp(), Event.class, "profileId", anonymousBrowsing ? null : masterProfileId);
                                }
                            }

                            for (Profile profile : profiles) {
                                String profileId = profile.getItemId();
                                if (!StringUtils.equals(profileId, masterProfileId)) {
                                    List<Session> sessions = persistenceService.query("profileId", profileId, null, Session.class);
                                    if (currentSession != null) {
                                        if (masterProfileId.equals(profileId) && !sessions.contains(currentSession)) {
                                            sessions.add(currentSession);
                                        }
                                    }

                                    for (Session session : sessions) {
                                        persistenceService.update(session, session.getTimeStamp(), Session.class, "profileId", anonymousBrowsing ? null : masterProfileId);
                                    }

                                    List<Event> events = persistenceService.query("profileId", profileId, null, Event.class);
                                    for (Event event : events) {
                                        if (!event.getItemId().equals(currentEvent.getItemId())) {
                                            persistenceService.update(event, event.getTimeStamp(), Event.class, "profileId", anonymousBrowsing ? null : masterProfileId);
                                        }
                                    }

                                    final String clientIdFromEvent = (String) event.getAttributes().get(Event.CLIENT_ID_ATTRIBUTE);
                                    String clientId = clientIdFromEvent != null ? clientIdFromEvent : "defaultClientId";
                                    profileService.addAliasToProfile(masterProfileId, profile.getItemId(), clientId);

                                    boolean isExist = profileService.load(profile.getItemId()) != null;
                                    if (isExist) {
                                        profileService.delete(profileId, false);
                                    }
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

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setPrivacyService(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setMaxProfilesInOneMerge(String maxProfilesInOneMerge) {
        this.maxProfilesInOneMerge = Integer.parseInt(maxProfilesInOneMerge);
    }
}
