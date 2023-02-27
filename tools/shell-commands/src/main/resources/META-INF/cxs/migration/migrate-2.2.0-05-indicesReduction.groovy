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
String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")

context.performMigrationStep("2.2.0-create-systemItems-index", () -> {
    if (!MigrationUtils.indexExists(context.getHttpClient(), esAddress, "${indexPrefix}-systemitems")) {
        String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "systemItems.json")
        String newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)
        HttpUtils.executePutRequest(context.getHttpClient(), esAddress + "/${indexPrefix}-systemitems", newIndexSettings, null)
    }
})

def indicesToReduce = [
        actiontype: "systemitems",
        campaign: "systemitems",
        campaignevent: "systemitems",
        goal: "systemitems",
        userlist: "systemitems",
        propertytype: "systemitems",
        scope: "systemitems",
        conditiontype: "systemitems",
        rule: "systemitems",
        scoring: "systemitems",
        segment: "systemitems",
        topic: "systemitems",
        patch: "systemitems",
        jsonschema: "systemitems",
        importconfig: "systemitems",
        exportconfig: "systemitems",
        rulestats: "systemitems",
        groovyaction: "systemitems",
        persona: "profile",
        personasession: "session"
]
def indicesToSuffixIds = [
        rulestats: "-stat",
        groovyaction: "-groovySourceCode"
]
indicesToReduce.each { indexToReduce ->
    context.performMigrationStep("2.2.0-reduce-${indexToReduce.key}", () -> {
        if (MigrationUtils.indexExists(context.getHttpClient(), esAddress, "${indexPrefix}-${indexToReduce.key}")) {
            def painless = null
            // check if we need to update the ids of those items first
            if (indicesToSuffixIds.containsKey(indexToReduce.key)) {
                painless = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/2.2.0/suffix_ids.painless").replace("#ID_SUFFIX", indicesToSuffixIds.get(indexToReduce.key))
            }
            // move items
            MigrationUtils.moveToIndex(context.getHttpClient(), bundleContext, esAddress, "${indexPrefix}-${indexToReduce.key}", "${indexPrefix}-${indexToReduce.value}", painless)
            MigrationUtils.deleteIndex(context.getHttpClient(), esAddress, "${indexPrefix}-${indexToReduce.key}")
            HttpUtils.executePostRequest(context.getHttpClient(), esAddress + "/${indexPrefix}-${indexToReduce.value}/_refresh", null, null);
            MigrationUtils.waitForYellowStatus(context.getHttpClient(), esAddress, context);
        }
    })
}


