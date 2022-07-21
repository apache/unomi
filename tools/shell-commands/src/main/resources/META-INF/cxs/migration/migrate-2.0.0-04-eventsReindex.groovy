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

String esAddress = migrationConfig.get("esAddress")
String indexPrefix = migrationConfig.get("indexPrefix")

// Remove all internal events that are no more persisted
String removeInternalEventsRequest = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/event_delete_by_query.json")
HttpUtils.executePostRequest(httpClient, "${esAddress}/${indexPrefix}-event-*/_delete_by_query", removeInternalEventsRequest, null)

// Reindex the rest of the events
String newIndexSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.0.0/event_index.json");
String reIndexScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/2.0.0/event_migrate.painless");
Set<String> eventIndices = MigrationUtils.getIndexesPrefixedBy(httpClient, esAddress, "${indexPrefix}-event-")
eventIndices.each { eventIndex ->
    MigrationUtils.reIndex(httpClient, bundleContext, esAddress, eventIndex, newIndexSettings, reIndexScript)
}