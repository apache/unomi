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

        Profile eventProfile = event.getProfile();
        final String mergePropName = (String) action.getParameterValues().get("mergeProfilePropertyName");
        final String mergePropValue = (String) action.getParameterValues().get("mergeProfilePropertyValue");
        boolean forceEventProfileAsMaster = action.getParameterValues().containsKey("forceEventProfileAsMaster") ? (boolean) action.getParameterValues().get("forceEventProfileAsMaster") : false;
        final String currentProfileMergeValue = (String) eventProfile.getSystemProperties().get(mergePropName);

        if (eventProfile instanceof Persona || eventProfile.isAnonymousProfile() || StringUtils.isEmpty(mergePropName) ||
                StringUtils.isEmpty(mergePropValue)) {
            return EventService.NO_CHANGE;
        }

        final List<Profile> profilesToBeMerge = getProfilesToBeMerge(mergePropName, mergePropValue);

        // Check if the user switched to another profile
        if (StringUtils.isNotEmpty(currentProfileMergeValue) && !currentProfileMergeValue.equals(mergePropValue)) {
            reassignSession(event, profilesToBeMerge, forceEventProfileAsMaster, mergePropName, mergePropValue);
            return EventService.PROFILE_UPDATED + EventService.SESSION_UPDATED;
        }

        // Store merge prop on current profile
        boolean profileUpdated = false;
        if (StringUtils.isEmpty(currentProfileMergeValue)) {
            profileUpdated = true;
            eventProfile.getSystemProperties().put(mergePropName, mergePropValue);
        }

        // If not profiles to merge we are done here.
        if (profilesToBeMerge.isEmpty()) {
            return profileUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
        }

        // add current Profile to profiles to be merged
        if (profilesToBeMerge.stream().noneMatch(p -> StringUtils.equals(p.getItemId(), eventProfile.getItemId()))) {
            profilesToBeMerge.add(eventProfile);
        }

        final String eventProfileId = eventProfile.getItemId();
        final Profile mergedProfile = profileService.mergeProfiles(forceEventProfileAsMaster ? eventProfile : profilesToBeMerge.get(0), profilesToBeMerge);
        final String mergedProfileId = mergedProfile.getItemId();

        // Profile is still using the same profileId after being merged, no need to rewrite exists data, merge is done
        if (!forceEventProfileAsMaster && mergedProfileId.equals(eventProfileId)) {
            return profileUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
        }

        // ProfileID changed we have a lot to do
        // First check for privacy stuff (inherit from previous profile if necessary)
        if (privacyService.isRequireAnonymousBrowsing(eventProfile)) {
            privacyService.setRequireAnonymousBrowsing(mergedProfileId, true, event.getScope());
        }
        final boolean anonymousBrowsing = privacyService.isRequireAnonymousBrowsing(mergedProfileId);

        // Modify current session:
        if (event.getSession() != null) {
            event.getSession().setProfile(anonymousBrowsing ? privacyService.getAnonymousProfile(mergedProfile) : mergedProfile);
        }

        // Modify current event:
        event.setProfileId(anonymousBrowsing ? null : mergedProfileId);
        event.setProfile(mergedProfile);

        event.getActionPostExecutors().add(() -> {
            try {
                // Save event, as we changed the profileId of the current event
                if (event.isPersistent()) {
                    persistenceService.save(event);
                }

                for (Profile profileToBeMerge : profilesToBeMerge) {
                    String profileToBeMergeId = profileToBeMerge.getItemId();
                    if (!StringUtils.equals(profileToBeMergeId, mergedProfileId)) {

                        // TODO (UNOMI-748): the following updates are asynchron due to usage of bulk processor in ElasticSearch persistence service update function.
                        //  We could consider replacing those updates(one item at a time) by updateByQueryAndScript to avoid loading all the sessions/events in memory,
                        //  but we would loose the asynchronous nature (By doing that request may take longer than before,
                        //  and could potentially break client side timeouts on requests)
                        List<Event> oldEvents = persistenceService.query("profileId", profileToBeMergeId, null, Event.class);
                        for (Event oldEvent : oldEvents) {
                            if (!oldEvent.getItemId().equals(event.getItemId())) {
                                persistenceService.update(oldEvent, Event.class, "profileId", anonymousBrowsing ? null : mergedProfileId);
                            }
                        }

                        // TODO (UNOMI-749): this is creating inconsistent sessions, they still contains old profile.
                        //  And due to deserialization of sessions the profileId property will always be the one from profile stored in the session
                        List<Session> oldSessions = persistenceService.query("profileId", profileToBeMergeId, null, Session.class);
                        for (Session oldSession : oldSessions) {
                            if (!oldSession.getItemId().equals(event.getSession().getItemId())) {
                                persistenceService.update(oldSession, Session.class, "profileId", anonymousBrowsing ? null : mergedProfileId);
                            }
                        }

                        final String clientIdFromEvent = (String) event.getAttributes().get(Event.CLIENT_ID_ATTRIBUTE);
                        String clientId = clientIdFromEvent != null ? clientIdFromEvent : "defaultClientId";
                        profileService.addAliasToProfile(mergedProfileId, profileToBeMergeId, clientId);
                        if (profileService.load(profileToBeMergeId) != null) {
                            profileService.delete(profileToBeMergeId, false);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("unable to execute callback action, profile and session will not be saved", e);
                return false;
            }
            return true;
        });

        return EventService.PROFILE_UPDATED + EventService.SESSION_UPDATED;
    }

    private List<Profile> getProfilesToBeMerge(String mergeProfilePropertyName, String mergeProfilePropertyValue) {
        Condition propertyCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        propertyCondition.setParameter("comparisonOperator", "equals");
        propertyCondition.setParameter("propertyName", "systemProperties." + mergeProfilePropertyName);
        propertyCondition.setParameter("propertyValue", mergeProfilePropertyValue);

        return persistenceService.query(propertyCondition, "properties.firstVisit", Profile.class, 0, maxProfilesInOneMerge).getList();
    }

    private void reassignSession(Event event, List<Profile> existingMergedProfiles, boolean forceEventProfileAsMaster, String mergePropName, String mergePropValue) {
        Profile eventProfile = event.getProfile();

        if (existingMergedProfiles.size() > 0) {
            // Take existing profile
            eventProfile = existingMergedProfiles.get(0);
        } else {
            if (!forceEventProfileAsMaster) {
                // Create a new profile
                eventProfile = new Profile(UUID.randomUUID().toString());
                eventProfile.setProperty("firstVisit", event.getTimeStamp());
            }
            eventProfile.getSystemProperties().put(mergePropName, mergePropValue);
        }

        logger.info("Different users, switch to " + eventProfile.getItemId());
        // At the end of the merge, we must set the merged profile as profile event to process other Actions
        event.setProfileId(eventProfile.getItemId());
        event.setProfile(eventProfile);

        if (event.getSession() != null) {
            Session eventSession = event.getSession();
            eventSession.setProfile(eventProfile);
            eventService.send(new Event("sessionReassigned", eventSession, eventProfile, event.getScope(), event, eventSession,
                    null, event.getTimeStamp(), false));
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