import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.unomi.shell.migration.service.MigrationContext
import org.apache.unomi.shell.migration.utils.HttpUtils
import org.apache.unomi.shell.migration.utils.MigrationUtils
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import java.util.Base64

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

// UNOMI-938: earlier versions stored API keys in plaintext, in the "key" field of each entry
// of a tenant's "apiKeys" array. As of 3.1.0, only a PBKDF2 hash ("keyHash") and a display-safe
// masked value ("maskedKey") are persisted; the plaintext is never stored. A tenant document
// with a still-plaintext "key" field keeps working via TenantServiceImpl's legacy-key fallback,
// so this migration is not mandatory for correctness, but it closes the plaintext-at-rest exposure
// window by rehashing every legacy key still found in Elasticsearch and removing the plaintext value.

MigrationContext context = migrationContext
String esAddress = context.getConfigString("esAddress")
String indexPrefix = context.getConfigString("indexPrefix")
def jsonSlurper = new JsonSlurper()

// Hashes a plaintext API key the same way ApiKeyHashServiceImpl does (PBKDF2WithHmacSHA512,
// 600000 iterations, format "iterations:base64(salt):base64(hash)").
def hashApiKey = { String plainTextKey ->
    int iterations = 600_000
    int saltLengthBytes = 16
    int hashLengthBits = 256
    SecureRandom rng = new SecureRandom()
    byte[] salt = new byte[saltLengthBytes]
    rng.nextBytes(salt)
    PBEKeySpec spec = new PBEKeySpec(plainTextKey.toCharArray(), salt, iterations, hashLengthBits)
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    byte[] hash = factory.generateSecret(spec).getEncoded()
    return "${iterations}:${Base64.encoder.encodeToString(salt)}:${Base64.encoder.encodeToString(hash)}"
}

// Masks a plaintext API key the same way ApiKeyHashServiceImpl does: "unomi_v1_****LAST4".
def maskApiKey = { String plainTextKey ->
    String withoutPrefix = plainTextKey.startsWith("unomi_v1_") ? plainTextKey.substring(9) : plainTextKey
    String lastFour = withoutPrefix.length() >= 4 ? withoutPrefix.substring(withoutPrefix.length() - 4) : withoutPrefix
    return "unomi_v1_****${lastFour}"
}

context.performMigrationStep("3.1.0-hash-legacy-api-keys", () -> {
    String tenantIndex = "${indexPrefix}-tenant"

    if (!MigrationUtils.indexExists(context.getHttpClient(), esAddress, tenantIndex)) {
        context.printMessage("Tenant index does not exist, skipping API key hashing")
        return
    }

    context.printMessage("Scanning tenant index for legacy plaintext API keys to rehash")

    String scrollQuery = JsonOutput.toJson([query: [match_all: [:]], size: 100])
    int tenantsProcessed = 0
    int tenantsUpdated = 0
    int keysRehashed = 0

    MigrationUtils.scrollQuery(context.getHttpClient(), esAddress, "/${tenantIndex}/_search", scrollQuery, "5m", (hits) -> {
        def hitsArray = jsonSlurper.parseText(hits)
        StringBuilder bulkUpdate = new StringBuilder()

        hitsArray.each { hit ->
            tenantsProcessed++
            List apiKeys = hit._source?.apiKeys
            if (apiKeys == null || apiKeys.isEmpty()) {
                return
            }

            boolean tenantChanged = false
            List newApiKeys = apiKeys.collect { apiKey ->
                String legacyKey = apiKey.key
                if (legacyKey == null || apiKey.keyHash != null) {
                    // Already migrated (has a hash) or nothing to migrate; leave untouched.
                    return apiKey
                }
                Map rehashedKey = new LinkedHashMap(apiKey)
                rehashedKey.remove("key")
                rehashedKey.keyHash = hashApiKey(legacyKey)
                rehashedKey.maskedKey = maskApiKey(legacyKey)
                tenantChanged = true
                keysRehashed++
                return rehashedKey
            }

            if (tenantChanged) {
                String tenantId = hit._id
                bulkUpdate.append(JsonOutput.toJson([update: [_id: tenantId, _index: hit._index]])).append("\n")
                bulkUpdate.append(JsonOutput.toJson([doc: [apiKeys: newApiKeys]])).append("\n")
                tenantsUpdated++
            }
        }

        if (bulkUpdate.length() > 0) {
            try {
                MigrationUtils.bulkUpdate(context.getHttpClient(), esAddress + "/_bulk", bulkUpdate.toString())
            } catch (Exception e) {
                context.printMessage("Error rehashing API keys for a batch of tenants: ${e.message}")
            }
        }
    })

    if (tenantsUpdated > 0) {
        HttpUtils.executePostRequest(context.getHttpClient(), esAddress + "/${tenantIndex}/_refresh", null, null)
    }

    context.printMessage("Processed ${tenantsProcessed} tenant(s): rehashed ${keysRehashed} legacy API key(s) across ${tenantsUpdated} tenant(s)")
})
