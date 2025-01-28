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

import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.api.services.*;
import org.jline.reader.LineReader;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class DeploymentCommandSupport implements Action {

    public static final String ALL_OPTION_LABEL = "* (All)";
    @Reference
    DefinitionsService definitionsService;

    @Reference
    GoalsService goalsService;

    @Reference
    ProfileService profileService;

    @Reference
    RulesService rulesService;

    @Reference
    SegmentService segmentService;

    @Reference
    PatchService patchService;

    @Reference
    BundleContext bundleContext;

    @Reference
    Session session;

    public static final String CONDITION_DEFINITION_TYPE = "conditions";
    public static final String ACTION_DEFINITION_TYPE = "actions";
    public static final String GOAL_DEFINITION_TYPE = "goals";
    public static final String CAMPAIGN_DEFINITION_TYPE = "campaigns";
    public static final String PERSONA_DEFINITION_TYPE = "personas";
    public static final String PROPERTY_DEFINITION_TYPE = "properties";
    public static final String RULE_DEFINITION_TYPE = "rules";
    public static final String SEGMENT_DEFINITION_TYPE = "segments";
    public static final String SCORING_DEFINITION_TYPE = "scoring";
    public static final String PATCH_DEFINITION_TYPE = "patches";
    public static final String VALUE_DEFINITION_TYPE = "values";
    public static final String MERGER_DEFINITION_TYPE = "mergers";
    public static final String MAPPING_DEFINITION_TYPE = "mappings";
    public static final String JSON_SCHEMA_DEFINITION_TYPE = "jsonschema";

    protected final static List<String> definitionTypes = Arrays.asList(
            CONDITION_DEFINITION_TYPE,
            ACTION_DEFINITION_TYPE,
            GOAL_DEFINITION_TYPE,
            CAMPAIGN_DEFINITION_TYPE,
            PERSONA_DEFINITION_TYPE,
            PROPERTY_DEFINITION_TYPE,
            RULE_DEFINITION_TYPE,
            SEGMENT_DEFINITION_TYPE,
            SCORING_DEFINITION_TYPE,
            PATCH_DEFINITION_TYPE,
            VALUE_DEFINITION_TYPE,
            MERGER_DEFINITION_TYPE,
            MAPPING_DEFINITION_TYPE,
            JSON_SCHEMA_DEFINITION_TYPE);

    @Argument(index = 0, name = "bundleId", description = "The bundle identifier where to find the definition", multiValued = false)
    Long bundleIdentifier;

    @Argument(index = 1, name = "type", description = "The kind of definitions you want to load (e.g.: *, conditions, actions, ..)", required = false, multiValued = false)
    String definitionType;

    @Argument(index = 2, name = "fileName", description = "The name of the file which contains the definition, without its extension (e.g: firstName)", required = false, multiValued = false)
    String fileName;

    public abstract void processDefinition(String definitionType, URL definitionURL);

    public Object execute() throws Exception {
        List<Bundle> bundlesToUpdate;
        if ("*".equals(definitionType)) {
            definitionType = ALL_OPTION_LABEL;
        }
        if ("*".equals(fileName)) {
            fileName = ALL_OPTION_LABEL;
        }
        if (bundleIdentifier == null) {
            List<Bundle> bundles = new ArrayList<>();
            for (Bundle bundle : bundleContext.getBundles()) {
                if (bundle.findEntries("META-INF/cxs/", "*.json", true) != null) {
                    bundles.add(bundle);
                }
            }

            bundles = bundles.stream()
                    .filter(b -> definitionTypes.stream().anyMatch((t) -> b.findEntries(getDefinitionTypePath(t), "*.json", true) != null))
                    .collect(Collectors.toList());

            List<String> bundleSymbolicNames = bundles.stream().map(Bundle::getSymbolicName).collect(Collectors.toList());
            bundleSymbolicNames.add(ALL_OPTION_LABEL);

            String bundleAnswer = askUserWithAuthorizedAnswer(session, "Which bundle ?" + getValuesWithNumber(bundleSymbolicNames) + "\n",
                    IntStream.range(1,bundleSymbolicNames.size()+1).mapToObj(Integer::toString).collect(Collectors.toList()));
            String selectedBundle = bundleSymbolicNames.get(new Integer(bundleAnswer)-1);
            if (selectedBundle.equals(ALL_OPTION_LABEL)) {
                bundlesToUpdate = bundles;
            } else {
                bundlesToUpdate = Collections.singletonList(bundles.get(new Integer(bundleAnswer) - 1));
            }
        } else {
            Bundle bundle = bundleContext.getBundle(bundleIdentifier);

            if (bundle == null) {
                System.out.println("Couldn't find a bundle with id: " + bundleIdentifier);
                return null;
            }

            bundlesToUpdate = Collections.singletonList(bundle);
        }

        if (definitionType == null) {
            List<String> possibleDefinitionNames = definitionTypes.stream().filter((t) -> bundlesToUpdate.stream().anyMatch(b->b.findEntries(getDefinitionTypePath(t), "*.json", true) != null)).collect(Collectors.toList());
            possibleDefinitionNames.add(ALL_OPTION_LABEL);

            if (possibleDefinitionNames.isEmpty()) {
                System.out.println("Couldn't find definitions in bundle : " + bundlesToUpdate);
                return null;
            }

            String definitionTypeAnswer = askUserWithAuthorizedAnswer(session, "Which kind of definition do you want to load?" + getValuesWithNumber(possibleDefinitionNames) + "\n",
                    IntStream.range(1,possibleDefinitionNames.size()+1).mapToObj(Integer::toString).collect(Collectors.toList()));
            definitionType = possibleDefinitionNames.get(new Integer(definitionTypeAnswer)-1);
        }

        if (!definitionTypes.contains(definitionType) && !ALL_OPTION_LABEL.equals(definitionType)) {
            System.out.println("Invalid type '" + definitionType + "' , allowed values : " +definitionTypes);
            return null;
        }

        String definitionTypePath = getDefinitionTypePath(definitionType);
        List<URL> definitionTypeURLs = bundlesToUpdate.stream().flatMap(b->b.findEntries(definitionTypePath, "*.json", true) != null ? Collections.list(b.findEntries(definitionTypePath, "*.json", true)).stream() : Stream.empty()).collect(Collectors.toList());
        if (definitionTypeURLs.isEmpty()) {
            System.out.println("Couldn't find definitions in bundle with id: " + bundleIdentifier + " and definition path: " + definitionTypePath);
            return null;
        }

        if (fileName == null) {
            List<String> definitionTypeFileNames = definitionTypeURLs.stream().map(u -> StringUtils.substringAfterLast(u.getFile(), "/")).sorted().collect(Collectors.toList());
            definitionTypeFileNames.add(ALL_OPTION_LABEL);
            String fileNameAnswer = askUserWithAuthorizedAnswer(session, "Which file do you want to load ?" + getValuesWithNumber(definitionTypeFileNames) + "\n",
                    IntStream.range(1,definitionTypeFileNames.size()+1).mapToObj(Integer::toString).collect(Collectors.toList()));
            fileName = definitionTypeFileNames.get(new Integer(fileNameAnswer)-1);
        }
        if (ALL_OPTION_LABEL.equals(fileName)) {
            for (URL url : definitionTypeURLs) {
                processDefinition(definitionType, url);
            }
        } else {
            if (!fileName.contains("/")) {
                fileName = "/" + fileName;
            }
            if (!fileName.endsWith(".json")) {
                fileName += ".json";
            }

            Optional<URL> optionalURL = definitionTypeURLs.stream().filter(u -> u.getFile().endsWith(fileName)).findFirst();
            if (optionalURL.isPresent()) {
                URL url = optionalURL.get();
                processDefinition(definitionType, url);
            } else {
                System.out.println("Couldn't find file " + fileName);
                return null;
            }
        }

        return null;
    }

    protected String askUserWithAuthorizedAnswer(Session session, String msg, List<String> authorizedAnswer) throws IOException {
        String answer;
        do {
            answer = promptMessageToUser(session,msg);
        } while (!authorizedAnswer.contains(answer.toLowerCase()));
        return answer;
    }

    protected String promptMessageToUser(Session session, String msg) throws IOException {
        LineReader reader = (LineReader) session.get(".jline.reader");
        return reader.readLine(msg, null);
    }

    protected String getValuesWithNumber(List<String> values) {
        StringBuilder definitionTypesWithNumber = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            definitionTypesWithNumber.append("\n").append(i+1).append(". ").append(values.get(i));
        }
        return definitionTypesWithNumber.toString();
    }

    protected String getDefinitionTypePath(String definitionType) {
        StringBuilder path = new StringBuilder("META-INF/cxs/");
        if (!ALL_OPTION_LABEL.equals(definitionType)) {
            path.append(definitionType);
        }
        return path.toString();
    }

}
