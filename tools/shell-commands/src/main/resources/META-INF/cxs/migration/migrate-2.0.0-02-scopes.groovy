import groovy.json.JsonSlurper
import org.apache.unomi.shell.migration.utils.ConsoleUtils
import org.apache.unomi.shell.migration.utils.HttpUtils
import org.apache.unomi.shell.migration.utils.MigrationUtils

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

def jsonSlurper = new JsonSlurper()
String searchScopesRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/scope_search.json")
String saveScopeRequestBulk = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/scope_save_bulk.ndjson")
String esAddress = migrationConfig.getString("esAddress", session)
String indexPrefix = migrationConfig.getString("indexPrefix", session)
String scopeIndex = indexPrefix + "-scope"

// Create scope index:
if (!MigrationUtils.indexExists(httpClient, esAddress, scopeIndex)) {
    String baseRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/base_index_mapping.json")
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "scope.json")
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(httpClient, esAddress, baseRequest, indexPrefix + "-profile", mapping)
    HttpUtils.executePutRequest(httpClient, esAddress + "/" + scopeIndex, newIndexSettings, null)

    // search existing scopes from event
    def searchResponse = jsonSlurper.parseText(HttpUtils.executePostRequest(httpClient, esAddress + "/" + indexPrefix + "-event-*/_search", searchScopesRequest, null))
    ConsoleUtils.printMessage(session, "Detected: " + searchResponse.aggregations.bucketInfos.count + " scopes to create")

    // create scopes
    def buckets = searchResponse.aggregations.scopes.buckets
    if (buckets != null && buckets.size() > 0) {
        final StringBuilder bulkSaveRequest = new StringBuilder()
        buckets.each {
            bucket -> bulkSaveRequest.append(saveScopeRequestBulk.replace("##scope##", bucket.key))
        }
        HttpUtils.executePostRequest(httpClient, esAddress + "/" + scopeIndex + "/_bulk", bulkSaveRequest.toString(), null)
    }
}