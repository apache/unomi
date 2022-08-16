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

String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")
String[] indicesToReindex = ["segment", "scoring", "campaign", "conditionType", "goal", "patch", "rule"];
indicesToReindex.each { indexToReindex ->
    String mappingFileName = "${indexToReindex}.json"
    String realIndexName = "${indexPrefix}-${indexToReindex.toLowerCase()}"
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, mappingFileName)
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(context.getHttpClient(), esAddress, baseSettings, realIndexName, mapping)
    MigrationUtils.reIndex(context.getHttpClient(), bundleContext, esAddress, realIndexName, newIndexSettings, null, context)
}
