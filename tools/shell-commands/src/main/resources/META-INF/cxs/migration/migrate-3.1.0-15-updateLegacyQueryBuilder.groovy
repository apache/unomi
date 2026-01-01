import org.apache.unomi.shell.migration.service.MigrationContext
import org.apache.unomi.shell.migration.utils.HttpUtils
import org.apache.unomi.shell.migration.utils.MigrationUtils
import org.json.JSONArray
import org.json.JSONObject

import static org.apache.unomi.shell.migration.service.MigrationConfig.CONFIG_ES_ADDRESS
import static org.apache.unomi.shell.migration.service.MigrationConfig.INDEX_PREFIX

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

// This migration updates all condition types that still use the legacy *ESQueryBuilder syntax
// and replaces them with the proper generic QueryBuilder syntax.
// Uses pattern matching to find any queryBuilder ending with "ESQueryBuilder" and replace
// it with "QueryBuilder" (e.g., "propertyConditionESQueryBuilder" → "propertyConditionQueryBuilder").
// This approach is more robust than a hardcoded list and will catch all legacy IDs, including
// custom ones that might have been created by plugins.
context.performMigrationStep("3.1.0-update-legacy-querybuilder", () -> {
    String systemItemsIndex = "${indexPrefix}-systemitems"

    if (MigrationUtils.indexExists(context.getHttpClient(), esAddress, systemItemsIndex)) {
        context.printMessage("Updating condition types with legacy queryBuilder IDs in systemitems index")

        // Get the Painless script from file
        String updateScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/3.1.0/update_legacy_querybuilder.painless")

        // Build the update request using JSONObject to properly escape the script
        JSONObject scriptObj = new JSONObject()
        scriptObj.put("source", updateScript)
        scriptObj.put("lang", "painless")

        // Query for condition types with legacy queryBuilder IDs
        JSONObject queryObj = new JSONObject()
        JSONObject boolObj = new JSONObject()
        JSONArray mustArray = new JSONArray()

        // Match condition types - handle both "conditionType" and "conditiontype" casings
        // Note: itemType can be stored with different casings, so we use a should clause
        // to match either variant. The queryBuilder wildcard will catch all legacy IDs regardless.
        JSONObject itemTypeBool = new JSONObject()
        JSONArray shouldItemTypeArray = new JSONArray()

        JSONObject termItemType1 = new JSONObject()
        JSONObject termItemTypeValue1 = new JSONObject()
        termItemTypeValue1.put("itemType.keyword", "conditionType")
        termItemType1.put("term", termItemTypeValue1)
        shouldItemTypeArray.put(termItemType1)

        JSONObject termItemType2 = new JSONObject()
        JSONObject termItemTypeValue2 = new JSONObject()
        termItemTypeValue2.put("itemType.keyword", "conditiontype")
        termItemType2.put("term", termItemTypeValue2)
        shouldItemTypeArray.put(termItemType2)

        itemTypeBool.put("should", shouldItemTypeArray)
        itemTypeBool.put("minimum_should_match", 1)
        JSONObject itemTypeBoolWrapper = new JSONObject()
        itemTypeBoolWrapper.put("bool", itemTypeBool)
        mustArray.put(itemTypeBoolWrapper)

        // Match any queryBuilder ending with "ESQueryBuilder" using a wildcard query
        // This is more robust than a hardcoded list and will catch all legacy IDs
        JSONObject wildcardQueryBuilder = new JSONObject()
        JSONObject wildcardQueryBuilderValue = new JSONObject()
        wildcardQueryBuilderValue.put("queryBuilder.keyword", "*ESQueryBuilder")
        wildcardQueryBuilder.put("wildcard", wildcardQueryBuilderValue)
        mustArray.put(wildcardQueryBuilder)

        boolObj.put("must", mustArray)
        queryObj.put("bool", boolObj)

        JSONObject updateRequestObj = new JSONObject()
        updateRequestObj.put("script", scriptObj)
        updateRequestObj.put("query", queryObj)

        String updateRequest = updateRequestObj.toString()

        try {
            context.printMessage("Updating condition types with legacy queryBuilder IDs...")
            String updateResponse = MigrationUtils.updateByQuery(context.getHttpClient(), esAddress, systemItemsIndex, updateRequest)
            context.printMessage("Update response: ${updateResponse}")

            // Parse response to get update count
            try {
                JSONObject responseObj = new JSONObject(updateResponse)
                if (responseObj.has("updated")) {
                    int updatedCount = responseObj.getInt("updated")
                    context.printMessage("Successfully updated ${updatedCount} condition type(s) with legacy queryBuilder IDs")
                } else if (responseObj.has("total")) {
                    int totalCount = responseObj.getInt("total")
                    context.printMessage("Found ${totalCount} condition type(s) to update")
                }
            } catch (Exception parseException) {
                context.printMessage("Could not parse update response, but update completed")
            }

            context.printMessage("Successfully updated condition types with legacy queryBuilder IDs")
        } catch (Exception e) {
            context.printException("Error updating condition types with legacy queryBuilder IDs", e)
            throw e
        }

        // Refresh the index to make changes visible
        HttpUtils.executePostRequest(context.getHttpClient(), esAddress + "/${systemItemsIndex}/_refresh", null, null)
        context.printMessage("Migration completed: Updated condition types with legacy queryBuilder IDs")
    } else {
        context.printMessage("Systemitems index does not exist, skipping legacy queryBuilder update")
    }
})

