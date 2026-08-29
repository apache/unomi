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
package org.apache.unomi.services.sorts;

import org.apache.unomi.api.*;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PersonalizationService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * System strategy to calculate control group on personalization that would use such configuration
 * The status is then stored in the current profile/session under: systemProperties.personalizationStrategyStatus
 * A control group is used with a percentage to decide if the current profile/session should have personalized results or not.
 * - in case of control group is true: the variants will be return untouched like received and an information will be added to the personalized results to warn the client
 * - in case of control group is false: we will execute the personalization normally
 */
public class ControlGroupPersonalizationStrategy implements PersonalizationStrategy {
    public static final String PERSONALIZATION_STRATEGY_STATUS = "personalizationStrategyStatus";
    public static final String PERSONALIZATION_STRATEGY_STATUS_ID = "personalizationId";
    public static final String PERSONALIZATION_STRATEGY_STATUS_IN_CTRL_GROUP = "inControlGroup";
    public static final String PERSONALIZATION_STRATEGY_STATUS_DATE = "timeStamp";

    public static final String CONTROL_GROUP_CONFIG_STORE_IN_SESSION = "storeInSession";
    public static final String CONTROL_GROUP_CONFIG_PERCENTAGE = "percentage";
    public static final String CONTROL_GROUP_CONFIG = "controlGroup";

    private final Random controlGroupRandom = new Random();

    @Override
    public PersonalizationResult personalizeList(Profile profile, Session session, PersonalizationService.PersonalizationRequest personalizationRequest) {
        if (personalizationRequest.getStrategyOptions() != null && personalizationRequest.getStrategyOptions().containsKey(CONTROL_GROUP_CONFIG)) {
            Map<String, Object> controlGroupMap = (Map<String, Object>) personalizationRequest.getStrategyOptions().get(CONTROL_GROUP_CONFIG);

            return controlGroupMap.containsKey(CONTROL_GROUP_CONFIG_STORE_IN_SESSION) &&
                    controlGroupMap.get(CONTROL_GROUP_CONFIG_STORE_IN_SESSION) instanceof Boolean &&
                    ((Boolean) controlGroupMap.get(CONTROL_GROUP_CONFIG_STORE_IN_SESSION)) ?
                    getPersonalizationResultForControlGroup(personalizationRequest, session, controlGroupMap, EventService.SESSION_UPDATED) :
                    getPersonalizationResultForControlGroup(personalizationRequest, profile, controlGroupMap, EventService.PROFILE_UPDATED);
        }

        throw new IllegalArgumentException("Not possible to perform control group strategy without control group config");
    }

    private PersonalizationResult getPersonalizationResultForControlGroup(PersonalizationService.PersonalizationRequest personalizationRequest, SystemPropertiesItem systemPropertiesItem, Map<String, Object> controlGroupConfig, int changeType) {
        // Control group will return the same untouched list of received content ids
        PersonalizationResult personalizationResult = new PersonalizationResult(
                personalizationRequest.getContents().stream().map(PersonalizationService.PersonalizedContent::getId).collect(Collectors.toList()));

        // get the list of existing personalization strategy status
        List<Map<String, Object>> strategyStatuses;
        if (systemPropertiesItem.getSystemProperties().get(PERSONALIZATION_STRATEGY_STATUS) == null) {
            strategyStatuses = new ArrayList<>();
            systemPropertiesItem.getSystemProperties().put(PERSONALIZATION_STRATEGY_STATUS, strategyStatuses);
        } else {
            strategyStatuses = (List<Map<String, Object>>) systemPropertiesItem.getSystemProperties().get(PERSONALIZATION_STRATEGY_STATUS);
        }

        // Check if we need to update an old status that would not contains control group info
        boolean inControlGroup;
        for (Map<String, Object> oldStrategyStatus : strategyStatuses) {
            if (personalizationRequest.getId().equals(oldStrategyStatus.get(PERSONALIZATION_STRATEGY_STATUS_ID))) {
                // Check if we have to update the strategy status or not ?
                if (!oldStrategyStatus.containsKey(PERSONALIZATION_STRATEGY_STATUS_IN_CTRL_GROUP)) {

                    // Old status doesn't contain any control group check, we need to calculate it and update the old status with the result.
                    inControlGroup = calculateControlGroup(controlGroupConfig);
                    oldStrategyStatus.put(PERSONALIZATION_STRATEGY_STATUS_IN_CTRL_GROUP, inControlGroup);
                    oldStrategyStatus.put(PERSONALIZATION_STRATEGY_STATUS_DATE, new Date());
                    personalizationResult.addChanges(changeType);

                } else {
                    // Just read existing status about the control group
                    inControlGroup = (boolean) oldStrategyStatus.get(PERSONALIZATION_STRATEGY_STATUS_IN_CTRL_GROUP);
                }

                personalizationResult.setInControlGroup(inControlGroup);
                return personalizationResult;
            }
        }

        // We didn't found any existing status for the current perso, we need to create a new one.
        inControlGroup = calculateControlGroup(controlGroupConfig);
        Map<String, Object> newStrategyStatus = new HashMap<>();
        newStrategyStatus.put(PERSONALIZATION_STRATEGY_STATUS_ID, personalizationRequest.getId());
        newStrategyStatus.put(PERSONALIZATION_STRATEGY_STATUS_DATE, new Date());
        newStrategyStatus.put(PERSONALIZATION_STRATEGY_STATUS_IN_CTRL_GROUP, inControlGroup);
        strategyStatuses.add(newStrategyStatus);

        personalizationResult.addChanges(changeType);
        personalizationResult.setInControlGroup(inControlGroup);
        return personalizationResult;
    }

    private boolean calculateControlGroup(Map<String,Object> controlGroupConfig) {
        double percentage = (controlGroupConfig.get(CONTROL_GROUP_CONFIG_PERCENTAGE) != null && controlGroupConfig.get(CONTROL_GROUP_CONFIG_PERCENTAGE) instanceof Number) ?
                ((Number) controlGroupConfig.get(CONTROL_GROUP_CONFIG_PERCENTAGE)).doubleValue() : 0;

        double random = controlGroupRandom.nextDouble() * 100.0;
        return random <= percentage;
    }
}
