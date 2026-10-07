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
String systemTenantId = "system"
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
        "profileAlias": [
                baseSettings: "requestBody/2.0.0/base_index_mapping.json",
                mapping: "profileAlias.json",
                useRollover: false
        ],
        "clusterNode": [
                baseSettings: "requestBody/2.0.0/base_index_mapping.json",
                mapping: "clusterNode.json",
                useRollover: false,
                forceDefaultTenant: true
        ],
        "generic": [
                baseSettings: "requestBody/2.0.0/base_index_mapping.json",
                mapping: null, // Copy the mapping currently stored on the index
                useRollover: false,
                forceDefaultTenant: true
        ]
]

def getIndexConfig = { String itemType ->
    return indexConfigs[itemType] ?: indexConfigs["generic"]
}

// Verify environment is ready for migration
context.performMigrationStep("4.0.0-environment-check", () -> {
    String elasticMajorVersion = MigrationUtils.getElasticMajorVersion(context.getHttpClient(), esAddress)
    context.printMessage("ElasticSearch major version: " + elasticMajorVersion)
})

// Get list of all index names and system items
context.performMigrationStep("4.0.0-get-all-indices", () -> {
    Set<String> allIndices = MigrationUtils.getIndexesPrefixedBy(context.getHttpClient(), esAddress, indexPrefix)
    context.printMessage("Found " + allIndices.size() + " indices with prefix " + indexPrefix)

    Set<String> allItemTypes = MigrationUtils.getAllItemTypes(context.getHttpClient(), esAddress, indexPrefix, "*", bundleContext)
    context.printMessage("Found " + allItemTypes.size() + " item types")

    // Get all system items from the systemitems index (store lower-case for painless checks)
    Set<String> systemItems = MigrationUtils.getAllItemTypes(context.getHttpClient(), esAddress, indexPrefix, "systemitems", bundleContext)
            .collect { it.toLowerCase(java.util.Locale.ROOT) } as Set
    context.printMessage("Found " + systemItems.size() + " system items")

    Map<String, Set<String>> shippedDefinitionIds = MigrationUtils.loadShippedDefinitionIds(bundleContext)
    context.printMessage("Loaded " + shippedDefinitionIds.values().sum { it.size() } + " shipped definition ids")

    // Create base parameters
    Map<String, Object> baseParams = new HashMap<>()
    baseParams.put("date", isoDate)
    baseParams.put("tenantId", tenantId)
    baseParams.put("systemTenantId", systemTenantId)
    baseParams.put("systemItems", systemItems)
    baseParams.put("shippedDefinitionIds", shippedDefinitionIds)

    context.printMessage("Using tenant ID: " + tenantId)

    // Get the Painless script
    String updateScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/4.0.0/initialize_tenant_and_audit_fields.painless")

    String searchEngine = MigrationUtils.getSearchEngine(context.getHttpClient(), esAddress)
    context.printMessage("Search engine: " + searchEngine)

    // Process each index (reindex them)
    allIndices.each { indexName ->
        context.printMessage("Processing index: " + indexName)

        // Determine item type and get configuration
        String itemType = MigrationUtils.resolveItemType(indexName, indexPrefix, indexConfigs.keySet())
        def indexConfig = getIndexConfig(itemType)

        // Add item type to parameters
        Map<String, Object> params = new HashMap<>(baseParams)
        params.put("itemType", itemType)
        // Cluster nodes and unknown indices keep site data in the configured tenant, never "system"
        boolean forceDefaultTenant = indexConfig.forceDefaultTenant == true
        params.put("forceDefaultTenant", forceDefaultTenant)

        // Get base settings and mapping
        String baseSettings = MigrationUtils.resourceAsString(bundleContext, indexConfig.baseSettings)
        // A known type must find its bundled 4.0 mapping; only unknown types copy the mapping stored on the index
        String mapping = indexConfig.mapping ?
                MigrationUtils.extractMappingFromBundles(bundleContext, indexConfig.mapping, searchEngine) :
                MigrationUtils.extractMappingFromIndex(context.getHttpClient(), esAddress, indexName)
        context.printMessage("Item type: ${itemType}, mapping: ${indexConfig.mapping ?: 'copied from the index'}, default tenant only: ${forceDefaultTenant}")

        // Build index settings
        String newIndexSettings
        if (indexConfig.useRollover) {
            newIndexSettings = MigrationUtils.buildIndexCreationRequestWithRollover(baseSettings, mapping, context, rolloverPolicyName, indexConfig.alias(indexPrefix))
        } else {
            newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)
        }

        // Execute reindex
        MigrationUtils.reIndex(context.getHttpClient(), bundleContext, esAddress, indexName, newIndexSettings, updateScript, params, context, "4.0.0-${indexName}-update")
    }
})

// Configure aliases for rollover indices after all reindexing is complete.
// Top-level step so resume after failure can run alias configuration even when
// "4.0.0-get-all-indices" is already marked COMPLETED (UNOMI-943).
context.performMigrationStep("4.0.0-configure-rollover-aliases", () -> {
    String configureAliasBody = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.2.0/configure_alias_body.json")

    // Process each rollover item type
    indexConfigs.each { itemType, config ->
        if (config.useRollover) {
            String alias = config.alias(indexPrefix)
            // Find all indices that match the rollover pattern (e.g., context-session-000001, context-session-000002)
            Set<String> rolloverIndices = MigrationUtils.getIndexesPrefixedBy(context.getHttpClient(), esAddress, "${indexPrefix}-${itemType}-")

            if (!rolloverIndices.isEmpty()) {
                // Sort indices to find the latest one (highest number)
                SortedSet<String> sortedIndices = new TreeSet<>(rolloverIndices)
                String writeIndex = sortedIndices.last()

                // All indices except the last one should be read-only
                SortedSet<String> readIndices = Collections.emptySortedSet()
                if (sortedIndices.size() > 1) {
                    readIndices = sortedIndices.headSet(sortedIndices.last())
                }

                context.printMessage("Configuring alias ${alias}: write index=${writeIndex}, read indices=${readIndices}")
                MigrationUtils.configureAlias(context.getHttpClient(), esAddress, alias, writeIndex, readIndices, configureAliasBody, context)
            }
        }
    }
})
