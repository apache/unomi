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
package org.apache.unomi.shell.migration.service;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.util.GroovyScriptEngine;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.MigrationService;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.unomi.shell.migration.service.MigrationConfig.*;

@Component(service = MigrationService.class, immediate = true)
public class MigrationServiceImpl implements MigrationService {

    public static final String MIGRATION_FS_ROOT_FOLDER = "migration";
    public static final Path MIGRATION_FS_SCRIPTS_FOLDER = Paths.get(System.getProperty( "karaf.data" ), MIGRATION_FS_ROOT_FOLDER, "scripts");

    private BundleContext bundleContext;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MigrationConfig migrationConfig;

    @Activate
    public void activate(ComponentContext componentContext) {
        this.bundleContext = componentContext.getBundleContext();
    }

    public void migrateUnomi(String originVersion, boolean skipConfirmation, Session session) throws Exception {
        System.out.println("Migrating Unomi...");
        // Wait for config to be loaded by file install, in case of unomi.autoMigrate the OSGI conf may take a few seconds to be loaded correctly.
        waitForMigrationConfigLoad(60, 1);

        // Load migration scrips
        Set<MigrationScript> scripts = loadOSGIScripts();
        scripts.addAll(loadFileSystemScripts());

        // Create migration context
        Files.createDirectories(MIGRATION_FS_SCRIPTS_FOLDER);
        MigrationContext context = new MigrationContext(session, migrationConfig);
        context.tryRecoverFromHistory();

        // no origin version, just print available scripts
        if (originVersion == null) {
            displayMigrations(scripts, context);
            context.printMessage("Select your migration starting point by specifying the current version (e.g. 1.2.0) or the last script that was already run (e.g. 1.2.1)");
            return;
        }

        // Check that there is some migration scripts available from given version
        Version fromVersion = new Version(originVersion);
        scripts = filterScriptsFromVersion(scripts, fromVersion);
        if (scripts.size() == 0) {
            context.printMessage("No migration scripts available found starting from version: " + originVersion);
            return;
        } else {
            context.printMessage("The following migration scripts starting from version: " + originVersion + " will be executed.");
            displayMigrations(scripts, context);
        }

        // Check for user approval before migrate
        if (!skipConfirmation && context.askUserWithAuthorizedAnswer(
                "[WARNING] You are about to execute a migration, this a very sensitive operation, are you sure? (yes/no): ",
                Arrays.asList("yes", "no")).equalsIgnoreCase("no")) {
            context.printMessage("Migration process aborted");
            return;
        }

        // Handle credentials
        CredentialsProvider credentialsProvider = null;
        String login = context.getConfigString(CONFIG_ES_LOGIN);
        if (StringUtils.isNotEmpty(login)) {
            credentialsProvider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials
                    = new UsernamePasswordCredentials(login, context.getConfigString(CONFIG_ES_PASSWORD));
            credentialsProvider.setCredentials(AuthScope.ANY, credentials);
        }

        try (CloseableHttpClient httpClient = HttpUtils.initHttpClient(context.getConfigBoolean(CONFIG_TRUST_ALL_CERTIFICATES), credentialsProvider)) {

            // Compile scripts
            context.setHttpClient(httpClient);
            scripts = parseScripts(scripts, context);

            // Start migration
            context.printMessage("Starting migration process from version: " + originVersion);
            for (MigrationScript migrateScript : scripts) {
                context.printMessage("Starting execution of: " + migrateScript);
                try {
                    migrateScript.getCompiledScript().run();
                } catch (Exception e) {
                    context.printException("Error executing: " + migrateScript, e);
                    throw e;
                }

                context.printMessage("Finish execution of: " + migrateScript);
            }

            // We clean history, migration is successful
            context.cleanHistory();
        }
    }

    private void waitForMigrationConfigLoad(int maxTry, int secondsToSleep) throws InterruptedException {
        while (!migrationConfig.getConfig().containsKey("felix.fileinstall.filename")) {
            maxTry -= 1;
            if (maxTry == 0) {
                throw new IllegalStateException("Waited too long for migration config to be available");
            } else {
                TimeUnit.SECONDS.sleep(secondsToSleep);
            }
        }
    }

    private void displayMigrations(Set<MigrationScript> scripts, MigrationContext context) {
        Version previousVersion = new Version("0.0.0");
        for (MigrationScript migration : scripts) {
            if (migration.getVersion().getMajor() > previousVersion.getMajor() || migration.getVersion().getMinor() > previousVersion.getMinor()) {
                context.printMessage("From " + migration.getVersion().getMajor() + "." + migration.getVersion().getMinor() + ".0:");
            }
            context.printMessage("- " + migration);
            previousVersion = migration.getVersion();
        }
    }

    private Set<MigrationScript> filterScriptsFromVersion(Set<MigrationScript> scripts, Version fromVersion) {
        return scripts.stream()
                .filter(migrateScript -> fromVersion.compareTo(migrateScript.getVersion()) < 0)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<MigrationScript> parseScripts(Set<MigrationScript> scripts, MigrationContext context) {
        Map<String, GroovyShell> shellsPerBundle = new HashMap<>();

        return scripts.stream()
                .peek(migrateScript -> {
                    // fallback on current bundle if the scripts is not provided by OSGI
                    Bundle scriptBundle = migrateScript.getBundle() != null ? migrateScript.getBundle() : bundleContext.getBundle();
                    if (!shellsPerBundle.containsKey(scriptBundle.getSymbolicName())) {
                        shellsPerBundle.put(scriptBundle.getSymbolicName(), buildShellForBundle(scriptBundle, context));
                    }
                    migrateScript.setCompiledScript(shellsPerBundle.get(scriptBundle.getSymbolicName()).parse(migrateScript.getScript()));
                })
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<MigrationScript> loadOSGIScripts() throws IOException {
        SortedSet<MigrationScript> migrationScripts = new TreeSet<>();
        for (Bundle bundle : bundleContext.getBundles()) {
            Enumeration<URL> scripts = bundle.findEntries("META-INF/cxs/migration", "*.groovy", true);
            if (scripts != null) {
                // check for shell

                while (scripts.hasMoreElements()) {
                    URL scriptURL = scripts.nextElement();
                    migrationScripts.add(new MigrationScript(scriptURL, bundle));
                }
            }
        }

        return migrationScripts;
    }

    private Set<MigrationScript> loadFileSystemScripts() throws IOException {
        // check migration folder exists
        if (!Files.isDirectory(MIGRATION_FS_SCRIPTS_FOLDER)) {
            return Collections.emptySet();
        }

        List<Path> paths;
        try (Stream<Path> walk = Files.walk(MIGRATION_FS_SCRIPTS_FOLDER)) {
            paths = walk
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> path.toString().toLowerCase().endsWith("groovy"))
                    .collect(Collectors.toList());
        }

        SortedSet<MigrationScript> migrationScripts = new TreeSet<>();
        for (Path path : paths) {
            migrationScripts.add(new MigrationScript(path.toUri().toURL(), null));
        }
        return migrationScripts;
    }

    private GroovyShell buildShellForBundle(Bundle bundle, MigrationContext context) {
        GroovyClassLoader groovyLoader = new GroovyClassLoader(bundle.adapt(BundleWiring.class).getClassLoader());
        GroovyScriptEngine groovyScriptEngine = new GroovyScriptEngine((URL[]) null, groovyLoader);
        GroovyShell groovyShell = new GroovyShell(groovyScriptEngine.getGroovyClassLoader());
        groovyShell.setVariable("migrationContext", context);
        groovyShell.setVariable("bundleContext", bundle.getBundleContext());
        return groovyShell;
    }

    public void setMigrationConfig(MigrationConfig migrationConfig) {
        this.migrationConfig = migrationConfig;
    }
}
