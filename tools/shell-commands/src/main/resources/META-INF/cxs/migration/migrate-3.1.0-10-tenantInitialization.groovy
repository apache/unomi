import org.apache.unomi.shell.migration.service.MigrationContext
import org.apache.unomi.shell.migration.utils.MigrationUtils
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
String esAddress = context.getConfigString("esAddress")
String indexPrefix = context.getConfigString("indexPrefix")
String tenantId = context.getConfigString(TENANT_ID)
ZonedDateTime unifiedDate = ZonedDateTime.now()
String isoDate = unifiedDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

// Create the default tenant index and items
context.performMigrationStep("3.1.0-create-tenant-index", () -> {
    String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "tenant.json")
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)

    if (!MigrationUtils.indexExists(context.getHttpClient(), esAddress, "${indexPrefix}-tenant")) {
        context.printMessage("Creating tenant index: ${indexPrefix}-tenant")
        MigrationUtils.createIndex(context.getHttpClient(), esAddress, "${indexPrefix}-tenant", newIndexSettings)

        // Create the default tenant (this might be adjusted based on actual tenant structure)
        String defaultTenantJson = """{
            "itemId": "${tenantId}",
            "itemType": "tenant",
            "name": "Default Tenant",
            "tenantId": "system",
            "description": "Default tenant created during migration to Unomi V3",
            "createdBy": "system-migration-3.1.0",
            "lastModifiedBy": "system-migration-3.1.0",
            "creationDate": "${isoDate}",
            "lastModificationDate": "${isoDate}",
            "version": 1,
            "status": "ACTIVE",
            "apiKeys" : [
                {
                  "itemId" : "5a3f11a8-38a7-41b0-9fe8-d1ef0b4ad8ca",
                  "itemType" : "apiKey",
                  "createdBy": "system-migration-3.1.0",
                  "lastModifiedBy": "system-migration-3.1.0",
                  "creationDate" : "${isoDate}",
                  "lastModificationDate" : "${isoDate}",
                  "key" : "C606D77D1D219509637A82C062BCD17F13D6DF1501702DC396D4A12D63D4E5F2",
                  "keyType" : "PUBLIC",
                  "revoked" : false
                },
                {
                  "itemId" : "3c595ea8-000e-4d0b-a329-0d259cc4d176",
                  "itemType" : "apiKey",
                  "createdBy": "system-migration-3.1.0",
                  "lastModifiedBy": "system-migration-3.1.0",
                  "creationDate" : "${isoDate}",
                  "lastModificationDate" : "${isoDate}",
                  "key" : "503BAABB3A14AEB4B50ACF3C82982FBABECDBAEA83879CA8AECA016A6A9EEA85",
                  "keyType" : "PRIVATE",
                  "revoked" : false
                }
            ],
            "properties" : { },
            "restrictedEventTypes" : [ ],
            "authorizedIPs" : [ ]            
        }"""

        MigrationUtils.indexData(context.getHttpClient(), esAddress, "${indexPrefix}-tenant", "_doc", "system_" + tenantId, defaultTenantJson)
        context.printMessage("Created default tenant")
    } else {
        context.printMessage("Tenant index already exists: ${indexPrefix}-tenant")
    }
})
