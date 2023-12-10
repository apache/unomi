import groovy.json.JsonSlurper
import org.apache.unomi.shell.migration.service.MigrationContext
import org.apache.unomi.shell.migration.utils.HttpRequestException
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

MigrationContext context = migrationContext
def jsonSlurper = new JsonSlurper()
String searchScopesRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.0.0/scope_search.json")
String saveScopeRequestBulk = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/scope_save_bulk.ndjson")
String esAddress = context.getConfigString("esAddress")
String indexPrefix = context.getConfigString("indexPrefix")
String scopeIndex = indexPrefix + "-scope"

context.performMigrationStep("2.0.0-create-scope-index", () -> {
    if (!MigrationUtils.indexExists(context.getHttpClient(), esAddress, scopeIndex)) {
        String baseRequest = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")
        String mapping = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/mappings/scope.json")
        String newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseRequest, mapping, context, false)
        HttpUtils.executePutRequest(context.getHttpClient(), esAddress + "/" + scopeIndex, newIndexSettings, null)
    }
})

context.performMigrationStep("2.0.0-create-scopes-from-existing-events", () -> {
    // search existing scopes from event
    def searchResponse = jsonSlurper.parseText(HttpUtils.executePostRequest(context.getHttpClient(), esAddress + "/" + indexPrefix + "-event-*/_search", searchScopesRequest, null))
    context.printMessage("Detected: " + searchResponse.aggregations.bucketInfos.count + " scopes to create")

    // create scopes
    def buckets = searchResponse.aggregations.scopes.buckets
    if (buckets != null && buckets.size() > 0) {
        final StringBuilder bulkSaveRequest = new StringBuilder()

        buckets.each {
            bucket -> {
                // Filter empty scope from existing events
                if (bucket.key) {
                    // check that the scope doesn't already exists
                    def scopeAlreadyExists = false
                    try {
                        def existingScope = jsonSlurper.parseText(HttpUtils.executeGetRequest(context.getHttpClient(), esAddress + "/" + scopeIndex + "/_doc/" + bucket.key, null));
                        scopeAlreadyExists = existingScope.found
                    } catch (HttpRequestException e) {
                        // can happen in case response code > 400 due to item not exist in ElasticSearch
                    }

                    if (!scopeAlreadyExists) {
                        context.printMessage("Scope: " + bucket.key + " will be created")
                        bulkSaveRequest.append(saveScopeRequestBulk.replace("##scope##", bucket.key))
                    } else {
                        context.printMessage("Scope: " + bucket.key + " already exists, won't be created")
                    }
                }
            }
        }

        if (bulkSaveRequest.length() > 0) {

            String response = HttpUtils.executePostRequest(context.getHttpClient(), esAddress + "/" + scopeIndex + "/_bulk", bulkSaveRequest.toString(), null)
            System.out.println("Scope created : " + response)
        }
    }
})