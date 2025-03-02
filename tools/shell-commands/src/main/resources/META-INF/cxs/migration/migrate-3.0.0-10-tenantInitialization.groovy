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

// Create the default tenant index and items
context.performMigrationStep("3.0.0-create-tenant-index", () -> {
    String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "tenant.json")
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)
    
    if (!MigrationUtils.indexExists(context.getHttpClient(), esAddress, "${indexPrefix}-tenant")) {
        context.printMessage("Creating tenant index: ${indexPrefix}-tenant")
        MigrationUtils.createIndex(context.getHttpClient(), esAddress, "${indexPrefix}-tenant", newIndexSettings)
        
        // Create the default tenant (this might be adjusted based on actual tenant structure)
        String defaultTenantJson = """{
            "itemId": "default",
            "itemType": "tenant",
            "scope": "tenant",
            "name": "Default Tenant",
            "description": "Default tenant created during migration to Unomi V3",
            "createdBy": "system-migration-3.0.0",
            "lastModifiedBy": "system-migration-3.0.0",
            "creationDate": ${System.currentTimeMillis()},
            "lastModificationDate": ${System.currentTimeMillis()},
            "version": 1,
            "enabled": true
        }"""
        
        MigrationUtils.indexData(context.getHttpClient(), esAddress, "${indexPrefix}-tenant", "_doc", "default", defaultTenantJson)
        context.printMessage("Created default tenant")
    } else {
        context.printMessage("Tenant index already exists: ${indexPrefix}-tenant")
    }
}) 