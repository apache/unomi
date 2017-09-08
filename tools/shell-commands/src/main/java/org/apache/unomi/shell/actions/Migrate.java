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
package org.apache.unomi.shell.actions;

import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Argument;
import org.apache.unomi.shell.migrations.MigrationTo200;
import org.apache.unomi.shell.utils.ConsoleUtils;
import org.osgi.framework.Version;

import java.util.*;

@Command(scope = "unomi", name = "migrate", description = "This will Migrate your date in ES to be compliant with current version")
public class Migrate extends OsgiCommandSupport {

    @Argument(name = "fromVersionWithoutSuffix", description = "Origin version without suffix/qualifier (e.g: 1.2.0)", required = true, multiValued = false, valueToShowInHelp = "1.2.0")
    private String fromVersionWithoutSuffix;

    protected Object doExecute() throws Exception {
        String confirmation = ConsoleUtils.askUserWithAuthorizedAnswer(session,"[WARNING] You are about to execute a migration, this a very sensitive operation, are you sure? (yes/no): ", Arrays.asList("yes", "no"));
        if (confirmation.equalsIgnoreCase("no")) {
            System.out.println("Migration process aborted");
            return null;
        }

        System.out.println("Starting migration process from version: " + fromVersionWithoutSuffix);

        Version fromVersion = new Version(fromVersionWithoutSuffix);
        Version currentVersion = getCurrentVersionWithoutQualifier();
        System.out.println("current version: " + currentVersion.toString());
        if (currentVersion.compareTo(fromVersion) <= 0) {
            System.out.println("From version is same or superior than current version, nothing to migrate.");
            return null;
        }

        if (fromVersion.compareTo(new Version("2.0.0")) < 0) {
            System.out.println("Starting migration to version 2.0.0");

            MigrationTo200 migrationTo200 = new MigrationTo200(session);
            migrationTo200.execute();

            System.out.println("Migration to version 2.0.0 done successfully");
        }

        return null;
    }

    private Version getCurrentVersionWithoutQualifier() {
        Version currentVersion = bundleContext.getBundle().getVersion();
        return new Version(currentVersion.getMajor() + "." + currentVersion.getMinor() + "." + currentVersion.getMicro());
    }
}
