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
package org.apache.unomi.shell.dev.commands;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.Patch;
import org.apache.unomi.api.PersonaWithSessions;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.persistence.spi.CustomObjectMapper;

import java.io.IOException;
import java.net.URL;

@Command(scope = "unomi", name = "deploy-definition", description = "This will deploy Unomi definitions contained in bundles")
@Service
public class DeployDefinition extends DeploymentCommandSupport {

    public void processDefinition(String definitionType, URL definitionURL) {
        try {
            if (ALL_OPTION_LABEL.equals(definitionType)) {
                String definitionURLString = definitionURL.toString();
                for (String possibleDefinitionType : definitionTypes) {
                    if (definitionURLString.contains(getDefinitionTypePath(possibleDefinitionType))) {
                        definitionType = possibleDefinitionType;
                        break;
                    }
                }
                if (ALL_OPTION_LABEL.equals(definitionType)) {
                    System.out.println("Couldn't resolve definition type for definition URL " + definitionURL);
                    return;
                }
            }
            boolean successful = true;
            switch (definitionType) {
                case CONDITION_DEFINITION_TYPE:
                    ConditionType conditionType = CustomObjectMapper.getObjectMapper().readValue(definitionURL, ConditionType.class);
                    definitionsService.setConditionType(conditionType);
                    break;
                case ACTION_DEFINITION_TYPE:
                    ActionType actionType = CustomObjectMapper.getObjectMapper().readValue(definitionURL, ActionType.class);
                    definitionsService.setActionType(actionType);
                    break;
                case GOAL_DEFINITION_TYPE:
                    Goal goal = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Goal.class);
                    goalsService.setGoal(goal);
                    break;
                case CAMPAIGN_DEFINITION_TYPE:
                    Campaign campaign = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Campaign.class);
                    goalsService.setCampaign(campaign);
                    break;
                case PERSONA_DEFINITION_TYPE:
                    PersonaWithSessions persona = CustomObjectMapper.getObjectMapper().readValue(definitionURL, PersonaWithSessions.class);
                    profileService.savePersonaWithSessions(persona);
                    break;
                case PROPERTY_DEFINITION_TYPE:
                    PropertyType propertyType = CustomObjectMapper.getObjectMapper().readValue(definitionURL, PropertyType.class);
                    profileService.setPropertyTypeTarget(definitionURL, propertyType);
                    profileService.setPropertyType(propertyType);
                    break;
                case RULE_DEFINITION_TYPE:
                    Rule rule = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Rule.class);
                    rulesService.setRule(rule);
                    break;
                case SEGMENT_DEFINITION_TYPE:
                    Segment segment = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Segment.class);
                    segmentService.setSegmentDefinition(segment);
                    break;
                case SCORING_DEFINITION_TYPE:
                    Scoring scoring = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Scoring.class);
                    segmentService.setScoringDefinition(scoring);
                    break;
                case PATCH_DEFINITION_TYPE:
                    Patch patch = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Patch.class);
                    patchService.patch(patch);
                    break;
                default:
                    System.out.println("Unrecognized definition type:" + definitionType);
                    successful = false;
                    break;
            }
            if (successful) {
                System.out.println("Predefined definition registered : " + definitionURL.getFile());
            }
        } catch (IOException e) {
            System.out.println("Error while saving definition " + definitionURL);
            System.out.println(e.getMessage());
        }
    }


}
