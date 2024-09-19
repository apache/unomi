import org.apache.unomi.shell.migration.MigrationException
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
def mewIndicesAndAliases = ["systemitems", "session-000001", "event-000001", "session", "event"]

// Check env is ready for migration
context.performMigrationStep("2.2.0-check-env-ready-for-migrate-event-session", () -> {
    def currentIndex = ""
    if (mewIndicesAndAliases.any{index -> {
        currentIndex = index
        return MigrationUtils.indexExists(context.getHttpClient(), esAddress, "${indexPrefix}-${currentIndex}")
    }}) {
        throw new MigrationException("Migration creates new index/aliases. Index or alias ${indexPrefix}-${currentIndex}  already exists, please remove it then restart migration")
    }
})


