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

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.util.GroovyScriptEngine;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.MigrateScript;
import org.apache.unomi.shell.migration.utils.ConsoleUtils;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.osgi.framework.*;
import org.osgi.framework.wiring.BundleWiring;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(scope = "unomi", name = "migrate", description = "This will Migrate your data in ES to be compliant with current version")
@Service
public class Migrate implements Action {
    public static final String CONFIG_ES_ADDRESS = "esAddress";
    public static final String CONFIG_TRUST_ALL_CERTIFICATES = "httpClient.trustAllCertificates";

    @Reference
    Session session;

    @Reference
    BundleContext bundleContext;

    @Argument(index = 0, name = "originVersion", description = "Origin version without suffix/qualifier (e.g: 1.2.0)", valueToShowInHelp = "1.2.0")
    private String originVersion;

    public Object execute() throws Exception {
        // Load migration scrips
        Set<MigrateScript> scripts = loadOSGIScripts();
        scripts.addAll(loadFileSystemScripts());

        if (originVersion == null) {
            displayMigrations(scripts);
            ConsoleUtils.printMessage(session, "Select your migration starting point by specifying the current version (e.g. 1.2.0) or the last script that was already run (e.g. 1.2.1)");
            return null;
        }

        // Check that there is some migration scripts available from given version
        Version fromVersion = new Version(originVersion);
        scripts = filterScriptsFromVersion(scripts, fromVersion);
        if (scripts.size() == 0) {
            ConsoleUtils.printMessage(session, "No migration scripts available found starting from version: " + originVersion);
            return null;
        } else {
            ConsoleUtils.printMessage(session, "The following migration scripts starting from version: " + originVersion + " will be executed.");
            displayMigrations(scripts);
        }

        // Check for user approval before migrate
        if (ConsoleUtils.askUserWithAuthorizedAnswer(session,
                "[WARNING] You are about to execute a migration, this a very sensitive operation, are you sure? (yes/no): ",
                Arrays.asList("yes", "no")).equalsIgnoreCase("no")) {
            ConsoleUtils.printMessage(session, "Migration process aborted");
            return null;
        }

        // Build conf
        Map<String, Object> migrationConfig = new HashMap<>();
        migrationConfig.put(CONFIG_ES_ADDRESS, ConsoleUtils.askUserWithDefaultAnswer(session, "Enter ElasticSearch 7 TARGET address (default = http://localhost:9200): ", "http://localhost:9200"));
        migrationConfig.put(CONFIG_TRUST_ALL_CERTIFICATES, ConsoleUtils.askUserWithAuthorizedAnswer(session,"We need to initialize a HttpClient, do we need to trust all certificates? (yes/no): ", Arrays.asList("yes", "no")).equalsIgnoreCase("yes"));

        try (CloseableHttpClient httpClient = HttpUtils.initHttpClient((Boolean) migrationConfig.get(CONFIG_TRUST_ALL_CERTIFICATES))) {

            // Compile scripts
            scripts = parseScripts(scripts, session, httpClient, migrationConfig);

            // Start migration
            ConsoleUtils.printMessage(session, "Starting migration process from version: " + originVersion);
            for (MigrateScript migrateScript : scripts) {
                ConsoleUtils.printMessage(session, "Starting execution of: " + migrateScript);
                try {
                    migrateScript.getCompiledScript().run();
                } catch (Exception e) {
                    ConsoleUtils.printException(session, "Error executing: " + migrateScript, e);
                    return null;
                }

                ConsoleUtils.printMessage(session, "Finnish execution of: " + migrateScript);
            }
        }

        return null;
    }

    private void displayMigrations(Set<MigrateScript> scripts) {
        Version previousVersion = new Version("0.0.0");
        for (MigrateScript migration : scripts) {
            if (migration.getVersion().getMajor() > previousVersion.getMajor() || migration.getVersion().getMinor() > previousVersion.getMinor()) {
                ConsoleUtils.printMessage(session, "From " + migration.getVersion().getMajor() + "." + migration.getVersion().getMinor() + ".0:");
            }
            ConsoleUtils.printMessage(session, "- " + migration);
            previousVersion = migration.getVersion();
        }
    }

    private Set<MigrateScript> filterScriptsFromVersion(Set<MigrateScript> scripts, Version fromVersion) {
        return scripts.stream()
                .filter(migrateScript -> fromVersion.compareTo(migrateScript.getVersion()) < 0)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<MigrateScript> parseScripts(Set<MigrateScript> scripts, Session session, CloseableHttpClient httpClient, Map<String, Object> migrationConfig) {
        Map<String, GroovyShell> shellsPerBundle = new HashMap<>();

        return scripts.stream()
                .peek(migrateScript -> {
                    // fallback on current bundle if the scripts is not provided by OSGI
                    Bundle scriptBundle = migrateScript.getBundle() != null ? migrateScript.getBundle() : bundleContext.getBundle();
                    if (!shellsPerBundle.containsKey(scriptBundle.getSymbolicName())) {
                        shellsPerBundle.put(scriptBundle.getSymbolicName(), buildShellForBundle(scriptBundle, session, httpClient, migrationConfig));
                    }
                    migrateScript.setCompiledScript(shellsPerBundle.get(scriptBundle.getSymbolicName()).parse(migrateScript.getScript()));
                })
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<MigrateScript> loadOSGIScripts() throws IOException {
        SortedSet<MigrateScript> migrationScripts = new TreeSet<>();
        for (Bundle bundle : bundleContext.getBundles()) {
            Enumeration<URL> scripts = bundle.findEntries("META-INF/cxs/migration", "*.groovy", true);
            if (scripts != null) {
                // check for shell

                while (scripts.hasMoreElements()) {
                    URL scriptURL = scripts.nextElement();
                    migrationScripts.add(new MigrateScript(scriptURL, bundle));
                }
            }
        }

        return migrationScripts;
    }

    private Set<MigrateScript> loadFileSystemScripts() throws IOException {
        // check migration folder exists
        Path migrationFolder = Paths.get(System.getProperty( "karaf.data" ), "migration", "scripts");
        if (!Files.isDirectory(migrationFolder)) {
            return Collections.emptySet();
        }

        List<Path> paths;
        try (Stream<Path> walk = Files.walk(migrationFolder)) {
            paths = walk
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> path.toString().toLowerCase().endsWith("groovy"))
                    .collect(Collectors.toList());
        }

        SortedSet<MigrateScript> migrationScripts = new TreeSet<>();
        for (Path path : paths) {
            migrationScripts.add(new MigrateScript(path.toUri().toURL(), null));
        }
        return migrationScripts;
    }

    private GroovyShell buildShellForBundle(Bundle bundle, Session session, CloseableHttpClient httpClient, Map<String, Object> migrationConfig) {
        GroovyClassLoader groovyLoader = new GroovyClassLoader(bundle.adapt(BundleWiring.class).getClassLoader());
        GroovyScriptEngine groovyScriptEngine = new GroovyScriptEngine((URL[]) null, groovyLoader);
        GroovyShell groovyShell = new GroovyShell(groovyScriptEngine.getGroovyClassLoader());
        groovyShell.setVariable("session", session);
        groovyShell.setVariable("httpClient", httpClient);
        groovyShell.setVariable("migrationConfig", migrationConfig);
        groovyShell.setVariable("bundleContext", bundle.getBundleContext());
        return groovyShell;
    }
}
