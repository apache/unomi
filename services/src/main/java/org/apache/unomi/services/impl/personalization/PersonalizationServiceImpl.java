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

package org.apache.unomi.services.impl.personalization;

import org.apache.unomi.api.PersonalizationResult;
import org.apache.unomi.api.PersonalizationStrategy;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PersonalizationService;
import org.apache.unomi.api.services.ProfileService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PersonalizationServiceImpl implements PersonalizationService {

    public static final String CONTROL_GROUPS_PROPERTY_NAME = "unomiControlGroups";
    private BundleContext bundleContext;
    private ProfileService profileService;

    private Map<String, PersonalizationStrategy> personalizationStrategies = new ConcurrentHashMap<>();

    private Random controlGroupRandom = new Random();

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void addPersonalizationStrategy(ServiceReference<PersonalizationStrategy> personalizationStrategyRef) {
        PersonalizationStrategy personalizationStrategy = bundleContext.getService(personalizationStrategyRef);
        personalizationStrategies.put(personalizationStrategyRef.getProperty("personalizationStrategyId").toString(), personalizationStrategy);
    }

    public void removePersonalizationStrategy(ServiceReference<PersonalizationStrategy> personalizationStrategyRef) {
        if (personalizationStrategyRef == null) {
            return;
        }
        personalizationStrategies.remove(personalizationStrategyRef.getProperty("personalizationStrategyId").toString());
    }

    @Override
    public boolean filter(Profile profile, Session session, PersonalizedContent personalizedContent) {
        boolean result = true;
        if (personalizedContent.getFilters() != null) {
            for (Filter filter : personalizedContent.getFilters()) {
                Condition condition = filter.getCondition();
                if (condition != null && condition.getConditionTypeId() != null) {
                    result &= profileService.matchCondition(condition, profile, session);
                }
            }
        }
        return result;
    }

    @Override
    public String bestMatch(Profile profile, Session session, PersonalizationRequest personalizationRequest) {
        PersonalizationResult result = personalizeList(profile,session,personalizationRequest);
        if (result.getContentIds().size() > 0) {
            return result.getContentIds().get(0);
        }
        return null;
    }

    @Override
    public PersonalizationResult personalizeList(Profile profile, Session session, PersonalizationRequest personalizationRequest) {
        PersonalizationStrategy strategy = personalizationStrategies.get(personalizationRequest.getStrategy());
        int changeType = EventService.NO_CHANGE;

        if (strategy != null) {
            if (personalizationRequest.getStrategyOptions() != null && personalizationRequest.getStrategyOptions().containsKey("controlGroup")) {
                Map<String,Object> controlGroupMap = (Map<String,Object>) personalizationRequest.getStrategyOptions().get("controlGroup");

                boolean storeInSession = false;
                if (controlGroupMap.containsKey("storeInSession")) {
                    storeInSession = (Boolean) controlGroupMap.get("storeInSession");
                }

                boolean profileInControlGroup = false;
                Optional<ControlGroup> currentControlGroup;

                List<ControlGroup> controlGroups = null;
                if (storeInSession) {
                    if (session.getProperty(CONTROL_GROUPS_PROPERTY_NAME) != null) {
                        controlGroups = ((List<Map<String, Object>>) session.getProperty(CONTROL_GROUPS_PROPERTY_NAME)).stream().map(ControlGroup::fromMap).collect(Collectors.toList());
                    }
                } else {
                    if (profile.getProperty(CONTROL_GROUPS_PROPERTY_NAME) != null) {
                        controlGroups = ((List<Map<String, Object>>) profile.getProperty(CONTROL_GROUPS_PROPERTY_NAME)).stream().map(ControlGroup::fromMap).collect(Collectors.toList());
                    }
                }
                if (controlGroups == null) {
                    controlGroups = new ArrayList<>();
                }
                currentControlGroup = controlGroups.stream().filter(controlGroup -> controlGroup.id.equals(personalizationRequest.getId())).findFirst();
                if (currentControlGroup.isPresent()) {
                    // we already have an entry for this personalization so this means the profile is in the control group
                    profileInControlGroup = true;
                } else {
                    double randomDouble = controlGroupRandom.nextDouble() * 100.0;
                    Object percentageObject = controlGroupMap.get("percentage");
                    Double controlGroupPercentage = null;
                    if (percentageObject != null) {
                        if (percentageObject instanceof Double) {
                            controlGroupPercentage = (Double) percentageObject;
                        } else if (percentageObject instanceof Integer) {
                            controlGroupPercentage = ((Integer) percentageObject).doubleValue();
                        }
                    }

                    if (randomDouble <= controlGroupPercentage) {
                        // Profile is elected to be in control group
                        profileInControlGroup = true;
                        ControlGroup controlGroup = new ControlGroup(personalizationRequest.getId(),
                                (String) controlGroupMap.get("displayName"),
                                (String) controlGroupMap.get("path"),
                                new Date());
                        controlGroups.add(controlGroup);
                        List<Map<String,Object>> controlGroupsMap = controlGroups.stream().map(ControlGroup::toMap).collect(Collectors.toList());
                        if (storeInSession) {
                            session.setProperty(CONTROL_GROUPS_PROPERTY_NAME, controlGroupsMap);
                            changeType = EventService.SESSION_UPDATED;
                        } else {
                            profile.setProperty(CONTROL_GROUPS_PROPERTY_NAME, controlGroupsMap);
                            changeType = EventService.PROFILE_UPDATED;
                        }
                    }
                }
                if (profileInControlGroup) {
                    // if profile is in control group we return the unmodified list.
                    return new PersonalizationResult(personalizationRequest.getContents().stream().map(PersonalizedContent::getId).collect(Collectors.toList()), changeType);
                }
            }
            return new PersonalizationResult(strategy.personalizeList(profile, session, personalizationRequest), changeType);
        }

        throw new IllegalArgumentException("Unknown strategy : "+ personalizationRequest.getStrategy());
    }
}
