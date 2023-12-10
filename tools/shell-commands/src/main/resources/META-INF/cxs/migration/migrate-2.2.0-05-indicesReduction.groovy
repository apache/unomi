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
def indicesToReduce = [
        actiontype: [reduceTo: "systemitems", renameId: true],
        campaign: [reduceTo: "systemitems", renameId: true],
        campaignevent: [reduceTo: "systemitems", renameId: true],
        goal: [reduceTo: "systemitems", renameId: true],
        userlist: [reduceTo: "systemitems", renameId: true],
        propertytype: [reduceTo: "systemitems", renameId: true],
        scope: [reduceTo: "systemitems", renameId: true],
        conditiontype: [reduceTo: "systemitems", renameId: true],
        rule: [reduceTo: "systemitems", renameId: true],
        scoring: [reduceTo: "systemitems", renameId: true],
        segment: [reduceTo: "systemitems", renameId: true],
        topic: [reduceTo: "systemitems", renameId: true],
        patch: [reduceTo: "systemitems", renameId: true],
        jsonschema: [reduceTo: "systemitems", renameId: true],
        importconfig: [reduceTo: "systemitems", renameId: true],
        exportconfig: [reduceTo: "systemitems", renameId: true],
        rulestats: [reduceTo: "systemitems", renameId: true],
        groovyaction: [reduceTo: "systemitems", renameId: true],
        persona: [reduceTo: "profile", renameId: false]
]

context.performMigrationStep("2.2.0-create-systemItems-index", () -> {
    if (!MigrationUtils.indexExists(context.getHttpClient(), esAddress, "${indexPrefix}-systemitems")) {
        String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, "systemItems.json")
        String newIndexSettings = MigrationUtils.buildIndexCreationRequest(baseSettings, mapping, context, false)
        HttpUtils.executePutRequest(context.getHttpClient(), esAddress + "/${indexPrefix}-systemitems", newIndexSettings, null)
    }
})

indicesToReduce.each { indexToReduce ->
    context.performMigrationStep("2.2.0-reduce-${indexToReduce.key}", () -> {
        if (MigrationUtils.indexExists(context.getHttpClient(), esAddress, "${indexPrefix}-${indexToReduce.key}")) {
            def painless = null
            System.out.println("start reduce ${indexToReduce.key}")
            // check if we need to update the ids of those items first
            if (indexToReduce.value.renameId) {
                System.out.println("rename Id to  ${indexToReduce.value.renameId}")
                painless = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/2.2.0/suffix_ids.painless").replace("#ID_SUFFIX", "_${indexToReduce.key}")
            }
            // move items
            def reduceToIndex = "${indexPrefix}-${indexToReduce.value.reduceTo}"
            if (indexToReduce.key.equals("scope")) {
                def resp = HttpUtils.executePostRequest(context.getHttpClient(), esAddress + "/${indexPrefix}-${indexToReduce.key}/_search", "{\n" +
                        "    \"query\": {\n" +
                        "        \"match_all\": {}\n" +
                        "    }\n" +
                        "}", null)
                System.out.println("Current Scope index : " + resp)
            }
            MigrationUtils.moveToIndex(context.getHttpClient(), bundleContext, esAddress, "${indexPrefix}-${indexToReduce.key}", reduceToIndex, painless)
            System.out.println("Move performed with data  ${painless}")
            MigrationUtils.deleteIndex(context.getHttpClient(), esAddress, "${indexPrefix}-${indexToReduce.key}")

            HttpUtils.executePostRequest(context.getHttpClient(), esAddress + "/${reduceToIndex}/_refresh", null, null);
            String searchScopesRequest = MigrationUtils.resourceAsString(bundleContext,"requestBody/2.2.0/scope_search.json")
            MigrationUtils.waitForYellowStatus(context.getHttpClient(), esAddress, context);
        }
    })
}


