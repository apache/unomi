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

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;

@Command(scope = "unomi", name = "undeploy-definition", description = "This will undeploy definitions contained in bundles")
@Service
public class UndeployDefinition extends DeploymentCommandSupport {

    public void processDefinition(String definitionType, URL definitionURL) {
        try {
            processDefinitionInternal(definitionType, definitionURL, getConsole(), "Predefined definition unregistered");
        } catch (IOException e) {
            handleDefinitionError(definitionURL, "removing", e);
        }
    }

    @Override
    protected boolean processDefinitionByType(String definitionType, URL definitionURL, PrintStream console) throws IOException {
        switch (definitionType) {
            case CONDITION_DEFINITION_TYPE:
                ConditionType conditionType = readDefinition(definitionURL, ConditionType.class);
                definitionsService.removeActionType(conditionType.getItemId());
                return true;
            case ACTION_DEFINITION_TYPE:
                ActionType actionType = readDefinition(definitionURL, ActionType.class);
                definitionsService.removeActionType(actionType.getItemId());
                return true;
            case GOAL_DEFINITION_TYPE:
                Goal goal = readDefinition(definitionURL, Goal.class);
                goalsService.removeGoal(goal.getItemId());
                return true;
            case CAMPAIGN_DEFINITION_TYPE:
                Campaign campaign = readDefinition(definitionURL, Campaign.class);
                goalsService.removeCampaign(campaign.getItemId());
                return true;
            case PERSONA_DEFINITION_TYPE:
                PersonaWithSessions persona = readDefinition(definitionURL, PersonaWithSessions.class);
                profileService.delete(persona.getPersona().getItemId(), true);
                return true;
            case PROPERTY_DEFINITION_TYPE:
                PropertyType propertyType = readDefinition(definitionURL, PropertyType.class);
                profileService.deletePropertyType(propertyType.getItemId());
                return true;
            case RULE_DEFINITION_TYPE:
                Rule rule = readDefinition(definitionURL, Rule.class);
                rulesService.removeRule(rule.getItemId());
                return true;
            case SEGMENT_DEFINITION_TYPE:
                Segment segment = readDefinition(definitionURL, Segment.class);
                segmentService.removeSegmentDefinition(segment.getItemId(), false);
                return true;
            case SCORING_DEFINITION_TYPE:
                Scoring scoring = readDefinition(definitionURL, Scoring.class);
                segmentService.removeScoringDefinition(scoring.getItemId(), false);
                return true;
            case PATCH_DEFINITION_TYPE:
                Patch patch = readDefinition(definitionURL, Patch.class);
                // patchService.patch(patch);
                return true;
            default:
                console.println("Unrecognized definition type: " + definitionType);
                return false;
        }
    }

}
