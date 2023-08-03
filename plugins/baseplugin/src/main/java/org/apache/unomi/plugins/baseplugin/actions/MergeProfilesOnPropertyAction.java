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
import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.*;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MergeProfilesOnPropertyAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(MergeProfilesOnPropertyAction.class.getName());

    private ProfileService profileService;
    private PersistenceService persistenceService;
    private EventService eventService;
    private DefinitionsService definitionsService;
    private PrivacyService privacyService;
    private ConfigSharingService configSharingService;
    private SchedulerService schedulerService;
    // TODO we can remove this limit after dealing with: UNOMI-776 (50 is completely arbitrary and it's used to bypass the auto-scroll done by the persistence Service)
    private int maxProfilesInOneMerge = 50;

    public int execute(Action action, Event event) {
        HttpServletResponse httpServletResponse = (HttpServletResponse) event.getAttributes().get(Event.HTTP_RESPONSE_ATTRIBUTE);
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get(Event.HTTP_REQUEST_ATTRIBUTE);

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
            String reassignProfileId = reassignCurrentBrowsingData(event, profilesToBeMerge, forceEventProfileAsMaster, mergePropName, mergePropValue);
            sendProfileCookie(reassignProfileId, httpServletResponse, httpServletRequest);

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
        final Profile masterProfile = profileService.mergeProfiles(forceEventProfileAsMaster ? eventProfile : profilesToBeMerge.get(0), profilesToBeMerge);
        final String masterProfileId = masterProfile.getItemId();

        // Profile is still using the same profileId after being merged, no need to rewrite exists data, merge is done
        if (!forceEventProfileAsMaster && masterProfileId.equals(eventProfileId)) {
            return profileUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
        }

        // ProfileID changed we have a lot to do
        // First check for privacy stuff (inherit from previous profile if necessary)
        if (privacyService.isRequireAnonymousBrowsing(eventProfile)) {
            privacyService.setRequireAnonymousBrowsing(masterProfileId, true, event.getScope());
        }
        final boolean anonymousBrowsing = privacyService.isRequireAnonymousBrowsing(masterProfileId);

        // we still send back the current profile cookie. It will be changed on the next request to the ContextServlet.
        // The current profile will be deleted only then because we cannot delete it right now (too soon)
        sendProfileCookie(eventProfileId, httpServletResponse, httpServletRequest);

        // Modify current session:
        if (event.getSession() != null) {
            event.getSession().setProfile(anonymousBrowsing ? privacyService.getAnonymousProfile(masterProfile) : masterProfile);
        }

        // Modify current event:
        event.setProfileId(anonymousBrowsing ? null : masterProfileId);
        event.setProfile(masterProfile);

        event.getActionPostExecutors().add(() -> {
            try {
                // This is the list of profile Ids to be updated in browsing data (events/sessions)
                List<Profile> mergedProfiles = profilesToBeMerge.stream()
                        .filter(mergedProfile -> !StringUtils.equals(mergedProfile.getItemId(), masterProfileId))
                        .collect(Collectors.toList());

                // ASYNC: Update browsing data (events/sessions) for merged profiles
                reassignPersistedBrowsingDatasAsync(anonymousBrowsing, mergedProfiles.stream().map(Item::getItemId).collect(Collectors.toList()), masterProfileId);

                // Save event, as we dynamically changed the profileId of the current event
                if (event.isPersistent()) {
                    persistenceService.save(event);
                }

                // we must mark all the profiles that we merged into the master as merged with the master, and they will
                // be deleted upon next load
                for (Profile mergedProfile : mergedProfiles) {
                    mergedProfile.setMergedWith(masterProfileId);
                    mergedProfile.setSystemProperty("lastUpdated", new Date());

                    boolean isExist  = persistenceService.load(mergedProfile.getItemId(), Profile.class) != null;
                    if (!isExist) {
                        //save the original event profile is it has been changed
                        persistenceService.save(mergedProfile);
                    } else {
                        Map<String,Object> sourceMap = new HashMap<>();
                        sourceMap.put("mergedWith", masterProfileId);
                        sourceMap.put("systemProperties", mergedProfile.getSystemProperties());
                        persistenceService.update(mergedProfile, null, Profile.class, sourceMap,true);
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

    private void sendProfileCookie(String profileId, HttpServletResponse response, HttpServletRequest request) {
        if (response != null && request != null) {
            String profileIdCookieName = (String) configSharingService.getProperty("profileIdCookieName");
            String profileIdCookieDomain = (String) configSharingService.getProperty("profileIdCookieDomain");
            Integer profileIdCookieMaxAgeInSeconds = (Integer) configSharingService.getProperty("profileIdCookieMaxAgeInSeconds");
            Boolean profileIdCookieHttpOnly = (Boolean) configSharingService.getProperty("profileIdCookieHttpOnly");

            response.addHeader("Set-Cookie",
                    profileIdCookieName + "=" + profileId +
                            "; Path=/" +
                            "; Max-Age=" + profileIdCookieMaxAgeInSeconds +
                            (StringUtils.isNotBlank(profileIdCookieDomain) ? ("; Domain=" + profileIdCookieDomain) : "")  +
                            "; SameSite=Lax" +
                            (request.isSecure() ? "; Secure" : "") +
                            (profileIdCookieHttpOnly ? "; HttpOnly" : ""));
        }
    }

    private List<Profile> getProfilesToBeMerge(String mergeProfilePropertyName, String mergeProfilePropertyValue) {
        Condition propertyCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        propertyCondition.setParameter("comparisonOperator", "equals");
        propertyCondition.setParameter("propertyName", "systemProperties." + mergeProfilePropertyName);
        propertyCondition.setParameter("propertyValue", mergeProfilePropertyValue);

        return persistenceService.query(propertyCondition, "properties.firstVisit", Profile.class, 0, maxProfilesInOneMerge).getList();
    }

    private void reassignPersistedBrowsingDatasAsync(boolean anonymousBrowsing, List<String> mergedProfileIds, String masterProfileId) {
        schedulerService.getSharedScheduleExecutorService().schedule(new TimerTask() {
            @Override
            public void run() {
                if (!anonymousBrowsing) {
                    Condition profileIdsCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
                    profileIdsCondition.setParameter("propertyName","profileId");
                    profileIdsCondition.setParameter("comparisonOperator","in");
                    profileIdsCondition.setParameter("propertyValues", mergedProfileIds);

                    String[] scripts = new String[]{"updateProfileId"};
                    Map<String, Object>[] scriptParams = new Map[]{Collections.singletonMap("profileId", masterProfileId)};
                    Condition[] conditions = new Condition[]{profileIdsCondition};

                    persistenceService.updateWithQueryAndStoredScript(null, new Class[]{Session.class, Event.class}, scripts, scriptParams, conditions, false);
                } else {
                    for (String mergedProfileId : mergedProfileIds) {
                        privacyService.anonymizeBrowsingData(mergedProfileId);
                    }
                }
            }
        }, 1000, TimeUnit.MILLISECONDS);
    }

    private String reassignCurrentBrowsingData(Event event, List<Profile> existingMergedProfiles, boolean forceEventProfileAsMaster, String mergePropName, String mergePropValue) {
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

        logger.info("Different users, switch to {}", eventProfile.getItemId());
        // At the end of the merge, we must set the merged profile as profile event to process other Actions
        event.setProfileId(eventProfile.getItemId());
        event.setProfile(eventProfile);

        if (event.getSession() != null) {
            Session eventSession = event.getSession();
            eventSession.setProfile(eventProfile);
            eventService.send(new Event("sessionReassigned", eventSession, eventProfile, event.getScope(), event, eventSession,
                    null, event.getTimeStamp(), false));
        }

        return eventProfile.getItemId();
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

    public void setConfigSharingService(ConfigSharingService configSharingService) {
        this.configSharingService = configSharingService;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setMaxProfilesInOneMerge(String maxProfilesInOneMerge) {
        this.maxProfilesInOneMerge = Integer.parseInt(maxProfilesInOneMerge);
    }
}