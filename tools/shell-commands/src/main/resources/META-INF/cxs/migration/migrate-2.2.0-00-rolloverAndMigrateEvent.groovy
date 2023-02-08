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
String newEventIndex = indexPrefix + "-event-000001"
String rolloverPolicyName = indexPrefix + "-unomi-rollover-policy"
String rolloverEventAlias = indexPrefix + "-event"


context.performMigrationStep("2.2.0-00-update-lifecyle-poll-interval", () -> {
    String updatePollIntervalBody = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.2.0/update_settings_poll_interval.json")
            .replace("#pollIntervalValue", "\"2s\"")
    HttpUtils.executePutRequest(context.getHttpClient(), esAddress + "/_cluster/settings", updatePollIntervalBody, null)
})

context.performMigrationStep("2.2.0-00-create-rollover-policy", () -> {
    String createRolloverPolicyQuery = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.2.0/create_rollover_policy_query.json")
    String rolloverQueryBody = MigrationUtils.buildRolloverPolicyCreationRequest(createRolloverPolicyQuery, context)

    HttpUtils.executePutRequest(context.getHttpClient(), esAddress + "/_ilm/policy/" + rolloverPolicyName, rolloverQueryBody, null)
})

context.performMigrationStep("2.2.0-00-create-event-index", () -> {
    if (!MigrationUtils.indexExists(context.getHttpClient(), esAddress, newEventIndex)) {
        String baseRequest = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.2.0/base_index_mapping.json")
        String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "event.json")

        String newIndexSettings = MigrationUtils.buildIndexCreationRequestWithRollover(baseRequest, mapping, context, rolloverPolicyName, rolloverEventAlias)
        HttpUtils.executePutRequest(context.getHttpClient(), esAddress + "/" + newEventIndex, newIndexSettings, null)
    }
})

Set<String> eventIndices = MigrationUtils.getIndexesPrefixedBy(context.getHttpClient(), esAddress, indexPrefix + "-event-date-")
List<String> sortedIndices = new ArrayList<>(eventIndices)
Collections.sort(sortedIndices)

context.performMigrationStep("2.2.0-00-migrate-existing-events", () -> {
    sortedIndices.each { eventIndex ->
        MigrationUtils.moveToIndex(context.getHttpClient(), bundleContext, esAddress, eventIndex, indexPrefix + "-event")
        sleep(3000)
    }
})

context.performMigrationStep("2.2.0-00-remove-old-events-indices", () -> {
    sortedIndices.each { eventIndex ->
        MigrationUtils.deleteIndex(context.getHttpClient(), esAddress, eventIndex)
    }
})

context.performMigrationStep("2.2.0-00-reset-poll-interval", () -> {
    String updatePollIntervalBody = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.2.0/update_settings_poll_interval.json")
            .replace("#pollIntervalValue", "null")
    HttpUtils.executePutRequest(context.getHttpClient(), esAddress + "/_cluster/settings", updatePollIntervalBody, null)
})
