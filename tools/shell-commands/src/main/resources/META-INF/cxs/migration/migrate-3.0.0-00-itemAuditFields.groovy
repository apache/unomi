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

// Verify environment is ready for migration
context.performMigrationStep("3.0.0-environment-check", () -> {
    String elasticMajorVersion = MigrationUtils.getElasticMajorVersion(context.getHttpClient(), esAddress)
    context.printMessage("ElasticSearch major version: " + elasticMajorVersion)
    // We can add additional checks here if needed
})

// Get list of all index names
context.performMigrationStep("3.0.0-get-all-indices", () -> {
    Set<String> allIndices = MigrationUtils.getIndicesWithPrefix(context.getHttpClient(), esAddress, indexPrefix)
    context.printMessage("Found " + allIndices.size() + " indices with prefix " + indexPrefix)

    // Set default values for all audit fields in every index - the generic part
    String updateAuditFieldsScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/3.0.0/initialize_audit_fields.painless")
    
    allIndices.each { indexName ->
        if (indexName != "${indexPrefix}-geonames") { // Skip geonames index
            context.printMessage("Processing index: " + indexName)
            MigrationUtils.updateByQuery(context.getHttpClient(), bundleContext, esAddress, indexName, updateAuditFieldsScript, context, "3.0.0-update-audit-fields-" + indexName)
        }
    }
})

// Now handle profile and session indices specifically for any specializations they might need
context.performMigrationStep("3.0.0-profile-audit-fields", () -> {
    String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")
    String updateProfileScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/3.0.0/initialize_profile_audit_fields.painless")
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "profile.json")
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)
    MigrationUtils.reIndex(context.getHttpClient(), bundleContext, esAddress, "${indexPrefix}-profile", newIndexSettings, updateProfileScript, context, "3.0.0-profile-audit-fields")
})

// Update session indices
context.performMigrationStep("3.0.0-session-audit-fields", () -> {
    String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")
    String updateSessionScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/3.0.0/initialize_session_audit_fields.painless")
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "session.json")
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)
    
    // Handle all session indices (they might have date-based suffixes)
    Set<String> sessionIndices = MigrationUtils.getIndicesWithPrefix(context.getHttpClient(), esAddress, "${indexPrefix}-session")
    sessionIndices.each { sessionIndex ->
        MigrationUtils.reIndex(context.getHttpClient(), bundleContext, esAddress, sessionIndex, newIndexSettings, updateSessionScript, context, "3.0.0-session-audit-fields-" + sessionIndex)
    }
})

// Update events indices
context.performMigrationStep("3.0.0-event-audit-fields", () -> {
    String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")
    String updateEventScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/3.0.0/initialize_event_audit_fields.painless")
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "event.json")
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)

    // Handle all event indices (they might have date-based suffixes)
    Set<String> eventIndices = MigrationUtils.getIndicesWithPrefix(context.getHttpClient(), esAddress, "${indexPrefix}-event")
    eventIndices.each { eventIndex ->
        MigrationUtils.reIndex(context.getHttpClient(), bundleContext, esAddress, eventIndex, newIndexSettings, updateEventScript, context, "3.0.0-event-audit-fields-" + eventIndex)
    }
}) 