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
package org.apache.unomi.shell.migration.actions;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.MigrationService;

@Command(scope = "unomi", name = "migrate", description = "This will Migrate your data in ES to be compliant with current version. " +
        "It's possible to configure the migration using OSGI configuration file: org.apache.unomi.migration.cfg, " +
        "if no configuration is provided then questions will be prompted during the migration process.")
@Service
public class Migrate implements Action {

    @Reference
    Session session;

    @Reference
    MigrationService migrationService;

    @Argument(name = "originVersion", description = "Origin version without suffix/qualifier (e.g: 1.2.0)", valueToShowInHelp = "1.2.0")
    private String originVersion;

    @Argument(index = 1, name = "skipConfirmation", description = "Should the confirmation before starting the migration process be skipped ?", valueToShowInHelp = "false")
    private boolean skipConfirmation = false;

    public Object execute() throws Exception {
        migrationService.migrateUnomi(originVersion, skipConfirmation, session);
        return null;
    }
}
