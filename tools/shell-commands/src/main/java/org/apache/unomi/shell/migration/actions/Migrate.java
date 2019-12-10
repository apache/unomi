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
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.Migration;
import org.apache.unomi.shell.migration.utils.ConsoleUtils;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

import java.util.*;

@Command(scope = "unomi", name = "migrate", description = "This will Migrate your date in ES to be compliant with current version")
@Service
public class Migrate implements Action {

    @Reference
    Session session;

    @Reference
    BundleContext bundleContext;

    @Argument(name = "fromVersionWithoutSuffix", description = "Origin version without suffix/qualifier (e.g: 1.2.0)", multiValued = false, valueToShowInHelp = "1.2.0")
    private String fromVersionWithoutSuffix;

    public Object execute() throws Exception {
        if (fromVersionWithoutSuffix == null) {
            listMigrations();
            return null;
        }

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

        CloseableHttpClient httpClient = null;
        try {
            httpClient = HttpUtils.initHttpClient(session);

            String esAddress = ConsoleUtils.askUserWithDefaultAnswer(session, "Enter ElasticSearch 7 TARGET address (default = http://localhost:9200): ", "http://localhost:9200");

            for (Migration migration : getMigrations()) {
                if (fromVersion.compareTo(migration.getToVersion()) < 0) {
                    String migrateConfirmation = ConsoleUtils.askUserWithAuthorizedAnswer(session,"Starting migration to version " + migration.getToVersion() + ", do you want to proceed? (yes/no): ", Arrays.asList("yes", "no"));
                    if (migrateConfirmation.equalsIgnoreCase("no")) {
                        System.out.println("Migration process aborted");
                        break;
                    }
                    migration.execute(session, httpClient, esAddress, bundleContext);
                    System.out.println("Migration to version " + migration.getToVersion() + " done successfully");
                }
            }
        } finally {
            if (httpClient != null) {
                httpClient.close();
            }
        }

        return null;
    }

    private Version getCurrentVersionWithoutQualifier() {
        Version currentVersion = bundleContext.getBundle().getVersion();
        return new Version(currentVersion.getMajor() + "." + currentVersion.getMinor() + "." + currentVersion.getMicro());
    }

    private void listMigrations() {
        Version previousVersion = new Version("0.0.0");
        for (Migration migration : getMigrations()) {
            if (migration.getToVersion().getMajor() > previousVersion.getMajor() || migration.getToVersion().getMinor() > previousVersion.getMinor()) {
                System.out.println("From " + migration.getToVersion().getMajor() + "." + migration.getToVersion().getMinor() + ".0:");
            }
            System.out.println("- " + migration.getToVersion() + " " + migration.getDescription());
            previousVersion = migration.getToVersion();
        }
        System.out.println("Select your migration starting point by specifying the current version (e.g. 1.2.0) or the last script that was already run (e.g. 1.2.1)");

    }

    private List<Migration> getMigrations() {
        Collection<ServiceReference<Migration>> migrationServiceReferences = null;
        try {
            migrationServiceReferences = bundleContext.getServiceReferences(Migration.class, null);
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
        SortedSet<Migration> migrations = new TreeSet<>(new Comparator<Migration>() {
            @Override
            public int compare(Migration o1, Migration o2) {
                return o1.getToVersion().compareTo(o2.getToVersion());
            }
        });
        for (ServiceReference<Migration> migrationServiceReference : migrationServiceReferences) {
            Migration migration = bundleContext.getService(migrationServiceReference);
            migrations.add(migration);
        }
        return new ArrayList<>(migrations);
    }

}
