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
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.*;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.api.services.ExecutionContextManager;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MergeProfilesOnPropertyAction implements ActionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MergeProfilesOnPropertyAction.class.getName());

    private ProfileService profileService;
    private PersistenceService persistenceService;
    private EventService eventService;
    private DefinitionsService definitionsService;
    private PrivacyService privacyService;
    private SchedulerService schedulerService;
    private TracerService tracerService;
    private ExecutionContextManager executionContextManager;
    private SecurityService securityService;
    // TODO we can remove this limit after dealing with: UNOMI-776 (50 is completely arbitrary and it's used to bypass the auto-scroll done by the persistence Service)
    private int maxProfilesInOneMerge = 50;

    public int execute(Action action, Event event) {
        RequestTracer tracer = null;
        if (tracerService != null && tracerService.isTracingEnabled()) {
            tracer = tracerService.getCurrentTracer();
            tracer.startOperation("merge-profiles",
                "Starting profile merge operation", action);
        }

        try {
            Profile eventProfile = event.getProfile();
            final String mergePropName = (String) action.getParameterValues().get("mergeProfilePropertyName");
            final String mergePropValue = (String) action.getParameterValues().get("mergeProfilePropertyValue");
            final String clientIdFromEvent = (String) event.getAttributes().get(Event.CLIENT_ID_ATTRIBUTE);
            final String clientId = clientIdFromEvent != null ? clientIdFromEvent : "defaultClientId";
            boolean forceEventProfileAsMaster = action.getParameterValues().containsKey("forceEventProfileAsMaster") ? (boolean) action.getParameterValues().get("forceEventProfileAsMaster") : false;
            final String currentProfileMergeValue = (String) eventProfile.getSystemProperties().get(mergePropName);

            if (eventProfile instanceof Persona || eventProfile.isAnonymousProfile() || StringUtils.isEmpty(mergePropName) ||
                    StringUtils.isEmpty(mergePropValue)) {
                if (tracer != null) {
                    tracer.endOperation(false, "Invalid profile or missing merge parameters");
                }
                return EventService.NO_CHANGE;
            }

            final List<Profile> profilesToBeMerge = getProfilesToBeMerge(mergePropName, mergePropValue);

            if (tracer != null) {
                tracer.trace("Found profiles to merge", Map.of(
                    "count", profilesToBeMerge.size(),
                    "profileIds", profilesToBeMerge.stream().map(Profile::getItemId).collect(Collectors.toList())
                ));
            }

            // Check if the user switched to another profile
            if (StringUtils.isNotEmpty(currentProfileMergeValue) && !currentProfileMergeValue.equals(mergePropValue)) {
                if (tracer != null) {
                    tracer.trace("Profile switch detected", Map.of(
                        "fromValue", currentProfileMergeValue,
                        "toValue", mergePropValue
                    ));
                }
                reassignCurrentBrowsingData(event, profilesToBeMerge, forceEventProfileAsMaster, mergePropName, mergePropValue);
                if (tracer != null) {
                    tracer.endOperation(true, "Profile switch completed");
                }
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
                if (tracer != null) {
                    tracer.endOperation(profileUpdated, profileUpdated ? "Profile updated but no merges needed" : "No changes needed");
                }
                return profileUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
            }

            // add current Profile to profiles to be merged
            if (profilesToBeMerge.stream().noneMatch(p -> StringUtils.equals(p.getItemId(), eventProfile.getItemId()))) {
                profilesToBeMerge.add(eventProfile);
            }

            final String eventProfileId = eventProfile.getItemId();
            final Profile masterProfile = profileService.mergeProfiles(forceEventProfileAsMaster ? eventProfile : profilesToBeMerge.get(0), profilesToBeMerge);
            final String masterProfileId = masterProfile.getItemId();

            if (tracer != null) {
                tracer.trace("Profile merge completed", Map.of(
                    "masterProfileId", masterProfileId,
                    "originalProfileId", eventProfileId
                ));
            }

            // Profile is still using the same profileId after being merged, no need to rewrite exists data, merge is done
            if (!forceEventProfileAsMaster && masterProfileId.equals(eventProfileId)) {
                if (tracer != null) {
                    tracer.endOperation(profileUpdated, "Profile merge completed with same ID");
                }
                return profileUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
            }

            // ProfileID changed we have a lot to do
            // First check for privacy stuff (inherit from previous profile if necessary)
            if (privacyService.isRequireAnonymousBrowsing(eventProfile)) {
                privacyService.setRequireAnonymousBrowsing(masterProfileId, true, event.getScope());
            }
            final boolean anonymousBrowsing = privacyService.isRequireAnonymousBrowsing(masterProfileId);

            // Modify current session:
            if (event.getSession() != null) {
                event.getSession().setProfile(anonymousBrowsing ? privacyService.getAnonymousProfile(masterProfile) : masterProfile);
            }

            // Modify current event:
            event.setProfileId(anonymousBrowsing ? null : masterProfileId);
            event.setProfile(masterProfile);

            final RequestTracer finalTracer = tracer;
            event.getActionPostExecutors().add(() -> {
                try {
                    // This is the list of profile Ids to be updated in browsing data (events/sessions)
                    List<String> mergedProfileIds = profilesToBeMerge.stream()
                            .map(Profile::getItemId)
                            .filter(mergedProfileId -> !StringUtils.equals(mergedProfileId, masterProfileId))
                            .collect(Collectors.toList());

                    // Get current tenant ID from execution context
                    String currentTenantId = executionContextManager.getCurrentContext() != null ?
                        executionContextManager.getCurrentContext().getTenantId() : "system";

                    // ASYNC: Update browsing data (events/sessions) for merged profiles
                    reassignPersistedBrowsingDatasAsync(anonymousBrowsing, mergedProfileIds, masterProfileId, currentTenantId);

                    // Save event, as we dynamically changed the profileId of the current event
                    if (event.isPersistent()) {
                        persistenceService.save(event);
                    }

                    // Handle aliases
                    for (String mergedProfileId : mergedProfileIds) {
                        profileService.addAliasToProfile(masterProfileId, mergedProfileId, clientId);
                        if (persistenceService.load(mergedProfileId, Profile.class) != null) {
                            profileService.delete(mergedProfileId, false);
                        }
                    }

                    if (finalTracer != null) {
                        finalTracer.trace("Post-merge cleanup completed", Map.of(
                            "mergedProfileIds", mergedProfileIds,
                            "masterProfileId", masterProfileId
                        ));
                    }
                } catch (Exception e) {
                    LOGGER.error("unable to execute callback action, profile and session will not be saved", e);
                    if (finalTracer != null) {
                        finalTracer.endOperation(false, "Error during post-execution: " + e.getMessage());
                    }
                    return false;
                }
                return true;
            });

            if (tracer != null) {
                tracer.endOperation(true, "Profile merge completed successfully");
            }
            return EventService.PROFILE_UPDATED + EventService.SESSION_UPDATED;
        } catch (Exception e) {
            if (tracer != null) {
                tracer.endOperation(false, "Error during profile merge: " + e.getMessage());
            }
            throw e;
        }
    }

    private List<Profile> getProfilesToBeMerge(String mergeProfilePropertyName, String mergeProfilePropertyValue) {
        Condition propertyCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        propertyCondition.setParameter("comparisonOperator", "equals");
        propertyCondition.setParameter("propertyName", "systemProperties." + mergeProfilePropertyName);
        propertyCondition.setParameter("propertyValue", mergeProfilePropertyValue);

        return persistenceService.query(propertyCondition, "properties.firstVisit", Profile.class, 0, maxProfilesInOneMerge).getList();
    }

    private void reassignPersistedBrowsingDatasAsync(boolean anonymousBrowsing, List<String> mergedProfileIds, String masterProfileId, String tenantId) {
        // Register task executor for data reassignment
        String taskType = "merge-profiles-reassign-data";

        // Create a reusable executor that can handle the parameters
        TaskExecutor mergeProfilesReassignDataExecutor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return taskType;
            }

            @Override
            public void execute(ScheduledTask task, TaskExecutor.TaskStatusCallback callback) {
                try {
                    Map<String, Object> parameters = task.getParameters();
                    boolean isAnonymousBrowsing = (boolean) parameters.get("anonymousBrowsing");
                    @SuppressWarnings("unchecked")
                    List<String> profilesIds = (List<String>) parameters.get("mergedProfileIds");
                    String masterProfile = (String) parameters.get("masterProfileId");
                    String tenantId = (String) parameters.get("tenantId");

                    securityService.setCurrentSubject(securityService.createSubject(tenantId, true));

                    // Execute the merge operation in the correct tenant context
                    executionContextManager.executeAsTenant(tenantId, () -> {
                        if (!isAnonymousBrowsing) {
                            Condition profileIdsCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
                            profileIdsCondition.setParameter("propertyName","profileId");
                            profileIdsCondition.setParameter("comparisonOperator","in");
                            profileIdsCondition.setParameter("propertyValues", profilesIds);

                            String[] scripts = new String[]{"updateProfileId"};
                            Map<String, Object>[] scriptParams = new Map[]{Collections.singletonMap("profileId", masterProfile)};
                            Condition[] conditions = new Condition[]{profileIdsCondition};

                            persistenceService.updateWithQueryAndStoredScript(new Class[]{Session.class, Event.class}, scripts, scriptParams, conditions, false);
                        } else {
                            for (String mergedProfileId : profilesIds) {
                                privacyService.anonymizeBrowsingData(mergedProfileId);
                            }
                        }
                        return null;
                    });

                    callback.complete();
                } catch (Exception e) {
                    LOGGER.error("Error while reassigning profile data", e);
                    callback.fail(e.getMessage());
                }
            }
        };

        // Register the executor
        schedulerService.registerTaskExecutor(mergeProfilesReassignDataExecutor);

        // Create a one-shot task for async data reassignment
        schedulerService.newTask(taskType)
            .withParameters(Map.of(
                "anonymousBrowsing", anonymousBrowsing,
                "mergedProfileIds", mergedProfileIds,
                "masterProfileId", masterProfileId,
                    "tenantId", tenantId
            ))
            .withInitialDelay(1000, TimeUnit.MILLISECONDS)
            .asOneShot()
            .schedule();
    }

    private void reassignCurrentBrowsingData(Event event, List<Profile> existingMergedProfiles, boolean forceEventProfileAsMaster, String mergePropName, String mergePropValue) {
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

        LOGGER.info("Different users, switch to {}", eventProfile.getItemId());
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

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setMaxProfilesInOneMerge(String maxProfilesInOneMerge) {
        this.maxProfilesInOneMerge = Integer.parseInt(maxProfilesInOneMerge);
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    public void bindExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }

    public void unbindExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = null;
    }

    public void bindSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void unbindSecurityService(SecurityService securityService) {
        this.securityService = null;
    }
}
