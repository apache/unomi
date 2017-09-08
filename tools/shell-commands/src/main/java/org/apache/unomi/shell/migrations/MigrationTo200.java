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
package org.apache.unomi.shell.migrations;

import org.apache.felix.service.command.CommandSession;
import org.apache.unomi.shell.utils.ConsoleUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * @author dgaillard
 */
public class MigrationTo200 extends AbstractMigrationResource {

    public MigrationTo200(CommandSession session) throws IOException {
        super(session);
    }

    public void execute() throws IOException {
        try {
            initHttpClient();
            migrateTags();
        } catch (IOException e) {
            closeHttpClient();
            throw e;
        }
    }

    private void migrateTags() throws IOException {
        String hostAddress = ConsoleUtils.askUserWithDefaultAnswer(session, "Host address (default = http://localhost:9200): ", "http://localhost:9200");

        List<JSONArray> totalHits = getDataToMigrate(hostAddress + "/context/propertyType/_search", 0, 10);

        StringBuilder updatedHits = new StringBuilder();
        for (JSONArray hits : totalHits) {
            Iterator<Object> hitsIterator = hits.iterator();
            LinkedHashMap<String, List<String>> legacyTags = getTagsStructurePriorTo200();
            while (hitsIterator.hasNext()) {
                JSONObject hit = (JSONObject) hitsIterator.next();
                if (hit.has("_source")) {
                    JSONObject hitSource = hit.getJSONObject("_source");
                    if (hitSource.has("tags")) {
                        JSONArray hitTags = hitSource.getJSONArray("tags");
                        Iterator<Object> tagsIterator = hitTags.iterator();
                        List<String> tagsBeforeMigration = new ArrayList<>();
                        List<String> tagsAfterMigration = new ArrayList<>();
                        while (tagsIterator.hasNext()) {
                            tagsBeforeMigration.add((String) tagsIterator.next());
                        }
                        for (String tag : tagsBeforeMigration) {
                            if (legacyTags.containsKey(tag) && !tagsAfterMigration.containsAll(legacyTags.get(tag))) {
                                tagsAfterMigration.addAll(legacyTags.get(tag));
                            }

                            if (!tagsAfterMigration.contains(tag)) {
                                tagsAfterMigration.add(tag);
                            }
                        }

                        updatedHits.append("{\"update\":{\"_id\":\"").append(hit.getString("_id")).append("\"}}\n");
                        updatedHits.append("{\"doc\":{\"tags\":").append(new JSONArray(tagsAfterMigration)).append("}}\n");
                    }
                }
            }
        }
        bulkUpdate(hostAddress + "/context/propertyType/_bulk", updatedHits.toString());
    }

    private LinkedHashMap<String, List<String>> getTagsStructurePriorTo200() {
        LinkedHashMap<String, List<String>> tags = new LinkedHashMap<>();
        tags.put("landing", Collections.singletonList("campaign"));
        tags.put("parameter", Collections.singletonList("campaign"));
        tags.put("referrer", Collections.singletonList("campaign"));

        tags.put("eventCondition", Collections.singletonList("condition"));
        tags.put("profileCondition", Collections.singletonList("condition"));
        tags.put("sessionCondition", Collections.singletonList("condition"));
        tags.put("sourceEventCondition", Collections.singletonList("condition"));
        tags.put("trackedCondition", Collections.singletonList("condition"));
        tags.put("usableInPastEventCondition", Collections.singletonList("condition"));

        tags.put("formMappingRule", Collections.<String>emptyList());

        tags.put("downloadGoal", Collections.singletonList("goal"));
        tags.put("formGoal", Collections.singletonList("goal"));
        tags.put("funnelGoal", Collections.singletonList("goal"));
        tags.put("landingPageGoal", Collections.singletonList("goal"));
        tags.put("pageVisitGoal", Collections.singletonList("goal"));
        tags.put("videoGoal", Collections.singletonList("goal"));

        tags.put("aggregated", Collections.singletonList("profileTags"));
        tags.put("autocompleted", Collections.singletonList("profileTags"));
        tags.put("demographic", Collections.singletonList("profileTags"));
        tags.put("event", Collections.singletonList("profileTags"));
        tags.put("geographic", Collections.singletonList("profileTags"));
        tags.put("logical", Collections.singletonList("profileTags"));

        tags.put("profileProperties", Collections.singletonList("properties"));
        tags.put("systemProfileProperties", Arrays.asList("properties", "profileProperties"));
        tags.put("basicProfileProperties", Arrays.asList("properties", "profileProperties"));
        tags.put("leadProfileProperties", Arrays.asList("properties", "profileProperties"));
        tags.put("contactProfileProperties", Arrays.asList("properties", "profileProperties"));
        tags.put("socialProfileProperties", Arrays.asList("properties", "profileProperties"));
        tags.put("personalProfileProperties", Arrays.asList("properties", "profileProperties"));
        tags.put("workProfileProperties", Arrays.asList("properties", "profileProperties"));

        tags.put("sessionProperties", Collections.singletonList("properties"));
        tags.put("geographicSessionProperties", Arrays.asList("properties", "sessionProperties"));
        tags.put("technicalSessionProperties", Arrays.asList("properties", "sessionProperties"));

        return tags;
    }
}
