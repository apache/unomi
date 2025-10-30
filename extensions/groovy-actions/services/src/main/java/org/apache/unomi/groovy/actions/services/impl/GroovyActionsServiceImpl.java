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
package org.apache.unomi.groovy.actions.services.impl;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.util.GroovyScriptEngine;
import org.apache.commons.io.IOUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.groovy.actions.GroovyAction;
import org.apache.unomi.groovy.actions.GroovyBundleResourceConnector;
import org.apache.unomi.groovy.actions.ScriptMetadata;
import org.apache.unomi.groovy.actions.annotations.Action;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 * High-performance GroovyActionsService implementation with pre-compilation,
 * hash-based change detection, and thread-safe execution.
 */
@Component(service = GroovyActionsService.class, configurationPid = "org.apache.unomi.groovy.actions")
@Designate(ocd = GroovyActionsServiceImpl.GroovyActionsServiceConfig.class)
public class GroovyActionsServiceImpl implements GroovyActionsService {

    @ObjectClassDefinition(name = "Groovy actions service config", description = "The configuration for the Groovy actions service")
    public @interface GroovyActionsServiceConfig {
        int services_groovy_actions_refresh_interval() default 1000;
    }

    private BundleContext bundleContext;
    private GroovyScriptEngine groovyScriptEngine;
    private CompilerConfiguration compilerConfiguration;

    private final Object compilationLock = new Object();
    private GroovyShell compilationShell;
    private volatile Map<String, ScriptMetadata> scriptMetadataCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> loggedRefreshErrors = new ConcurrentHashMap<>();
    private static final int MAX_LOGGED_ERRORS = 100; // Prevent memory leak

    private static final Logger LOGGER = LoggerFactory.getLogger(GroovyActionsServiceImpl.class.getName());
    private static final String BASE_SCRIPT_NAME = "BaseScript";
    private static final String REFRESH_ACTIONS_TASK_TYPE = "refresh-groovy-actions";

    private DefinitionsService definitionsService;
    private PersistenceService persistenceService;
    private SchedulerService schedulerService;
    private String refreshGroovyActionsTaskId;
    private GroovyActionsServiceConfig config;

    @Reference
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @Reference
    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Reference
    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;

        if (schedulerService != null) {
            LOGGER.info("SchedulerService was set after GroovyActionsService initialization, initializing scheduled tasks now");
            initializeTimers();
        }
    }

    @Activate
    public void start(GroovyActionsServiceConfig config, BundleContext bundleContext) {
        LOGGER.debug("postConstruct {}", bundleContext.getBundle());

        this.config = config;
        this.bundleContext = bundleContext;

        GroovyBundleResourceConnector bundleResourceConnector = new GroovyBundleResourceConnector(bundleContext);
        GroovyClassLoader groovyLoader = new GroovyClassLoader(bundleContext.getBundle().adapt(BundleWiring.class).getClassLoader());
        this.groovyScriptEngine = new GroovyScriptEngine(bundleResourceConnector, groovyLoader);

        // Initialize Groovy compiler and compilation shell
        initializeGroovyCompiler();

        try {
            loadBaseScript();
        } catch (IOException e) {
            LOGGER.error("Failed to load base script", e);
        }

        // PRE-COMPILE ALL SCRIPTS AT STARTUP (no on-demand compilation)
        preloadAllScripts();

        if (schedulerService != null) {
            initializeTimers();
        } else {
            LOGGER.warn("SchedulerService not available during GroovyActionsService initialization. Scheduled tasks will not be registered. They will be registered when SchedulerService becomes available.");
        }
        LOGGER.info("Groovy action service initialized with {} scripts", scriptMetadataCache.size());
    }

    @Deactivate
    public void onDestroy() {
        LOGGER.debug("onDestroy Method called");
        if (schedulerService != null && refreshGroovyActionsTaskId != null) {
            schedulerService.cancelTask(refreshGroovyActionsTaskId);
        }
    }

    /**
     * Load the Base script.
     * It's a script which provides utility functions that we can use in other groovy script
     * The functions added by the base script could be called by the groovy actions executed in
     * {@link org.apache.unomi.groovy.actions.GroovyActionDispatcher#execute}
     * The base script would be added in the configuration of the {@link GroovyActionsServiceImpl#compilationShell GroovyShell} , so when a
     * script will be parsed with the GroovyShell (groovyShell.parse(...)), the action will extends the base script, so the functions
     * could be called
     *
     * @throws IOException
     */
    private void loadBaseScript() throws IOException {
        URL groovyBaseScriptURL = bundleContext.getBundle().getEntry("META-INF/base/BaseScript.groovy");
        if (groovyBaseScriptURL == null) {
            return;
        }
        LOGGER.debug("Found Groovy base script at {}, loading... ", groovyBaseScriptURL.getPath());
        GroovyCodeSource groovyCodeSource = new GroovyCodeSource(IOUtils.toString(groovyBaseScriptURL.openStream(), StandardCharsets.UTF_8), BASE_SCRIPT_NAME, "/groovy/script");
        groovyScriptEngine.getGroovyClassLoader().parseClass(groovyCodeSource, true);
    }

    /**
     * Initializes compiler configuration and shared compilation shell.
     */
    private void initializeGroovyCompiler() {
        // Configure the compiler with imports and base script
        compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.addCompilationCustomizers(createImportCustomizer());
        compilerConfiguration.setScriptBaseClass(BASE_SCRIPT_NAME);
        groovyScriptEngine.setConfig(compilerConfiguration);

        // Create single shared shell for compilation only
        this.compilationShell = new GroovyShell(groovyScriptEngine.getGroovyClassLoader(), compilerConfiguration);
    }

    /**
     * Pre-compiles all scripts at startup to eliminate runtime compilation overhead.
     */
    private void preloadAllScripts() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("Pre-compiling all Groovy scripts at startup...");

        int successCount = 0;
        int failureCount = 0;
        long totalCompilationTime = 0;

        for (GroovyAction groovyAction : persistenceService.getAllItems(GroovyAction.class)) {
            try {
                String actionName = groovyAction.getName();
                String scriptContent = groovyAction.getScript();

                long scriptStartTime = System.currentTimeMillis();
                ScriptMetadata metadata = compileAndCreateMetadata(actionName, scriptContent);
                long scriptCompilationTime = System.currentTimeMillis() - scriptStartTime;
                totalCompilationTime += scriptCompilationTime;

                scriptMetadataCache.put(actionName, metadata);

                successCount++;
                LOGGER.debug("Pre-compiled script: {} ({}ms)", actionName, scriptCompilationTime);

            } catch (Exception e) {
                failureCount++;
                LOGGER.error("Failed to pre-compile script: {}", groovyAction.getName(), e);
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        LOGGER.info("Pre-compilation completed: {} scripts successfully compiled, {} failures. Total time: {}ms",
                successCount, failureCount, totalTime);
        LOGGER.debug("Pre-compilation metrics: Average per script: {}ms, Compilation overhead: {}ms",
                successCount > 0 ? totalCompilationTime / successCount : 0,
                totalTime - totalCompilationTime);
    }

    /**
     * Thread-safe script compilation using synchronized shared shell.
     */
    private Class<? extends Script> compileScript(String actionName, String scriptContent) {
        GroovyCodeSource codeSource = buildClassScript(scriptContent, actionName);
        synchronized(compilationLock) {
            return compilationShell.parse(codeSource).getClass();
        }
    }

    /**
     * Creates import customizer with standard Unomi imports.
     */
    private ImportCustomizer createImportCustomizer() {
        ImportCustomizer importCustomizer = new ImportCustomizer();
        importCustomizer.addImports("org.apache.unomi.api.services.EventService", "org.apache.unomi.groovy.actions.annotations.Action",
                "org.apache.unomi.groovy.actions.annotations.Parameter");
        return importCustomizer;
    }

    /**
     * Validates that a string parameter is not null or empty.
     */
    private void validateNotEmpty(String value, String parameterName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(parameterName + " cannot be null or empty");
        }
    }

    /**
     * Compiles a script and creates metadata with timing information.
     */
    private ScriptMetadata compileAndCreateMetadata(String actionName, String scriptContent) {
        long compilationStartTime = System.currentTimeMillis();
        Class<? extends Script> scriptClass = compileScript(actionName, scriptContent);
        long compilationTime = System.currentTimeMillis() - compilationStartTime;

        LOGGER.debug("Script {} compiled in {}ms", actionName, compilationTime);
        return new ScriptMetadata(actionName, scriptContent, scriptClass);
    }

    /**
     * Extracts Action annotation from script class if present.
     */
    private Action getActionAnnotation(Class<? extends Script> scriptClass) {
        try {
            return scriptClass.getMethod("execute").getAnnotation(Action.class);
        } catch (Exception e) {
            LOGGER.error("Failed to extract action annotation", e);
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * Implementation performs hash-based change detection to skip unnecessary recompilation.
     */
    @Override
    public void save(String actionName, String groovyScript) {
        validateNotEmpty(actionName, "Action name");
        validateNotEmpty(groovyScript, "Groovy script");

        long startTime = System.currentTimeMillis();
        LOGGER.info("Saving script: {}", actionName);

        try {
            ScriptMetadata existingMetadata = scriptMetadataCache.get(actionName);
            if (existingMetadata != null && !existingMetadata.hasChanged(groovyScript)) {
                LOGGER.info("Script {} unchanged, skipping recompilation ({}ms)", actionName,
                    System.currentTimeMillis() - startTime);
                return;
            }

            long compilationStartTime = System.currentTimeMillis();
            ScriptMetadata metadata = compileAndCreateMetadata(actionName, groovyScript);
            long compilationTime = System.currentTimeMillis() - compilationStartTime;

            Action actionAnnotation = getActionAnnotation(metadata.getCompiledClass());
            if (actionAnnotation != null) {
                saveActionType(actionAnnotation);
            }

            saveScript(actionName, groovyScript);

            scriptMetadataCache.put(actionName, metadata);

            long totalTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Script {} saved and compiled successfully (total: {}ms, compilation: {}ms)",
                actionName, totalTime, compilationTime);

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            LOGGER.error("Failed to save script: {} ({}ms)", actionName, totalTime, e);
            throw new RuntimeException("Failed to save script: " + actionName, e);
        }
    }

    /**
     * Builds and registers ActionType from Action annotation.
     */
    private void saveActionType(Action action) {
        Metadata metadata = new Metadata(null, action.id(), action.name().isEmpty() ? action.id() : action.name(), action.description());
        metadata.setHidden(action.hidden());
        metadata.setReadOnly(true);
        metadata.setSystemTags(new HashSet<>(asList(action.systemTags())));
        ActionType actionType = new ActionType(metadata);
        actionType.setActionExecutor(action.actionExecutor());

        actionType.setParameters(Stream.of(action.parameters())
                .map(parameter -> new org.apache.unomi.api.Parameter(parameter.id(), parameter.type(), parameter.multivalued()))
                .collect(Collectors.toList()));
        definitionsService.setActionType(actionType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(String actionName) {
        validateNotEmpty(actionName, "Action name");

        LOGGER.info("Removing script: {}", actionName);

        ScriptMetadata removedMetadata = scriptMetadataCache.remove(actionName);
        persistenceService.remove(actionName, GroovyAction.class);

        // Clean up error tracking to prevent memory leak
        loggedRefreshErrors.remove(actionName);

        if (removedMetadata != null) {
            Action actionAnnotation = getActionAnnotation(removedMetadata.getCompiledClass());
            if (actionAnnotation != null) {
                definitionsService.removeActionType(actionAnnotation.id());
            }
        }

        LOGGER.info("Script {} removed successfully", actionName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends Script> getCompiledScript(String id) {
        validateNotEmpty(id, "Script ID");

        ScriptMetadata metadata = scriptMetadataCache.get(id);
        if (metadata == null) {
            LOGGER.warn("Script {} not found in cache", id);
            return null;
        }
        return metadata.getCompiledClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScriptMetadata getScriptMetadata(String actionName) {
        validateNotEmpty(actionName, "Action name");

        return scriptMetadataCache.get(actionName);
    }

    /**
     * Creates GroovyCodeSource for compilation.
     */
    private GroovyCodeSource buildClassScript(String groovyScript, String actionName) {
        return new GroovyCodeSource(groovyScript, actionName, "/groovy/script");
    }

    /**
     * Persists script to storage.
     */
    private void saveScript(String actionName, String script) {
        GroovyAction groovyScript = new GroovyAction(actionName, script);
        persistenceService.save(groovyScript);
        LOGGER.info("The script {} has been persisted.", actionName);
    }

    /**
     * Refreshes scripts from persistence with selective recompilation.
     * Uses hash-based change detection and atomic cache updates.
     */
    private void refreshGroovyActions() {
        long startTime = System.currentTimeMillis();

        Map<String, ScriptMetadata> newMetadataCache = new ConcurrentHashMap<>();
        int unchangedCount = 0;
        int recompiledCount = 0;
        int errorCount = 0;
        int newErrorCount = 0;
        long totalCompilationTime = 0;

        for (GroovyAction groovyAction : persistenceService.getAllItems(GroovyAction.class)) {
            String actionName = groovyAction.getName();
            String scriptContent = groovyAction.getScript();

            try {
                ScriptMetadata existingMetadata = scriptMetadataCache.get(actionName);
                if (existingMetadata != null && !existingMetadata.hasChanged(scriptContent)) {
                    newMetadataCache.put(actionName, existingMetadata);
                    unchangedCount++;
                    LOGGER.debug("Script {} unchanged during refresh, keeping cached version", actionName);
                } else {
                    if (recompiledCount == 0) {
                        LOGGER.info("Refreshing scripts from persistence layer...");
                    }

                    long compilationStartTime = System.currentTimeMillis();
                    ScriptMetadata metadata = compileAndCreateMetadata(actionName, scriptContent);
                    long compilationTime = System.currentTimeMillis() - compilationStartTime;
                    totalCompilationTime += compilationTime;

                    // Clear error tracking on successful compilation
                    loggedRefreshErrors.remove(actionName);

                    newMetadataCache.put(actionName, metadata);
                    recompiledCount++;
                    LOGGER.info("Script {} recompiled during refresh ({}ms)", actionName, compilationTime);
                }

            } catch (Exception e) {
                if (newErrorCount == 0 && recompiledCount == 0) {
                    LOGGER.info("Refreshing scripts from persistence layer...");
                }

                errorCount++;

                // Prevent log spam for repeated compilation errors during refresh
                String errorMessage = e.getMessage();
                Set<String> scriptErrors = loggedRefreshErrors.get(actionName);

                if (scriptErrors == null || !scriptErrors.contains(errorMessage)) {
                    newErrorCount++;
                    LOGGER.error("Failed to refresh script: {}", actionName, e);

                    // Prevent memory leak by limiting tracked errors before adding new entries
                    if (scriptErrors == null && loggedRefreshErrors.size() >= MAX_LOGGED_ERRORS) {
                        // Remove one random entry to make space (simple eviction)
                        String firstKey = loggedRefreshErrors.keySet().iterator().next();
                        loggedRefreshErrors.remove(firstKey);
                    }

                    // Now safely add the error
                    if (scriptErrors == null) {
                        scriptErrors = ConcurrentHashMap.newKeySet();
                        loggedRefreshErrors.put(actionName, scriptErrors);
                    }
                    scriptErrors.add(errorMessage);

                    LOGGER.warn("Keeping existing version of script {} due to compilation error", actionName);
                }

                ScriptMetadata existingMetadata = scriptMetadataCache.get(actionName);
                if (existingMetadata != null) {
                    newMetadataCache.put(actionName, existingMetadata);
                }
            }
        }

        this.scriptMetadataCache = newMetadataCache;

        if (recompiledCount > 0 || newErrorCount > 0) {
            long totalTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Script refresh completed: {} unchanged, {} recompiled, {} errors. Total time: {}ms",
                    unchangedCount, recompiledCount, errorCount, totalTime);
            LOGGER.debug("Refresh metrics: Recompilation time: {}ms, Cache update overhead: {}ms",
                    totalCompilationTime, totalTime - totalCompilationTime);
        } else {
            LOGGER.debug("Script refresh completed: {} scripts checked, no changes detected ({}ms)",
                    unchangedCount, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Initializes periodic script refresh timer.
     */
    private void initializeTimers() {
        TaskExecutor refreshGroovyActionsTaskExecutor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return REFRESH_ACTIONS_TASK_TYPE;
            }

            @Override
            public void execute(ScheduledTask task, TaskExecutor.TaskStatusCallback callback) {
                try {
                    refreshGroovyActions();
                    callback.complete();
                } catch (Exception e) {
                    LOGGER.error("Error while reassigning profile data", e);
                    callback.fail(e.getMessage());
                }
            }
        };

        schedulerService.registerTaskExecutor(refreshGroovyActionsTaskExecutor);

        if (this.refreshGroovyActionsTaskId != null) {
            schedulerService.cancelTask(this.refreshGroovyActionsTaskId);
        }
        this.refreshGroovyActionsTaskId = schedulerService.newTask(REFRESH_ACTIONS_TASK_TYPE)
                .withPeriod(config.services_groovy_actions_refresh_interval(), TimeUnit.MILLISECONDS)
                .nonPersistent()
                .schedule().getItemId();
    }
}
