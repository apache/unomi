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
import org.apache.unomi.api.*;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Command(scope = "unomi", name = "deploy-definition", description = "This will deploy a specific definition")
public class DeployDefinitionCommand extends OsgiCommandSupport {

    private DefinitionsService definitionsService;
    private GoalsService goalsService;
    private ProfileService profileService;
    private RulesService rulesService;
    private SegmentService segmentService;
    private PatchService patchService;

    private final static List<String> definitionTypes = Arrays.asList("condition", "action", "goal", "campaign", "persona", "property", "rule", "segment", "scoring", "patch");


    @Argument(index = 0, name = "bundleId", description = "The bundle identifier where to find the definition", multiValued = false)
    Long bundleIdentifier;

    @Argument(index = 1, name = "type", description = "The kind of definitions you want to load (e.g.: condition, action, ..)", required = false, multiValued = false)
    String definitionType;

    @Argument(index = 2, name = "fileName", description = "The name of the file which contains the definition, without its extension (e.g: firstName)", required = false, multiValued = false)
    String fileName;

    protected Object doExecute() throws Exception {
        Bundle bundleToUpdate;
        if (bundleIdentifier == null) {
            List<Bundle> bundles = new ArrayList<>();
            for (Bundle bundle : bundleContext.getBundles()) {
                if (bundle.findEntries("META-INF/cxs/", "*.json", true) != null) {
                    bundles.add(bundle);
                }
            }

            String bundleAnswer = askUserWithAuthorizedAnswer(session, "Which bundle ?" + getValuesWithNumber(bundles.stream().map(Bundle::getSymbolicName).collect(Collectors.toList())) + "\n",
                    IntStream.range(1,bundles.size()+1).mapToObj(Integer::toString).collect(Collectors.toList()));
            bundleToUpdate = bundles.get(new Integer(bundleAnswer)-1);
            bundleIdentifier = bundleToUpdate.getBundleId();
        } else {
            bundleToUpdate = bundleContext.getBundle(bundleIdentifier);
        }

        if (bundleToUpdate == null) {
            System.out.println("Couldn't find a bundle with id: " + bundleIdentifier);
            return null;
        }


        if (definitionType == null) {
            List<String> values = definitionTypes.stream().filter((t) -> bundleToUpdate.findEntries(getDefinitionTypePath(t), "*.json", true) != null).collect(Collectors.toList());
            String definitionTypeAnswer = askUserWithAuthorizedAnswer(session, "Which kind of definition do you want to load?" + getValuesWithNumber(values) + "\n",
                    IntStream.range(1,values.size()+1).mapToObj(Integer::toString).collect(Collectors.toList()));
            definitionType = values.get(new Integer(definitionTypeAnswer)-1);
        }

        if (!definitionTypes.contains(definitionType)) {
            System.out.println("Invalid type '" + definitionType + "' , allowed values : " +definitionTypes);
            return null;
        }

        String path = getDefinitionTypePath(definitionType);
        Enumeration<URL> definitions = bundleToUpdate.findEntries(path, "*.json", true);
        if (definitions == null) {
            System.out.println("Couldn't find definitions in bundle with id: " + bundleIdentifier + " and definition path: " + path);
            return null;
        }

        List<URL> values = new ArrayList<>();
        while (definitions.hasMoreElements()) {
            values.add(definitions.nextElement());
        }
        if (fileName == null) {
            List<String> stringList = values.stream().map(u -> StringUtils.substringAfterLast(u.getFile(), "/")).collect(Collectors.toList());
            Collections.sort(stringList);
            stringList.add(0, "* (All)");
            String fileNameAnswer = askUserWithAuthorizedAnswer(session, "Which file do you want to load ?" + getValuesWithNumber(stringList) + "\n",
                    IntStream.range(1,stringList.size()+1).mapToObj(Integer::toString).collect(Collectors.toList()));
            fileName = stringList.get(new Integer(fileNameAnswer)-1);
        }
        if (fileName.startsWith("*")) {
            for (URL url : values) {
                updateDefinition(definitionType, url);
            }
        } else {
            if (!fileName.contains("/")) {
                fileName = "/" + fileName;
            }
            if (!fileName.endsWith(".json")) {
                fileName += ".json";
            }

            Optional<URL> optionalURL = values.stream().filter(u -> u.getFile().endsWith(fileName)).findFirst();
            if (optionalURL.isPresent()) {
                URL url = optionalURL.get();
                updateDefinition(definitionType, url);
            } else {
                System.out.println("Couldn't find file " + fileName);
                return null;
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

    private String getValuesWithNumber(List<String> values) {
        StringBuilder definitionTypesWithNumber = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            definitionTypesWithNumber.append("\n").append(i+1).append(". ").append(values.get(i));
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
                    PersonaWithSessions persona = CustomObjectMapper.getObjectMapper().readValue(definitionURL, PersonaWithSessions.class);
                    profileService.savePersonaWithSessions(persona);
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
                case "patch":
                    Patch patch = CustomObjectMapper.getObjectMapper().readValue(definitionURL, Patch.class);
                    patchService.patch(patch);
                    break;
            }
            System.out.println("Predefined definition registered : "+definitionURL.getFile());
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
            case "patch":
                path.append("patches");
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

    public void setPatchService(PatchService patchService) {
        this.patchService = patchService;
    }
}
