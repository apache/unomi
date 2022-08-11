import groovy.json.JsonSlurper
import org.apache.unomi.shell.migration.utils.ConsoleUtils
import org.apache.unomi.shell.migration.utils.HttpUtils
import org.apache.unomi.shell.migration.utils.MigrationUtils

import java.time.Instant

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

Instant migrationTime = Instant.now();
def jsonSlurper = new JsonSlurper()
String aliasSaveBulkRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/alias_save_bulk.ndjson");
String esAddress = migrationConfig.getString("esAddress", session)
String indexPrefix = migrationConfig.getString("indexPrefix", session)
String aliasIndex = indexPrefix + "-profilealias"
String profileIndex = indexPrefix + "-profile"

// create alias index
if (!MigrationUtils.indexExists(httpClient, esAddress, aliasIndex)) {
    String baseRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/base_index_mapping.json")
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "profileAlias.json")
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(httpClient, esAddress, baseRequest, profileIndex, mapping)
    HttpUtils.executePutRequest(httpClient, esAddress + "/" + aliasIndex, newIndexSettings, null)

    // scroll search on profiles merged
    String profileMergedSearchRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/profile_merged_search.json")
    MigrationUtils.scrollQuery(httpClient, esAddress, "/" + profileIndex + "/_search", profileMergedSearchRequest, "1h", new MigrationUtils.ScrollCallback() {
        @Override
        void execute(String hits) {
            // create aliases for those merged profiles and delete them.
            def jsonHits = jsonSlurper.parseText(hits)
            ConsoleUtils.printMessage(session, "Detected: " + jsonHits.size() + " profile alias to create")
            final StringBuilder bulkSaveRequest = new StringBuilder()
            jsonHits.each {
                jsonHit -> {
                    // check that master still exists before creating alias:
                    def masterProfile = jsonSlurper.parseText(HttpUtils.executeGetRequest(httpClient, esAddress + "/" + profileIndex + "/_doc/" + jsonHit._source.mergedWith, null))
                    if (masterProfile.found) {
                        bulkSaveRequest.append(aliasSaveBulkRequest
                                .replace("##itemId##", jsonHit._source.itemId)
                                .replace("##profileId##", jsonHit._source.mergedWith)
                                .replace("##migrationTime##", migrationTime.toString()))
                    }
                }
            }
            if (bulkSaveRequest.length() > 0) {
                HttpUtils.executePostRequest(httpClient, esAddress + "/" + aliasIndex + "/_bulk", bulkSaveRequest.toString(), null)
            }
        }
    })

    // delete existing merged profiles
    String profileMergedDeleteRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/profile_merged_delete.json")
    HttpUtils.executePostRequest(httpClient, esAddress + "/" + profileIndex + "/_delete_by_query", profileMergedDeleteRequest, null)
}