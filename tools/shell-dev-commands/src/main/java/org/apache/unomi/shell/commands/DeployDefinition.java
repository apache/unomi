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
package org.apache.unomi.shell.commands;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.PersonaWithSessions;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.*;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.jline.reader.LineReader;
import org.osgi.framework.Bundle;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

@Command(scope = "unomi", name = "deploy-definition", description = "This will deploy a specific definition")
public class DeployDefinition extends OsgiCommandSupport {

    private DefinitionsService definitionsService;
    private GoalsService goalsService;
    private ProfileService profileService;
    private RulesService rulesService;
    private SegmentService segmentService;

    private List<String> definitionTypes = Arrays.asList("condition", "action", "goal", "campaign", "persona", "persona with sessions", "property", "rule", "segment", "scoring");

    @Argument(index = 0, name = "bundleId", description = "The bundle identifier where to find the definition", required = true, multiValued = false)
    Long bundleIdentifier;

    @Argument(index = 1, name = "fileName", description = "The name of the file which contains the definition, without its extension (e.g: firstName)", required = true, multiValued = false)
    String fileName;

    protected Object doExecute() throws Exception {
        Bundle bundleToUpdate = bundleContext.getBundle(bundleIdentifier);
        if (bundleToUpdate == null) {
            System.out.println("Couldn't find a bundle with id: " + bundleIdentifier);
            return null;
        }

        String definitionTypeAnswer = askUserWithAuthorizedAnswer(session,"Which kind of definition do you want to load?" + getDefinitionTypesWithNumber() + "\n", Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));
        String definitionType = definitionTypes.get(new Integer(definitionTypeAnswer));

        String path = getDefinitionTypePath(definitionType);
        Enumeration<URL> definitions = bundleToUpdate.findEntries(path, "*.json", true);
        if (definitions == null) {
            System.out.println("Couldn't find definitions in bundle with id: " + bundleIdentifier + " and definition path: " + path);
            return null;
        }

        while (definitions.hasMoreElements()) {
            URL definitionURL = definitions.nextElement();
            if (definitionURL.toString().contains(fileName)) {
                System.out.println("Found definition at " + definitionURL + ", loading... ");

                updateDefinition(definitionType, definitionURL);
            }
        }

        return null;
    }

    private String askUserWithAuthorizedAnswer(CommandSession session, String msg, List<String> authorizedAnswer) throws IOException {
        String answer;
        do {
            answer = promptMessageToUser(session,msg);
        } while (!authorizedAnswer.contains(answer.toLowerCase()));
        return answer;
    }

    private String promptMessageToUser(CommandSession session, String msg) throws IOException {
        LineReader reader = (LineReader) session.get(".jline.reader");
        return reader.readLine(msg, null);
    }

    private String getDefinitionTypesWithNumber() {
        StringBuilder definitionTypesWithNumber = new StringBuilder();
        for (int i = 0; i < definitionTypes.size(); i++) {
            definitionTypesWithNumber.append("\n").append(i).append(". ").append(definitionTypes.get(i));
        }
        return definitionTypesWithNumber.toString();
    }

    private void updateDefinition(String definitionType, URL definitionURL) {
        try {
            switch (definitionType) {
                case "condition":
                    ConditionType conditionType = CustomObjectMapper.getObjectMapper().readValue(definitionURL, ConditionType.class);
                    definitionsService.setConditionType(conditionType);
                    break;
                case "action":
                    ActionType actionType = CustomObjectMapper.getObjectMapper().readValue(definitionURL, ActionType.class);
                    definitionsService.setActionType(actionType);
                    break;
                case "goal":
                    Goal goal = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Goal.class);
                    goalsService.setGoal(goal);
                    break;
                case "campaign":
                    Campaign campaign = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Campaign.class);
                    goalsService.setCampaign(campaign);
                    break;
                case "persona":
                    Persona persona = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Persona.class);
                    profileService.savePersona(persona);
                    break;
                case "persona with session":
                    PersonaWithSessions personaWithSessions = CustomObjectMapper.getObjectMapper().readValue(definitionURL, PersonaWithSessions.class);
                    profileService.savePersonaWithSessions(personaWithSessions);
                    break;
                case "property":
                    PropertyType propertyType = CustomObjectMapper.getObjectMapper().readValue(definitionURL, PropertyType.class);
                    profileService.setPropertyTypeTarget(definitionURL, propertyType);
                    profileService.setPropertyType(propertyType);
                    break;
                case "rule":
                    Rule rule = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Rule.class);
                    rulesService.setRule(rule);
                    break;
                case "segment":
                    Segment segment = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Segment.class);
                    segmentService.setSegmentDefinition(segment);
                    break;
                case "scoring":
                    Scoring scoring = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Scoring.class);
                    segmentService.setScoringDefinition(scoring);
                    break;
            }
            System.out.println("Predefined definition registered");
        } catch (IOException e) {
            System.out.println("Error while saving definition " + definitionURL);
            System.out.println(e.getMessage());
        }
    }

    private String getDefinitionTypePath(String definitionType) {
        StringBuilder path = new StringBuilder("META-INF/cxs/");
        switch (definitionType) {
            case "condition":
                path.append("conditions");
                break;
            case "action":
                path.append("actions");
                break;
            case "goal":
                path.append("goals");
                break;
            case "campaign":
                path.append("campaigns");
                break;
            case "persona":
                path.append("personas");
                break;
            case "persona with session":
                path.append("personas");
                break;
            case "property":
                path.append("properties");
                break;
            case "rule":
                path.append("rules");
                break;
            case "segment":
                path.append("segments");
                break;
            case "scoring":
                path.append("scoring");
                break;
        }

        return path.toString();
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setGoalsService(GoalsService goalsService) {
        this.goalsService = goalsService;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }
}
