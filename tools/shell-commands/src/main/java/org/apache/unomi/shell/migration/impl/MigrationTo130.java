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
public class MigrationTo130 implements Migration {

    private CloseableHttpClient httpClient;
    private CommandSession session;
    private LinkedHashMap<String, List<String>> tagsStructurePriorTo130;

    @Override
    public Version getFromVersion() {
        return null;
    }

    @Override
    public Version getToVersion() {
        return new Version("1.3.0");
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
        initTagsStructurePriorTo130();
        String hostAddress = ConsoleUtils.askUserWithDefaultAnswer(session, "Host address (default = http://localhost:9200): ", "http://localhost:9200");
        String tagsOperation = ConsoleUtils.askUserWithAuthorizedAnswer(session, "How to manage tags?\n1. copy: will duplicate tags in systemTags property\n2. move: will move tags in systemTags property\n[1 - 2]: ", Arrays.asList("1", "2"));
        String removeNamespaceOnSystemTags = ConsoleUtils.askUserWithAuthorizedAnswer(session, "As we will copy/move the tags, do you wish to remove existing namespace on tags before copy/move in systemTags? (e.g: hidden.) (yes/no): ", Arrays.asList("yes", "no"));

        List<String> typeToMigrate = Arrays.asList("actionType", "conditionType", "campaign", "goal", "rule", "scoring", "segment", "userList");
        for (String type : typeToMigrate) {
            migrateTagsInResult(hostAddress, type, 10, true, tagsOperation, removeNamespaceOnSystemTags.equals("yes"), null);
        }

        migrateTagsInResult(hostAddress, "propertyType", 10, false, tagsOperation, removeNamespaceOnSystemTags.equals("yes"), null);
    }

    private void migrateTagsInResult(String hostAddress, String type, int currentOffset,
                                     boolean tagsInMetadata, String tagsOperation, boolean removeNamespaceOnSystemTags, String scrollId) throws IOException {

        JSONObject responseJSON;
        if (StringUtils.isNotBlank(scrollId)) {
            responseJSON = MigrationUtils.continueQueryWithScroll(httpClient, hostAddress, scrollId);
        } else {
            responseJSON = MigrationUtils.queryWithScroll(httpClient, hostAddress + "/context/" + type + "/_search");
        }

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
                            updateTagsForHit(updatedHits, hit.getString("_id"), hitMetadata, tagsInMetadata, tagsOperation, removeNamespaceOnSystemTags);
                        } else if (!tagsInMetadata) {
                            updateTagsForHit(updatedHits, hit.getString("_id"), hitSource, tagsInMetadata, tagsOperation, removeNamespaceOnSystemTags);
                        }
                    }
                }
                String jsonData = updatedHits.toString();
                if (StringUtils.isNotBlank(jsonData)) {
                    MigrationUtils.bulkUpdate(httpClient, hostAddress + "/context/" + type + "/_bulk", jsonData);
                }

                if (hitsObject.getInt("total") > currentOffset) {
                    migrateTagsInResult(hostAddress, type, currentOffset + 10, tagsInMetadata, tagsOperation, removeNamespaceOnSystemTags, responseJSON.getString("_scroll_id"));
                }
            }
        }
    }

    private void updateTagsForHit(StringBuilder updatedHits, String hitId, JSONObject jsonObject,
                                  boolean tagsInMetadata, String tagsOperation, boolean removeNamespaceOnSystemTags) {
        if (jsonObject.has("tags")) {
            JSONArray hitTags = jsonObject.getJSONArray("tags");
            Iterator<Object> tagsIterator = hitTags.iterator();
            Set<String> tagsBeforeMigration = new HashSet<>();
            Set<String> tagsAfterMigration = new HashSet<>();
            if (tagsIterator.hasNext()) {
                while (tagsIterator.hasNext()) {
                    tagsBeforeMigration.add((String) tagsIterator.next());
                }

                for (String tag : tagsBeforeMigration) {
                    if (tagsStructurePriorTo130.containsKey(tag)) {
                        tagsAfterMigration.addAll(tagsStructurePriorTo130.get(tag));
                    }
                    tagsAfterMigration.add(tag);
                }

                updatedHits.append("{\"update\":{\"_id\":\"").append(hitId).append("\"}}\n");
                if (tagsOperation.equals("1")) {
                    Set<String> tags = removeNamespaceOnTags(removeNamespaceOnSystemTags, tagsAfterMigration);
                    updatedHits.append("{\"doc\":{\"metadata\":{\"tags\":").append(new JSONArray(tagsAfterMigration))
                            .append(",\"systemTags\":").append(new JSONArray(tags)).append("}}}\n");
                }
                if (tagsOperation.equals("2")) {
                    Set<String> tags = removeNamespaceOnTags(removeNamespaceOnSystemTags, tagsAfterMigration);
                    updatedHits.append("{\"doc\":{\"metadata\":{\"systemTags\":").append(new JSONArray(tags)).append("}}}\n");
                    if (tagsInMetadata) {
                        updatedHits.append("{\"update\":{\"_id\":\"").append(hitId).append("\"}}\n");
                        updatedHits.append("{\"script\":\"ctx._source.metadata.remove(\\\"tags\\\")\"}\n");
                    }
                }
                if (!tagsInMetadata) {
                    updatedHits.append("{\"update\":{\"_id\":\"").append(hitId).append("\"}}\n");
                    updatedHits.append("{\"script\":\"ctx._source.remove(\\\"tags\\\")\"}\n");
                }
            }
        }
    }

    private Set<String> removeNamespaceOnTags(boolean removeNamespaceOnSystemTags, Set<String> tagsAfterMigration) {
        if (!removeNamespaceOnSystemTags) {
            return tagsAfterMigration;
        }

        Set<String> tags = new HashSet<>();
        for (String tag : tagsAfterMigration) {
            if (StringUtils.startsWith(tag, "hidden.")) {
                tags.add(StringUtils.substringAfter(tag, "hidden."));
            } else {
                tags.add(tag);
            }
        }
        return tags;
    }

    private void initTagsStructurePriorTo130() {
        tagsStructurePriorTo130 = new LinkedHashMap<>();
        tagsStructurePriorTo130.put("landing", Collections.singletonList("campaign"));
        tagsStructurePriorTo130.put("parameter", Collections.singletonList("campaign"));
        tagsStructurePriorTo130.put("referrer", Collections.singletonList("campaign"));

        tagsStructurePriorTo130.put("eventCondition", Collections.singletonList("condition"));
        tagsStructurePriorTo130.put("profileCondition", Collections.singletonList("condition"));
        tagsStructurePriorTo130.put("sessionCondition", Collections.singletonList("condition"));
        tagsStructurePriorTo130.put("sourceEventCondition", Collections.singletonList("condition"));
        tagsStructurePriorTo130.put("trackedCondition", Collections.singletonList("condition"));
        tagsStructurePriorTo130.put("usableInPastEventCondition", Collections.singletonList("condition"));

        tagsStructurePriorTo130.put("formMappingRule", Collections.<String>emptyList());

        tagsStructurePriorTo130.put("downloadGoal", Collections.singletonList("goal"));
        tagsStructurePriorTo130.put("formGoal", Collections.singletonList("goal"));
        tagsStructurePriorTo130.put("funnelGoal", Collections.singletonList("goal"));
        tagsStructurePriorTo130.put("landingPageGoal", Collections.singletonList("goal"));
        tagsStructurePriorTo130.put("pageVisitGoal", Collections.singletonList("goal"));
        tagsStructurePriorTo130.put("videoGoal", Collections.singletonList("goal"));

        tagsStructurePriorTo130.put("aggregated", Collections.singletonList("profileTags"));
        tagsStructurePriorTo130.put("autocompleted", Collections.singletonList("profileTags"));
        tagsStructurePriorTo130.put("demographic", Collections.singletonList("profileTags"));
        tagsStructurePriorTo130.put("event", Collections.singletonList("profileTags"));
        tagsStructurePriorTo130.put("geographic", Collections.singletonList("profileTags"));
        tagsStructurePriorTo130.put("logical", Collections.singletonList("profileTags"));

        tagsStructurePriorTo130.put("profileProperties", Collections.singletonList("properties"));
        tagsStructurePriorTo130.put("systemProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo130.put("basicProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo130.put("leadProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo130.put("contactProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo130.put("socialProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo130.put("personalProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo130.put("workProfileProperties", Arrays.asList("properties", "profileProperties"));
        tagsStructurePriorTo130.put("personalIdentifierProperties", Arrays.asList("properties", "profileProperties"));

        tagsStructurePriorTo130.put("sessionProperties", Collections.singletonList("properties"));
        tagsStructurePriorTo130.put("geographicSessionProperties", Arrays.asList("properties", "sessionProperties"));
        tagsStructurePriorTo130.put("technicalSessionProperties", Arrays.asList("properties", "sessionProperties"));
    }
}
