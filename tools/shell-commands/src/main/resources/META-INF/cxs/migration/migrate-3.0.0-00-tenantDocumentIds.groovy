import org.apache.unomi.shell.migration.service.MigrationContext
import org.apache.unomi.shell.migration.utils.MigrationUtils
import org.apache.unomi.shell.migration.utils.HttpUtils
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import static org.apache.unomi.shell.migration.service.MigrationConfig.*

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
String esAddress = context.getConfigString(CONFIG_ES_ADDRESS)
String indexPrefix = context.getConfigString(INDEX_PREFIX)
String tenantId = context.getConfigString(TENANT_ID)
String systemTenantId = "system" // System tenant ID for system-level items
String rolloverPolicyName = indexPrefix + "-unomi-rollover-policy"
String rolloverSessionAlias = indexPrefix + "-session"
String rolloverEventAlias = indexPrefix + "-event"
ZonedDateTime unifiedDate = ZonedDateTime.now()
String isoDate = unifiedDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

// Define index-specific configurations
def indexConfigs = [
    "profile": [
        baseSettings: "requestBody/2.0.0/base_index_mapping.json",
        mapping: "profile.json",
        useRollover: false
    ],
    "session": [
        baseSettings: "requestBody/2.2.0/base_index_withRollover_request.json",
        mapping: "session.json",
        useRollover: true,
        alias: { indexPrefix + "-session" }
    ],
    "event": [
        baseSettings: "requestBody/2.2.0/base_index_withRollover_request.json",
        mapping: "event.json",
        useRollover: true,
        alias: { indexPrefix + "-event" }
    ],
    "systemitems": [
        baseSettings: "requestBody/2.0.0/base_index_mapping.json",
        mapping: "systemItems.json",
        useRollover: false
    ],
    "geonameentry": [
        baseSettings: "requestBody/2.0.0/base_index_mapping.json",
        mapping: "geonameEntry.json",
        useRollover: false
    ],
    "personasession": [
        baseSettings: "requestBody/2.0.0/base_index_mapping.json",
        mapping: "personaSession.json",
        useRollover: false
    ],
    "generic": [
        baseSettings: "requestBody/2.0.0/base_index_mapping.json",
        mapping: null, // Will be determined dynamically from resolved item type
        useRollover: false
    ]
]

// Helper function to resolve item type from index name
def resolveItemType = { String indexName ->
    def type = indexConfigs.find { type, config ->
        indexName.startsWith("${indexPrefix}-${type}")
    }
    return type ? type.key : "generic"
}

// Helper function to get index configuration
def getIndexConfig = { String itemType ->
    return indexConfigs[itemType] ?: indexConfigs["generic"]
}

// Verify environment is ready for migration
context.performMigrationStep("3.0.0-environment-check", () -> {
    String elasticMajorVersion = MigrationUtils.getElasticMajorVersion(context.getHttpClient(), esAddress)
    context.printMessage("ElasticSearch major version: " + elasticMajorVersion)
})

// Get list of all index names and system items
context.performMigrationStep("3.0.0-get-all-indices", () -> {
    Set<String> allIndices = MigrationUtils.getIndexesPrefixedBy(context.getHttpClient(), esAddress, indexPrefix)
    context.printMessage("Found " + allIndices.size() + " indices with prefix " + indexPrefix)

    Set<String> allItemTypes = MigrationUtils.getAllItemTypes(context.getHttpClient(), esAddress, indexPrefix, "*", bundleContext)
    context.printMessage("Found " + allItemTypes.size() + " item types")

    // Get all system items from the systemitems index
    Set<String> systemItems = MigrationUtils.getAllItemTypes(context.getHttpClient(), esAddress, indexPrefix, "systemitems", bundleContext)
    context.printMessage("Found " + systemItems.size() + " system items")

    // Create base parameters
    Map<String, Object> baseParams = new HashMap<>()
    baseParams.put("date", isoDate)
    baseParams.put("tenantId", tenantId)
    baseParams.put("systemTenantId", systemTenantId)
    baseParams.put("systemItems", systemItems)

    context.printMessage("Using tenant ID: " + tenantId)

    // Get the Painless script
    String updateScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/3.0.0/initialize_tenant_and_audit_fields.painless")

    // Process each index
    allIndices.each { indexName ->
        context.printMessage("Processing index: " + indexName)
        
        // Determine item type and get configuration
        String itemType = resolveItemType(indexName)
        def indexConfig = getIndexConfig(itemType)
        
        // Add item type to parameters
        Map<String, Object> params = new HashMap<>(baseParams)
        params.put("itemType", itemType)
        
        // Get base settings and mapping
        String baseSettings = MigrationUtils.resourceAsString(bundleContext, indexConfig.baseSettings)
        String mapping = indexConfig.mapping ? 
            MigrationUtils.extractMappingFromBundles(bundleContext, indexConfig.mapping) :
            MigrationUtils.extractMappingFromBundles(bundleContext, "${itemType}.json")
        
        // Build index settings
        String newIndexSettings
        if (indexConfig.useRollover) {
            String alias = indexConfig.alias(indexPrefix)
            newIndexSettings = MigrationUtils.buildIndexCreationRequestWithRollover(baseSettings, mapping, context, rolloverPolicyName, alias)
        } else {
            newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)
        }
        
        // Execute reindex
        MigrationUtils.reIndex(context.getHttpClient(), bundleContext, esAddress, indexName, newIndexSettings, updateScript, params, context, "3.0.0-${itemType}-update")
    }
}) 