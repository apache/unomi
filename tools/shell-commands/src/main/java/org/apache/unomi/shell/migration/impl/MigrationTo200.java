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
package org.apache.unomi.shell.migration.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.service.command.CommandSession;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.shell.migration.Migration;
import org.apache.unomi.shell.migration.utils.ConsoleUtils;
import org.apache.unomi.shell.migration.utils.MigrationUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.framework.Version;

import java.io.IOException;
import java.util.*;

/**
 * @author dgaillard
 */
public class MigrationTo200 implements Migration {

    private CloseableHttpClient httpClient;
    private CommandSession session;
    private LinkedHashMap<String, List<String>> tagsStructurePriorTo200;

    @Override
    public Version getFromVersion() {
        return null;
    }

    @Override
    public Version getToVersion() {
        return new Version("2.0.0");
    }

    @Override
    public void execute(CommandSession session, CloseableHttpClient httpClient) throws IOException {
        try {
            this.httpClient = httpClient;
            this.session = session;
            migrateTags();
        } catch (IOException e) {
            if (httpClient != null) {
                httpClient.close();
            }
            throw e;
        }
    }

    private void migrateTags() throws IOException {
        initTagsStructurePriorTo200();
        String hostAddress = ConsoleUtils.askUserWithDefaultAnswer(session, "Host address (default = http://localhost:9200): ", "http://localhost:9200");

        List<String> typeToMigrate = Arrays.asList("actionType", "conditionType", "campaign", "goal", "rule", "scoring", "segment", "userList");
        for (String type : typeToMigrate) {
            migrateTypeTags(hostAddress, type);
        }

        migratePropertyTypesTags(hostAddress);
    }

    private void migrateTypeTags(String hostAddress, String type) throws IOException {
        JSONObject responseJSON = MigrationUtils.queryWithScroll(httpClient, hostAddress + "/context/" + type + "/_search");

        migrateTagsInResult(responseJSON, hostAddress, type,10, true);
    }

    private void migratePropertyTypesTags(String hostAddress) throws IOException {
        JSONObject responseJSON = MigrationUtils.queryWithScroll(httpClient,hostAddress + "/context/propertyType/_search");

        migrateTagsInResult(responseJSON, hostAddress, "propertyType", 10, false);
    }

    private void migrateTagsInResult(JSONObject responseJSON, String hostAddress, String type, int currentOffset, boolean tagsInMetadata) throws IOException {
        if (responseJSON.has("hits")) {
            JSONObject hitsObject = responseJSON.getJSONObject("hits");
            if (hitsObject.has("hits")) {
                JSONArray hits = hitsObject.getJSONArray("hits");

                StringBuilder updatedHits = new StringBuilder();
                Iterator<Object> hitsIterator = hits.iterator();
                while (hitsIterator.hasNext()) {
                    JSONObject hit = (JSONObject) hitsIterator.next();
                    if (hit.has("_source")) {
                        JSONObject hitSource = hit.getJSONObject("_source");
                        if (tagsInMetadata && hitSource.has("metadata")) {
                            JSONObject hitMetadata = hitSource.getJSONObject("metadata");
                            updateTagsForHit(updatedHits, hit.getString("_id"), hitMetadata, tagsInMetadata);
                        } else if (!tagsInMetadata) {
                            updateTagsForHit(updatedHits, hit.getString("_id"), hitSource, tagsInMetadata);
                        }
                    }
                }
                String jsonData = updatedHits.toString();
                if (StringUtils.isNotBlank(jsonData)) {
                    MigrationUtils.bulkUpdate(httpClient, hostAddress + "/context/" + type + "/_bulk", jsonData);
                }

                if (hitsObject.getInt("total") > currentOffset) {
                    migrateTagsInResult(MigrationUtils.continueQueryWithScroll(httpClient, hostAddress, responseJSON.getString("_scroll_id")), hostAddress, type,currentOffset + 10, tagsInMetadata);
                }
            }
        }
    }

    private void updateTagsForHit(StringBuilder updatedHits, String hitId, JSONObject jsonObject, boolean tagsInMetadata) {
        if (jsonObject.has("tags")) {
            JSONArray hitTags = jsonObject.getJSONArray("tags");
            Iterator<Object> tagsIterator = hitTags.iterator();
            List<String> tagsBeforeMigration = new ArrayList<>();
            List<String> tagsAfterMigration = new ArrayList<>();
            if (tagsIterator.hasNext()) {
                while (tagsIterator.hasNext()) {
                    tagsBeforeMigration.add((String) tagsIterator.next());
                }

                for (String tag : tagsBeforeMigration) {
                    if (tagsStructurePriorTo200.containsKey(tag) && !tagsAfterMigration.containsAll(tagsStructurePriorTo200.get(tag))) {
                        tagsAfterMigration.addAll(tagsStructurePriorTo200.get(tag));
                    }

                    if (!tagsAfterMigration.contains(tag)) {
                        tagsAfterMigration.add(tag);
                    }
                }

                updatedHits.append("{\"update\":{\"_id\":\"").append(hitId).append("\"}}\n");
                updatedHits.append("{\"doc\":{\"metadata\":{\"tags\":").append(new JSONArray(tagsAfterMigration)).append("}}}\n");
                if (!tagsInMetadata) {
                    updatedHits.append("{\"update\":{\"_id\":\"").append(hitId).append("\"}}\n");
                    updatedHits.append("{\"script\":\"ctx._source.remove(\\\"tags\\\")\"}\n");
                }
            }
        }
    }

    private void initTagsStructurePriorTo200() {
        tagsStructurePriorTo200 = new LinkedHashMap<>();
        tagsStructurePriorTo200.put("landing", Collections.singletonList("campaign"));
        tagsStructurePriorTo200.put("parameter", Collections.singletonList("campaign"));
        tagsStructurePriorTo200.put("referrer", Collections.singletonList("campaign"));

        tagsStructurePriorTo200.put("eventCondition", Collections.singletonList("condition"));
        tagsStructurePriorTo200.put("profileCondition", Collections.singletonList("condition"));
        tagsStructurePriorTo200.put("sessionCondition", Collections.singletonList("condition"));
        tagsStructurePriorTo200.put("sourceEventCondition", Collections.singletonList("condition"));
        tagsStructurePriorTo200.put("trackedCondition", Collections.singletonList("condition"));
        tagsStructurePriorTo200.put("usableInPastEventCondition", Collections.singletonList("condition"));

        tagsStructurePriorTo200.put("formMappingRule", Collections.<String>emptyList());

        tagsStructurePriorTo200.put("downloadGoal", Collections.singletonList("goal"));
        tagsStructurePriorTo200.put("formGoal", Collections.singletonList("goal"));
        tagsStructurePriorTo200.put("funnelGoal", Collections.singletonList("goal"));
        tagsStructurePriorTo200.put("landingPageGoal", Collections.singletonList("goal"));
        tagsStructurePriorTo200.put("pageVisitGoal", Collections.singletonList("goal"));
        tagsStructurePriorTo200.put("videoGoal", Collections.singletonList("goal"));

        tagsStructurePriorTo200.put("aggregated", Collections.singletonList("profileTags"));
        tagsStructurePriorTo200.put("autocompleted", Collections.singletonList("profileTags"));
        tagsStructurePriorTo200.put("demographic", Collections.singletonList("profileTags"));
        tagsStructurePriorTo200.put("event", Collections.singletonList("profileTags"));
        tagsStructurePriorTo200.put("geographic", Collections.singletonList("profileTags"));
        tagsStructurePriorTo200.put("logical", Collections.singletonList("profileTags"));

        tagsStructurePriorTo200.put("profileProperties", Collections.singletonList("properties"));
        tagsStructurePriorTo200.put("systemProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo200.put("basicProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo200.put("leadProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo200.put("contactProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo200.put("socialProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo200.put("personalProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo200.put("workProfileProperties", Arrays.asList("properties", "profileProperties"));

        tagsStructurePriorTo200.put("sessionProperties", Collections.singletonList("properties"));
        tagsStructurePriorTo200.put("geographicSessionProperties", Arrays.asList("properties", "sessionProperties"));
        tagsStructurePriorTo200.put("technicalSessionProperties", Arrays.asList("properties", "sessionProperties"));
    }
}
