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

@Command(scope = "unomi", name = "deploy-definition", description = "This will deploy Unomi definitions contained in bundles")
@Service
public class DeployDefinition extends DeploymentCommandSupport {

    public void processDefinition(String definitionType, URL definitionURL) {
        try {
            processDefinitionInternal(definitionType, definitionURL, getConsole(), "Predefined definition registered");
        } catch (IOException e) {
            handleDefinitionError(definitionURL, "saving", e);
        }
    }

    protected void deployConditionType(URL definitionURL) throws IOException {
        ConditionType conditionType = readDefinition(definitionURL, ConditionType.class);
        definitionsService.setConditionType(conditionType);
    }

    protected void deployActionType(URL definitionURL) throws IOException {
        ActionType actionType = readDefinition(definitionURL, ActionType.class);
        definitionsService.setActionType(actionType);
    }

    protected void deployGoal(URL definitionURL) throws IOException {
        Goal goal = readDefinition(definitionURL, Goal.class);
        goalsService.setGoal(goal);
    }

    protected void deployCampaign(URL definitionURL) throws IOException {
        Campaign campaign = readDefinition(definitionURL, Campaign.class);
        goalsService.setCampaign(campaign);
    }

    protected void deployPersona(URL definitionURL) throws IOException {
        PersonaWithSessions persona = readDefinition(definitionURL, PersonaWithSessions.class);
        profileService.savePersonaWithSessions(persona);
    }

    protected void deployPropertyType(URL definitionURL) throws IOException {
        PropertyType propertyType = readDefinition(definitionURL, PropertyType.class);
        profileService.setPropertyTypeTarget(definitionURL, propertyType);
        profileService.setPropertyType(propertyType);
    }

    protected void deployRule(URL definitionURL) throws IOException {
        Rule rule = readDefinition(definitionURL, Rule.class);
        rulesService.setRule(rule);
    }

    protected void deploySegment(URL definitionURL) throws IOException {
        Segment segment = readDefinition(definitionURL, Segment.class);
        segmentService.setSegmentDefinition(segment);
    }

    protected void deployScoring(URL definitionURL) throws IOException {
        Scoring scoring = readDefinition(definitionURL, Scoring.class);
        segmentService.setScoringDefinition(scoring);
    }

    protected void deployPatch(URL definitionURL) throws IOException {
        Patch patch = readDefinition(definitionURL, Patch.class);
        patchService.patch(patch);
    }

    @Override
    protected boolean processDefinitionByType(String definitionType, URL definitionURL, PrintStream console) throws IOException {
        switch (definitionType) {
            case CONDITION_DEFINITION_TYPE:
                deployConditionType(definitionURL);
                return true;
            case ACTION_DEFINITION_TYPE:
                deployActionType(definitionURL);
                return true;
            case GOAL_DEFINITION_TYPE:
                deployGoal(definitionURL);
                return true;
            case CAMPAIGN_DEFINITION_TYPE:
                deployCampaign(definitionURL);
                return true;
            case PERSONA_DEFINITION_TYPE:
                deployPersona(definitionURL);
                return true;
            case PROPERTY_DEFINITION_TYPE:
                deployPropertyType(definitionURL);
                return true;
            case RULE_DEFINITION_TYPE:
                deployRule(definitionURL);
                return true;
            case SEGMENT_DEFINITION_TYPE:
                deploySegment(definitionURL);
                return true;
            case SCORING_DEFINITION_TYPE:
                deployScoring(definitionURL);
                return true;
            case PATCH_DEFINITION_TYPE:
                deployPatch(definitionURL);
                return true;
            default:
                console.println("Unrecognized definition type:" + definitionType);
                return false;
        }
    }
}
