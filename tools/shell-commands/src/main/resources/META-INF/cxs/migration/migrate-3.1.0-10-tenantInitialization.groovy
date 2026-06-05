import org.apache.unomi.shell.migration.service.MigrationContext
import org.apache.unomi.shell.migration.utils.MigrationUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.SecureRandom
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
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

// Delete API key files older than 24 hours left by previous migration runs
Path secretsDir = Paths.get(System.getProperty("karaf.data", "data"), "migration", "secrets")
if (Files.exists(secretsDir)) {
    long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    Files.list(secretsDir)
         .filter { f -> f.toString().endsWith(".txt") }
         .filter { f -> Files.getLastModifiedTime(f).toMillis() < cutoff }
         .each   { f -> Files.delete(f); context.printMessage("Deleted expired key file: ${f.fileName}") }
}

// Create the default tenant index and items
context.performMigrationStep("3.1.0-create-tenant-index", () -> {
    String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "tenant.json")
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)

    if (!MigrationUtils.indexExists(context.getHttpClient(), esAddress, "${indexPrefix}-tenant")) {
        context.printMessage("Creating tenant index: ${indexPrefix}-tenant")
        MigrationUtils.createIndex(context.getHttpClient(), esAddress, "${indexPrefix}-tenant", newIndexSettings)

        // Generate unique API key values inside the step — only runs when the step actually executes
        SecureRandom rng = new SecureRandom()
        byte[] pubBytes  = new byte[32]; rng.nextBytes(pubBytes)
        byte[] privBytes = new byte[32]; rng.nextBytes(privBytes)
        String generatedPublicKey  = pubBytes.collect  { String.format('%02X', it) }.join()
        String generatedPrivateKey = privBytes.collect { String.format('%02X', it) }.join()
        String publicKeyId  = UUID.randomUUID().toString()
        String privateKeyId = UUID.randomUUID().toString()

        // Write keys to a time-limited file AND print to console — the only opportunity to record them
        Files.createDirectories(secretsDir)
        String safeDate = isoDate.replaceAll('[^a-zA-Z0-9-]', '-')
        Path keyFile = secretsDir.resolve("tenant-api-keys-${tenantId}-${safeDate}.txt")
        String sep = "=" * 70
        String fileContent = """\
${sep}
Unomi 3.1 Migration -- Tenant API Keys
${sep}
Generated : ${isoDate}
Tenant ID : ${tenantId}

PUBLIC KEY  (X-Unomi-Public-Key header -- context.json / event collector):
  ${generatedPublicKey}

PRIVATE KEY (X-Unomi-Key header -- protected / admin endpoints):
  ${generatedPrivateKey}

IMPORTANT: These keys cannot be recovered after this file is deleted.
           Save them in a password manager or secrets vault now.
           This file is automatically deleted 24 hours after creation
           by the next migration run. Delete it manually once saved.
${sep}
"""
        Files.write(keyFile, fileContent.getBytes("UTF-8"))

        context.printMessage(sep)
        context.printMessage("TENANT API KEYS -- SAVE THESE, THEY WILL NOT BE SHOWN AGAIN")
        context.printMessage("  Public key  : ${generatedPublicKey}")
        context.printMessage("  Private key : ${generatedPrivateKey}")
        context.printMessage("  Keys file   : ${keyFile}")
        context.printMessage("  (auto-deleted after 24 h; delete manually once keys are saved)")
        context.printMessage(sep)

        // Create the default tenant document
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
                  "itemId" : "${publicKeyId}",
                  "itemType" : "apiKey",
                  "createdBy": "system-migration-3.1.0",
                  "lastModifiedBy": "system-migration-3.1.0",
                  "creationDate" : "${isoDate}",
                  "lastModificationDate" : "${isoDate}",
                  "key" : "${generatedPublicKey}",
                  "keyType" : "PUBLIC",
                  "revoked" : false
                },
                {
                  "itemId" : "${privateKeyId}",
                  "itemType" : "apiKey",
                  "createdBy": "system-migration-3.1.0",
                  "lastModifiedBy": "system-migration-3.1.0",
                  "creationDate" : "${isoDate}",
                  "lastModificationDate" : "${isoDate}",
                  "key" : "${generatedPrivateKey}",
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
