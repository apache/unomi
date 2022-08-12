import groovy.json.JsonSlurper
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.unomi.shell.migration.actions.MigrationHistory
import org.apache.unomi.shell.migration.utils.ConsoleUtils
import org.apache.unomi.shell.migration.utils.HttpRequestException
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

MigrationHistory history = migrationHistory
CloseableHttpClient client = httpClient
Instant migrationTime = Instant.now()
def jsonSlurper = new JsonSlurper()
String esAddress = migrationConfig.getString("esAddress", session)
String indexPrefix = migrationConfig.getString("indexPrefix", session)
String aliasIndex = indexPrefix + "-profilealias"
String profileIndex = indexPrefix + "-profile"


history.performMigrationStep("2.0.0-create-profileAlias-index", () -> {
    if (!MigrationUtils.indexExists(client, esAddress, aliasIndex)) {
        String baseRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/base_index_mapping.json")
        String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "profileAlias.json")
        String newIndexSettings = MigrationUtils.buildIndexCreationRequest(client, esAddress, baseRequest, profileIndex, mapping)
        HttpUtils.executePutRequest(client, esAddress + "/" + aliasIndex, newIndexSettings, null)
    }
})

history.performMigrationStep("2.0.0-create-aliases-for-existing-merged-profiles", () -> {
    String aliasSaveBulkRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/alias_save_bulk.ndjson");
    String profileMergedSearchRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/profile_merged_search.json")

    MigrationUtils.scrollQuery(client, esAddress, "/" + profileIndex + "/_search", profileMergedSearchRequest, "1h", hits -> {
        // create aliases for those merged profiles and delete them.
        def jsonHits = jsonSlurper.parseText(hits)
        ConsoleUtils.printMessage(session, "Detected: " + jsonHits.size() + " existing profiles merged")
        final StringBuilder bulkSaveRequest = new StringBuilder()

        jsonHits.each {
            jsonHit -> {
                // check that master still exists and that no alias exist for this profile yet
                def mergedProfileId = jsonHit._source.itemId
                def masterProfileId = jsonHit._source.mergedWith
                def masterProfileExists = false
                def aliasAlreadyExists = false

                try {
                    def masterProfile = jsonSlurper.parseText(HttpUtils.executeGetRequest(client, esAddress + "/" + profileIndex + "/_doc/" + masterProfileId, null))
                    masterProfileExists = masterProfile.found
                } catch (HttpRequestException e) {
                    // can happen in case response code > 400 due to item not exist in ElasticSearch
                }

                try {
                    def existingAlias = jsonSlurper.parseText(HttpUtils.executeGetRequest(client, esAddress + "/" + aliasIndex + "/_doc/" + mergedProfileId, null));
                    aliasAlreadyExists = existingAlias.found
                } catch (HttpRequestException e) {
                    // can happen in case of response code > 400 due to item not exist in ElasticSearch
                }

                if (masterProfileExists && !aliasAlreadyExists) {
                    bulkSaveRequest.append(aliasSaveBulkRequest
                            .replace("##itemId##", mergedProfileId)
                            .replace("##profileId##", masterProfileId)
                            .replace("##migrationTime##", migrationTime.toString()))
                }
            }
        }

        if (bulkSaveRequest.length() > 0) {
            HttpUtils.executePostRequest(client, esAddress + "/" + aliasIndex + "/_bulk", bulkSaveRequest.toString(), null)
        }
    })
})

history.performMigrationStep("2.0.0-delete-existing-merged-profiles", () -> {
    String profileMergedDeleteRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/profile_merged_delete.json")
    HttpUtils.executePostRequest(client, esAddress + "/" + profileIndex + "/_delete_by_query", profileMergedDeleteRequest, null)
})