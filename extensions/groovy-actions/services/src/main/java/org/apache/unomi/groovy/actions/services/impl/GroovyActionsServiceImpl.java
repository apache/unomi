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
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
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
    private ScheduledFuture<?> scheduledFuture;
    
    private final Object compilationLock = new Object();
    private GroovyShell compilationShell;
    private volatile Map<String, ScriptMetadata> scriptMetadataCache = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(GroovyActionsServiceImpl.class.getName());
    private static final String BASE_SCRIPT_NAME = "BaseScript";

    private DefinitionsService definitionsService;
    private PersistenceService persistenceService;
    private SchedulerService schedulerService;
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
        
        initializeTimers();
        LOGGER.info("Groovy action service initialized with {} scripts", scriptMetadataCache.size());
    }

    @Deactivate
    public void onDestroy() {
        LOGGER.debug("onDestroy Method called");
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(true);
        }
    }

    /**
     * Loads the base script that provides utility functions for Groovy actions.
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
                Class<? extends Script> scriptClass = compileScript(actionName, scriptContent);
                long scriptCompilationTime = System.currentTimeMillis() - scriptStartTime;
                totalCompilationTime += scriptCompilationTime;
                
                ScriptMetadata metadata = new ScriptMetadata(actionName, scriptContent, scriptClass);
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
     * {@inheritDoc}
     * Implementation performs hash-based change detection to skip unnecessary recompilation.
     */
    @Override
    public void save(String actionName, String groovyScript) {
        if (actionName == null || actionName.trim().isEmpty()) {
            throw new IllegalArgumentException("Action name cannot be null or empty");
        }
        if (groovyScript == null || groovyScript.trim().isEmpty()) {
            throw new IllegalArgumentException("Groovy script cannot be null or empty");
        }
        
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
            Class<? extends Script> scriptClass = compileScript(actionName, groovyScript);
            long compilationTime = System.currentTimeMillis() - compilationStartTime;
            
            Action actionAnnotation = scriptClass.getMethod("execute").getAnnotation(Action.class);
            if (actionAnnotation != null) {
                saveActionType(actionAnnotation);
            }
            
            saveScript(actionName, groovyScript);
            
            ScriptMetadata metadata = new ScriptMetadata(actionName, groovyScript, scriptClass);
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
    public void remove(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Script ID cannot be null or empty");
        }
        
        LOGGER.info("Removing script: {}", id);
        
        ScriptMetadata removedMetadata = scriptMetadataCache.remove(id);
        persistenceService.remove(id, GroovyAction.class);
        
        try {
            if (removedMetadata != null) {
                Class<? extends Script> cachedClass = removedMetadata.getCompiledClass();
                Action actionAnnotation = cachedClass.getMethod("execute").getAnnotation(Action.class);
                if (actionAnnotation != null) {
                    definitionsService.removeActionType(actionAnnotation.id());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to remove action type for script: {}", id, e);
        }
        
        LOGGER.info("Script {} removed successfully", id);
    }


    /**
     * {@inheritDoc}
     * Performance Warning: Compiles on-demand if not cached.
     */
    @Override
    public Class<? extends Script> getOrCompileScript(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Script ID cannot be null or empty");
        }
        
        ScriptMetadata metadata = scriptMetadataCache.get(id);
        if (metadata != null) {
            return metadata.getCompiledClass();
        }
        
        long startTime = System.currentTimeMillis();
        LOGGER.warn("Script {} not found in cache, compiling on-demand (performance warning)", id);
        
        GroovyAction groovyAction = persistenceService.load(id, GroovyAction.class);
        if (groovyAction == null) {
            LOGGER.warn("Script {} not found in persistence, returning null ({}ms)", id, 
                System.currentTimeMillis() - startTime);
            return null;
        }
        
        try {
            long compilationStartTime = System.currentTimeMillis();
            Class<? extends Script> scriptClass = compileScript(id, groovyAction.getScript());
            long compilationTime = System.currentTimeMillis() - compilationStartTime;
            
            ScriptMetadata newMetadata = new ScriptMetadata(id, groovyAction.getScript(), scriptClass);
            scriptMetadataCache.put(id, newMetadata);
            
            long totalTime = System.currentTimeMillis() - startTime;
            LOGGER.warn("On-demand compilation completed for {} (total: {}ms, compilation: {}ms)", 
                id, totalTime, compilationTime);
            return scriptClass;
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            LOGGER.error("Failed to compile script {} on-demand ({}ms)", id, totalTime, e);
            return null;
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends Script> getCompiledScript(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Script ID cannot be null or empty");
        }
        
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
        if (actionName == null || actionName.trim().isEmpty()) {
            throw new IllegalArgumentException("Action name cannot be null or empty");
        }
        
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
                    Class<? extends Script> scriptClass = compileScript(actionName, scriptContent);
                    long compilationTime = System.currentTimeMillis() - compilationStartTime;
                    totalCompilationTime += compilationTime;
                    
                    ScriptMetadata metadata = new ScriptMetadata(actionName, scriptContent, scriptClass);
                    newMetadataCache.put(actionName, metadata);
                    recompiledCount++;
                    LOGGER.info("Script {} recompiled during refresh ({}ms)", actionName, compilationTime);
                }
                
            } catch (Exception e) {
                if (errorCount == 0 && recompiledCount == 0) {
                    LOGGER.info("Refreshing scripts from persistence layer...");
                }
                
                errorCount++;
                LOGGER.error("Failed to refresh script: {}", actionName, e);
                
                ScriptMetadata existingMetadata = scriptMetadataCache.get(actionName);
                if (existingMetadata != null) {
                    newMetadataCache.put(actionName, existingMetadata);
                    LOGGER.warn("Keeping existing version of script {} due to compilation error", actionName);
                }
            }
        }
        
        this.scriptMetadataCache = newMetadataCache;
        
        if (recompiledCount > 0 || errorCount > 0) {
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
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                refreshGroovyActions();
            }
        };
        scheduledFuture = schedulerService.getScheduleExecutorService().scheduleWithFixedDelay(task, 0, config.services_groovy_actions_refresh_interval(),
                TimeUnit.MILLISECONDS);
    }
}
