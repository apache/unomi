import groovy.json.JsonSlurper
import org.apache.unomi.shell.migration.service.MigrationContext
import org.apache.unomi.shell.migration.utils.HttpUtils
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
def jsonSlurper = new JsonSlurper()

context.performMigrationStep("3.1.0-fix-profile-nbOfVisits", () -> {
    String profileIndex = "${indexPrefix}-profile"
    String sessionIndex = "${indexPrefix}-session-*"

    context.printMessage("Starting migration to fix Profile.nbOfVisits field")

    // First step: Copy nbOfVisits to totalNbOfVisits for all profiles
    context.printMessage("Step 1: Copying nbOfVisits to totalNbOfVisits")
    String copyScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/3.1.0/copy_nbOfVisits_to_totalNbOfVisits.painless")
    String copyRequestBody = MigrationUtils.resourceAsString(bundleContext, "requestBody/3.1.0/profile_copy_nbOfVisits_request.json")
    MigrationUtils.updateByQuery(context.getHttpClient(), esAddress, profileIndex, copyRequestBody.replace('#painless', copyScript))

    context.printMessage("Step 1 completed: nbOfVisits copied to totalNbOfVisits")

    // Second step: Update nbOfVisits with actual session count for each profile
    context.printMessage("Step 2: Updating nbOfVisits with actual session count")

    String scrollQuery = MigrationUtils.resourceAsString(bundleContext, "requestBody/3.1.0/profile_scroll_query.json")
    int profilesProcessed = 0
    int profilesUpdated = 0

    // Scroll through all profiles
    MigrationUtils.scrollQuery(context.getHttpClient(), esAddress, "/${profileIndex}/_search", scrollQuery, "5m", (hits) -> {
        def hitsArray = jsonSlurper.parseText(hits)
        StringBuilder bulkUpdate = new StringBuilder()

        hitsArray.each { hit ->
            String profileId = hit._id
            profilesProcessed++

            if (profilesProcessed % 10000 == 0) {
                context.printMessage("Processed ${profilesProcessed} profiles...")
            }

            // Count sessions for this profile
            String countQuery = MigrationUtils.resourceAsString(bundleContext, "requestBody/3.1.0/count_sessions_by_profile.json")
            String countQueryWithProfileId = countQuery.replace('#profileId', profileId)

            try {
                def countResponse = jsonSlurper.parseText(
                    HttpUtils.executePostRequest(context.getHttpClient(), esAddress + "/${sessionIndex}/_count", countQueryWithProfileId, null)
                )

                int sessionCount = countResponse.count

                // Prepare bulk update
                bulkUpdate.append('{"update":{"_id":"').append(profileId).append('","_index":"').append(profileIndex).append('"}}\n')
                bulkUpdate.append('{"doc":{"properties":{"nbOfVisits":').append(sessionCount).append('}}}\n')

                profilesUpdated++
            } catch (Exception e) {
                context.printMessage("Error counting sessions for profile ${profileId}: ${e.message}")
            }
        }

        // Execute bulk update if we have updates
        if (bulkUpdate.length() > 0) {
            try {
                MigrationUtils.bulkUpdate(context.getHttpClient(), esAddress + "/_bulk", bulkUpdate.toString())
            } catch (Exception e) {
                context.printMessage("Error during bulk update: ${e.message}")
            }
        }
    })

    // Refresh the profile index
    HttpUtils.executePostRequest(context.getHttpClient(), esAddress + "/${profileIndex}/_refresh", null, null)

    context.printMessage("Migration completed: Processed ${profilesProcessed} profiles, updated ${profilesUpdated} profiles")
})

