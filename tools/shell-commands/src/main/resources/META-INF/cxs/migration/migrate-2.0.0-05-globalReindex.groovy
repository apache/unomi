import org.apache.unomi.shell.migration.actions.MigrationHistory
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

MigrationHistory history = migrationHistory
String esAddress = migrationConfig.getString("esAddress", session)
String indexPrefix = migrationConfig.getString("indexPrefix", session)

String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/base_index_mapping.json")
String[] indicesToReindex = ["segment", "scoring", "campaign", "conditionType", "goal", "patch", "rule"];
indicesToReindex.each { indexToReindex ->
    String mappingFileName = "${indexToReindex}.json"
    String realIndexName = "${indexPrefix}-${indexToReindex.toLowerCase()}"
    String mapping = MigrationUtils.extractMappingFromBundles(bundleContext, mappingFileName)
    String newIndexSettings = MigrationUtils.buildIndexCreationRequest(httpClient, esAddress, baseSettings, realIndexName, mapping)
    MigrationUtils.reIndex(httpClient, bundleContext, esAddress, realIndexName, newIndexSettings, null, history)
}
