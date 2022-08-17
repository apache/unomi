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
package org.apache.unomi.shell.migration;

import org.apache.karaf.shell.api.console.Session;

/**
 * Simple OSGI service used for migrating Unomi data
 */
public interface MigrationService {

    /**
     * This will Migrate your data in ES to be compliant with current Unomi version.
     * It's possible to configure the migration using OSGI configuration file: org.apache.unomi.migration.cfg,
     *  if no configuration is provided then questions will be prompted during the migration process.
     *  (only in case you are in karaf shell context, if not, a missing configuration will fail the migration process)
     *
     * @param originVersion Origin version without suffix/qualifier (e.g: 1.2.0)
     * @param skipConfirmation Should the confirmation before starting the migration process be skipped ? (only supported in karaf shell context)
     * @param session Karaf shell session, for execution in Karaf shell context, null otherwise
     * @throws Exception
     */
    void migrateUnomi(String originVersion, boolean skipConfirmation, Session session) throws Exception;
}
