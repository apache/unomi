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

context.performMigrationStep("2.4.0-migrate-view-event-page-path", () -> {
    String updatePathScript = MigrationUtils.getFileWithoutComments(bundleContext, "requestBody/2.4.0/view_event_page_path_migrate.painless")
    String baseSettings = MigrationUtils.resourceAsString(bundleContext, "requestBody/2.4.0/base_update_by_query_request.json")
    HttpUtils.executePostRequest(context.getHttpClient(), "${esAddress}/${indexPrefix}-event/_update_by_query", baseSettings.replace('#painless', updatePathScript), null)
})
