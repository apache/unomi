import org.apache.unomi.shell.migration.service.MigrationContext
import org.apache.unomi.shell.migration.utils.MigrationUtils
import org.apache.unomi.shell.migration.utils.HttpUtils
import org.json.JSONObject
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

// Get all system item types
Set<String> systemItems = MigrationUtils.getAllItemTypes(context.getHttpClient(), esAddress, indexPrefix, "systemitems", bundleContext)
context.printMessage("Found " + systemItems.size() + " system item types")

// Fix itemIds in systemitems index that may have been incorrectly processed by migration 3.1.0-00
// The 3.1.0-00 migration script had a bug where it split baseId on underscore and took only the first part,
// causing itemIds like "dummy_scope" to become "dummy" when constructing document IDs.
// This migration fixes items where itemId in source doesn't match what it should be based on the document ID.
// Note: Migration 2.2.0 intentionally sets itemId = documentId (with suffix), which is fine because
// setMetadata() extracts the correct itemId from the document ID. However, if the 3.1.0-00 migration
// incorrectly processed the baseId, we need to fix the itemId in the source to match the document ID.
context.performMigrationStep("3.1.0-fix-system-item-ids", () -> {
    String systemItemsIndex = "${indexPrefix}-systemitems"
    
    if (MigrationUtils.indexExists(context.getHttpClient(), esAddress, systemItemsIndex)) {
        context.printMessage("Fixing itemIds in systemitems index that end with itemType suffix")
        
        // Process each system item type
        systemItems.each { itemType ->
            context.printMessage("Fixing items of type: ${itemType}")
            
            // Get the Painless script from file
            String fixScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/3.1.0/fix_system_item_ids.painless")
            
            // Build the update request using JSONObject to properly escape the script
            // This is the same approach used in MigrationUtils.getScriptPart() and other migrations
            JSONObject scriptObj = new JSONObject()
            scriptObj.put("source", fixScript)
            scriptObj.put("lang", "painless")
            
            JSONObject queryObj = new JSONObject()
            JSONObject termObj = new JSONObject()
            termObj.put("itemType", itemType)
            queryObj.put("term", termObj)
            
            JSONObject updateRequestObj = new JSONObject()
            updateRequestObj.put("script", scriptObj)
            updateRequestObj.put("query", queryObj)
            
            String updateRequest = updateRequestObj.toString()
            
            try {
                MigrationUtils.updateByQuery(context.getHttpClient(), esAddress, systemItemsIndex, updateRequest)
                context.printMessage("Fixed itemIds for item type: ${itemType}")
            } catch (Exception e) {
                context.printMessage("Warning: Could not fix itemIds for item type ${itemType}: ${e.getMessage()}")
                // Continue with other item types even if one fails
            }
        }
        
        // Refresh the index to make changes visible
        HttpUtils.executePostRequest(context.getHttpClient(), esAddress + "/${systemItemsIndex}/_refresh", null, null)
        context.printMessage("Fixed itemIds in systemitems index")
    } else {
        context.printMessage("Systemitems index does not exist, skipping itemId fix")
    }
})

