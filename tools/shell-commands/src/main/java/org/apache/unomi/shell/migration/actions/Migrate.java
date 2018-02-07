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

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Argument;
import org.apache.unomi.shell.migration.Migration;
import org.apache.unomi.shell.migration.utils.ConsoleUtils;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.osgi.framework.Version;

import java.util.*;

@Command(scope = "unomi", name = "migrate", description = "This will Migrate your date in ES to be compliant with current version")
public class Migrate extends OsgiCommandSupport {

    private List<Migration> migrations;

    @Argument(name = "fromVersionWithoutSuffix", description = "Origin version without suffix/qualifier (e.g: 1.2.0)", required = true, multiValued = false, valueToShowInHelp = "1.2.0")
    private String fromVersionWithoutSuffix;

    protected Object doExecute() throws Exception {
        String confirmation = ConsoleUtils.askUserWithAuthorizedAnswer(session,"[WARNING] You are about to execute a migration, this a very sensitive operation, are you sure? (yes/no): ", Arrays.asList("yes", "no"));
        if (confirmation.equalsIgnoreCase("no")) {
            System.out.println("Migration process aborted");
            return null;
        }

        System.out.println("Starting migration process from version: " + fromVersionWithoutSuffix);

        String esAddress = ConsoleUtils.askUserWithDefaultAnswer(session, "Elasticsearch address (default = http://localhost:9200): ", "http://localhost:9200");

        Version fromVersion = new Version(fromVersionWithoutSuffix);
        Version currentVersion = getCurrentVersionWithoutQualifier();
        System.out.println("current version: " + currentVersion.toString());
        if (currentVersion.compareTo(fromVersion) <= 0) {
            System.out.println("From version is same or superior than current version, nothing to migrate.");
            return null;
        }

        CloseableHttpClient httpClient = HttpUtils.initHttpClient(session);

        for (Migration migration : migrations) {
            if (fromVersion.compareTo(migration.getToVersion()) < 0) {
                System.out.println("Starting migration to version " + migration.getToVersion());
                migration.execute(session, httpClient, esAddress);
                System.out.println("Migration to version " + migration.getToVersion() + " done successfully");
            }
        }

        if (httpClient != null) {
            httpClient.close();
        }

        return null;
    }

    private Version getCurrentVersionWithoutQualifier() {
        Version currentVersion = bundleContext.getBundle().getVersion();
        return new Version(currentVersion.getMajor() + "." + currentVersion.getMinor() + "." + currentVersion.getMicro());
    }

    public void setMigrations(List<Migration> migrations) {
        this.migrations = migrations;
    }
}
