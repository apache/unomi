import org.apache.unomi.shell.migration.service.MigrationContext
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
String esAddress = context.getConfigString("esAddress")
String indexPrefix = context.getConfigString("indexPrefix")
String rolloverPolicyName = indexPrefix + "-unomi-rollover-policy"
String rolloverSessionAlias = indexPrefix + "-session"

context.performMigrationStep("2.5.0-clean-profile-mapping", () -> {
    String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")
    String updatePastEventScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/2.5.0/update_pastEvents_profile.painless")
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "profile.json")
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)
    MigrationUtils.reIndex(context.getHttpClient(), bundleContext, esAddress, indexPrefix + "-profile", newIndexSettings, updatePastEventScript, context, "2.5.0-clean-profile-mapping")
})

context.performMigrationStep("2.5.0-clean-session-mapping", () -> {
    String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.2.0/base_index_withRollover_request.json")
    String cleanPastEventScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/2.5.0/remove_pastEvents_session.painless")
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "session.json")
    String newIndexSettings = MigrationUtils.buildIndexCreationRequestWithRollover(baseSettings, mapping, context, rolloverPolicyName, rolloverSessionAlias)
    Set<String> sessionIndices = MigrationUtils.getIndexesPrefixedBy(context.getHttpClient(), esAddress, "${indexPrefix}-session-")
    String configureAliasBody = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.2.0/configure_alias_body.json")

    Set<String> sortedSet = new TreeSet<>(sessionIndices)
    sortedSet.each { sessionIndex ->
        MigrationUtils.reIndex(context.getHttpClient(), bundleContext, esAddress, sessionIndex, newIndexSettings, cleanPastEventScript, context, "2.5.0-clean-session-mapping")
    }
    SortedSet<String> allExceptLast = Collections.emptySortedSet();
    if (sortedSet.size() > 1){
         allExceptLast = sortedSet.headSet(sortedSet.last());
    }
    MigrationUtils.configureAlias(context.getHttpClient(), esAddress, rolloverSessionAlias, sortedSet.last(), allExceptLast, configureAliasBody, context)
})
