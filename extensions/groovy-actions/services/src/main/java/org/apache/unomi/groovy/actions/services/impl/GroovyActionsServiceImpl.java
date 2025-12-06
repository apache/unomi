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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.groovy.actions.GroovyAction;
import org.apache.unomi.groovy.actions.GroovyBundleResourceConnector;
import org.apache.unomi.groovy.actions.ScriptMetadata;
import org.apache.unomi.groovy.actions.annotations.Action;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.services.actions.ActionExecutorDispatcher;
import org.apache.unomi.services.common.cache.AbstractMultiTypeCachingService;
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
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

/**
 * High-performance GroovyActionsService implementation with pre-compilation,
 * hash-based change detection, and thread-safe execution.
 *
 * This implementation handles three distinct scenarios for Groovy actions:
 *
 * 1. Preloading from bundle resources:
 *    - Groovy scripts are loaded from META-INF/cxs/actions/*.groovy files
 *    - ActionTypes are registered directly during processGroovyScript
 *    - Custom loadPredefinedItemsForType handles storing code sources in tenant map
 *
 * 2. Manual saving via API:
 *    - ActionTypes are registered directly during save method
 *    - Code sources are stored in the tenant map for runtime execution
 *
 * 3. Cache refreshing from persistence:
 *    - processGroovyActionForCache is used which only stores code sources in tenant map
 *    - No ActionType persistence happens during cache refresh
 *    - Avoids circular persistence operations during refresh
 */
@Component(service = GroovyActionsService.class, configurationPid = "org.apache.unomi.groovy.actions")
@Designate(ocd = GroovyActionsServiceImpl.GroovyActionsServiceConfig.class)
public class GroovyActionsServiceImpl extends AbstractMultiTypeCachingService implements GroovyActionsService {

    @ObjectClassDefinition(name = "Groovy actions service config", description = "The configuration for the Groovy actions service")
    public @interface GroovyActionsServiceConfig {
        int services_groovy_actions_refresh_interval() default 1000;
    }

    private GroovyScriptEngine groovyScriptEngine;
    
    // Thread-safe compilation shell for ScriptMetadata
    private final Object compilationLock = new Object();
    private GroovyShell compilationShell;
    private volatile Map<String, Map<String, ScriptMetadata>> scriptMetadataCacheByTenant = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> loggedRefreshErrors = new ConcurrentHashMap<>();
    private static final int MAX_LOGGED_ERRORS = 100; // Prevent memory leak
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GroovyActionsServiceImpl.class.getName());
    private static final String BASE_SCRIPT_NAME = "BaseScript";
    // Original path for Groovy actions
    private static final String ACTIONS_LOCATION = "actions";

    private DefinitionsService definitionsService;
    private ActionExecutorDispatcher actionExecutorDispatcher;
    private GroovyActionsServiceConfig config;

    // Define the cacheable type config for GroovyAction
    private final CacheableTypeConfig<GroovyAction> groovyActionTypeConfig = CacheableTypeConfig
            .<GroovyAction>builder(GroovyAction.class, GroovyAction.ITEM_TYPE, ACTIONS_LOCATION)
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(1000) // Will be overridden by config
            .withIdExtractor(GroovyAction::getName)
            // Skip saving action types during cache refresh to avoid circular persistence operations
            .withPostProcessor(this::processGroovyActionForCache)
            .withStreamProcessor((bundleContext, url, inputStream) -> contextManager.executeAsSystem(() -> processGroovyScript(bundleContext, url, inputStream)))
            .build();

    @Reference
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @Reference
    public void setActionExecutorDispatcher(ActionExecutorDispatcher actionExecutorDispatcher) {
        this.actionExecutorDispatcher = actionExecutorDispatcher;
    }

    @Reference
    public void setCacheService(MultiTypeCacheService cacheService) {
        super.setCacheService(cacheService);
    }

    @Reference
    public void setSchedulerService(SchedulerService schedulerService) {
        super.setSchedulerService(schedulerService);
    }

    @Reference
    public void setTenantService(TenantService tenantService) {
        super.setTenantService(tenantService);
    }

    @Reference
    public void setContextManager(ExecutionContextManager contextManager) {
        super.setContextManager(contextManager);
    }

    @Reference
    public void setPersistenceService(PersistenceService persistenceService) {
        super.setPersistenceService(persistenceService);
    }

    @Activate
    public void activate(GroovyActionsServiceConfig config, BundleContext bundleContext) {
        LOGGER.debug("Activating Groovy Actions Service {}", bundleContext.getBundle());
        this.config = config;
        this.setBundleContext(bundleContext);

        // Initialize Groovy-specific components
        initializeGroovyComponents();

        // Initialize the caching service
        super.postConstruct();
    }

    @Deactivate
    @Override
    public void preDestroy() {
        LOGGER.debug("Deactivating Groovy Actions Service");
        super.preDestroy();
    }

    /**
     * Override the loadPredefinedItemsForType method to use our own extension pattern (*.groovy instead of *.json)
     * while keeping the original path structure
     */
    @Override
    @SuppressWarnings("unchecked")
    protected <T extends Serializable> void loadPredefinedItemsForType(BundleContext bundleContext, CacheableTypeConfig<T> config) {
        // Skip if this type doesn't match our GroovyAction type
        if (!config.getType().equals(GroovyAction.class)) {
            // Use the parent implementation for other types
            super.loadPredefinedItemsForType(bundleContext, config);
            return;
        }

        // Skip if this type doesn't have predefined items
        if (!config.hasPredefinedItems()) {
            return;
        }

        // Use *.groovy pattern instead of *.json for Groovy actions
        Enumeration<URL> entries = bundleContext.getBundle()
            .findEntries("META-INF/cxs/" + config.getMetaInfPath(), "*.groovy", true);

        if (entries == null) return;

        // Process entries in the same way as the parent class does
        List<URL> entryList = Collections.list(entries);
        if (config.hasUrlComparator()) {
            entryList.sort(config.getUrlComparator());
        }

        for (URL entryURL : entryList) {
            logger.debug("Found predefined Groovy action at {}, loading... ", entryURL.getPath());

            try {
                final long bundleId = bundleContext.getBundle().getBundleId();

                // Use stream processor to process the Groovy script
                try (InputStream inputStream = entryURL.openStream()) {
                    // During preloading, the processGroovyScript method will extract and register the ActionType
                    T item = config.getStreamProcessor().apply(bundleContext, entryURL, inputStream);
                    if (item == null) {
                        logger.warn("Stream processor returned null for {}", entryURL);
                        continue;
                    }

                    // Final item variable for lambda
                    final T finalItem = item;

                    // Process in system context to ensure permissions
                    contextManager.executeAsSystem(() -> {
                        try {
                            // We're skipping the post-processor here because:
                            // 1. For GroovyAction, the ActionType is already registered in processGroovyScript
                            // 2. The only other thing postProcessor does is to add the code source to the tenant map

                            // Manual handling of what's needed from the post-processor
                            // (just storing the script metadata in tenant map)
                            if (finalItem instanceof GroovyAction) {
                                GroovyAction groovyAction = (GroovyAction) finalItem;
                                String actionName = groovyAction.getName();
                                String script = groovyAction.getScript();

                                // Create and store ScriptMetadata for the new interface
                                try {
                                    ScriptMetadata metadata = compileAndCreateMetadata(actionName, script);
                                    Map<String, ScriptMetadata> scriptMetadataMap = scriptMetadataCacheByTenant
                                        .computeIfAbsent(SYSTEM_TENANT, k -> new ConcurrentHashMap<>());
                                    scriptMetadataMap.put(actionName, metadata);
                                } catch (Exception e) {
                                    logger.error("Failed to create ScriptMetadata for predefined action {}", actionName, e);
                                }
                            }

                            // Track contribution
                            addPluginContribution(bundleId, finalItem);

                            // Add to cache
                            String id = config.getIdExtractor().apply(finalItem);
                            cacheService.put(config.getItemType(), id, SYSTEM_TENANT, finalItem);

                            logger.info("Predefined Groovy action registered: {}", id);
                        } catch (Exception e) {
                            logger.error("Error processing Groovy action {}", entryURL, e);
                        }
                        return null;
                    });
                } catch (Exception e) {
                    logger.error("Error processing {} with stream processor: {}", entryURL, e.getMessage(), e);
                }
            } catch (Exception e) {
                logger.error("Error loading Groovy action {}", entryURL, e);
            }
        }
    }

    /**
     * Process a Groovy script from an input stream and create a GroovyAction.
     * This is used by AbstractMultiTypeCachingService to process .groovy files
     * instead of expecting JSON files.
     *
     * @param bundleContext the bundle context
     * @param url the URL of the resource
     * @param inputStream the input stream containing the Groovy script
     * @return a new GroovyAction instance
     */
    private GroovyAction processGroovyScript(BundleContext bundleContext, URL url, InputStream inputStream) {
        try {
            String actionName = FilenameUtils.getBaseName(url.getPath());
            String groovyScript = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

            // Create the GroovyAction instance
            GroovyAction groovyAction = new GroovyAction(actionName, groovyScript);

            // During preloading, we need to register the ActionType immediately
            // Create a code source for parsing
            GroovyCodeSource groovyCodeSource = new GroovyCodeSource(groovyScript, actionName, "/groovy/script");

            // Extract Action annotation and register the ActionType
            try {
                synchronized(compilationLock) {
                    Action actionAnnotation = compilationShell.parse(groovyCodeSource).getClass().getMethod("execute").getAnnotation(Action.class);
                    if (actionAnnotation != null) {
                        contextManager.executeAsSystem(() -> {
                            saveActionType(actionAnnotation);
                        });
                    }
                }
            } catch (NoSuchMethodException e) {
                LOGGER.warn("Failed to extract Action annotation from predefined Groovy script {}: {}", actionName, e.getMessage());
            }

            LOGGER.debug("Processed Groovy script from {}, action name: {}", url.getPath(), actionName);
            return groovyAction;

        } catch (IOException e) {
            LOGGER.error("Error processing Groovy script from {}: {}", url.getPath(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Initialize the Groovy-specific components like GroovyScriptEngine and GroovyShell
     */
    private void initializeGroovyComponents() {
        GroovyBundleResourceConnector bundleResourceConnector = new GroovyBundleResourceConnector(bundleContext);
        GroovyClassLoader groovyLoader = new GroovyClassLoader(bundleContext.getBundle().adapt(BundleWiring.class).getClassLoader());
        this.groovyScriptEngine = new GroovyScriptEngine(bundleResourceConnector, groovyLoader);

        initializeCompilationShell();
        try {
            loadBaseScript();
        } catch (IOException e) {
            LOGGER.error("Failed to load base script", e);
        }
    }

    /**
     * Load the Base script.
     * It's a script which provides utility functions that we can use in other groovy script
     * The functions added by the base script could be called by the groovy actions executed in
     * {@link org.apache.unomi.groovy.actions.GroovyActionDispatcher#execute}
     * The base script would be added in the configuration of the {@link GroovyActionsServiceImpl#groovyShell GroovyShell} , so when a
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
     * Initialize the compilation shell with proper configuration
     */
    private void initializeCompilationShell() {
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.addCompilationCustomizers(createImportCustomizer());

        compilerConfiguration.setScriptBaseClass(BASE_SCRIPT_NAME);
        groovyScriptEngine.setConfig(compilerConfiguration);
        
        // Initialize the compilation shell for ScriptMetadata
        this.compilationShell = new GroovyShell(groovyScriptEngine.getGroovyClassLoader(), compilerConfiguration);
        compilationShell.setVariable("actionExecutorDispatcher", actionExecutorDispatcher);
        compilationShell.setVariable("definitionsService", definitionsService);
        compilationShell.setVariable("logger", LoggerFactory.getLogger("GroovyAction"));
    }

    private ImportCustomizer createImportCustomizer() {
        ImportCustomizer importCustomizer = new ImportCustomizer();
        importCustomizer.addImports("org.apache.unomi.api.services.EventService", "org.apache.unomi.groovy.actions.annotations.Action",
                "org.apache.unomi.groovy.actions.annotations.Parameter");
        return importCustomizer;
    }

    /**
     * Process a GroovyAction for caching purposes, creating ScriptMetadata and storing it in the tenant map.
     * This method specifically avoids registering ActionTypes to prevent circular persistence operations.
     *
     * @param groovyAction the GroovyAction to process
     */
    private void processGroovyActionForCache(GroovyAction groovyAction) {
        try {
            String actionName = groovyAction.getName();
            String script = groovyAction.getScript();

            // Create and store ScriptMetadata for the new interface
            try {
                ScriptMetadata metadata = compileAndCreateMetadata(actionName, script);
                Map<String, ScriptMetadata> scriptMetadataMap = getScriptMetadataMap();
                scriptMetadataMap.put(actionName, metadata);
            } catch (Exception e) {
                logRefreshError(actionName, "Failed to create ScriptMetadata", e);
            }

            // We parse the script to validate it, but intentionally skip saving ActionType
            // to avoid circular persistence operations during cache refresh
            try {
                GroovyCodeSource groovyCodeSource = new GroovyCodeSource(script, actionName, "/groovy/script");
                synchronized(compilationLock) {
                    compilationShell.parse(groovyCodeSource).getClass().getMethod("execute");
                }
                // Note: We don't extract or save the ActionType here
            } catch (NoSuchMethodException e) {
                logRefreshError(actionName, "Failed to validate Groovy script", e);
            }
        } catch (Exception e) {
            logRefreshError(groovyAction.getName(), "Error processing Groovy action", e);
        }
    }

    /**
     * Logs refresh errors with rate limiting to prevent log spam.
     * Only logs the first MAX_LOGGED_ERRORS errors per action to prevent memory leaks.
     */
    private void logRefreshError(String actionName, String message, Exception e) {
        String tenantId = contextManager.getCurrentContext().getTenantId();
        Set<String> tenantErrors = loggedRefreshErrors.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet());
        
        if (tenantErrors.size() < MAX_LOGGED_ERRORS) {
            tenantErrors.add(actionName);
            LOGGER.error("{} for action {}: {}", message, actionName, e.getMessage(), e);
        } else if (tenantErrors.contains(actionName)) {
            // Already logged this action, just log at debug level
            LOGGER.debug("{} for action {}: {}", message, actionName, e.getMessage());
        } else {
            // Too many errors logged, skip this one
            LOGGER.debug("Skipping error log for action {} due to error limit ({}): {}", 
                actionName, MAX_LOGGED_ERRORS, e.getMessage());
        }
    }

    @Override
    protected Set<CacheableTypeConfig<?>> getTypeConfigs() {
        // Update refresh interval from config
        if (config != null) {
            CacheableTypeConfig<GroovyAction> updatedConfig = CacheableTypeConfig
                .<GroovyAction>builder(GroovyAction.class, GroovyAction.ITEM_TYPE, ACTIONS_LOCATION)
                .withInheritFromSystemTenant(true)
                .withRequiresRefresh(true)
                .withRefreshInterval(config.services_groovy_actions_refresh_interval())
                .withIdExtractor(GroovyAction::getName)
                // We need to skip saving the action type during cache refresh to avoid circular persistence operations.
                // During cache refresh, we're loading items that already exist in the persistence store,
                // so calling saveActionType would trigger another persistence.save operation for the same item.
                .withPostProcessor(this::processGroovyActionForCache)
                .withStreamProcessor(this::processGroovyScript)
                .build();

            return Collections.singleton(updatedConfig);
        }

        return Collections.singleton(groovyActionTypeConfig);
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
     * Thread-safe script compilation using synchronized shared shell.
     */
    private Class<? extends Script> compileScript(String actionName, String scriptContent) {
        GroovyCodeSource codeSource = new GroovyCodeSource(scriptContent, actionName, "/groovy/script");
        synchronized(compilationLock) {
            return compilationShell.parse(codeSource).getClass();
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
        } catch (NoSuchMethodException e) {
            // Scripts without an execute() method are valid; they simply have no @Action metadata
            LOGGER.debug("No execute() method found on script class {}, skipping @Action extraction", scriptClass.getName());
            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to extract action annotation", e);
            return null;
        }
    }

    /**
     * Gets the script metadata map for the current tenant.
     */
    private Map<String, ScriptMetadata> getScriptMetadataMap() {
        String tenantId = contextManager.getCurrentContext().getTenantId();
        return scriptMetadataCacheByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    @Override
    public void save(String actionName, String groovyScript) {
        validateNotEmpty(actionName, "Action name");
        validateNotEmpty(groovyScript, "Groovy script");

        long startTime = System.currentTimeMillis();
        LOGGER.info("Saving script: {}", actionName);

        try {
            Map<String, ScriptMetadata> scriptMetadataMap = getScriptMetadataMap();
            
            ScriptMetadata existingMetadata = scriptMetadataMap.get(actionName);
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

            // Create and save the GroovyAction
            GroovyAction groovyAction = new GroovyAction(actionName, groovyScript);
            saveItem(groovyAction, GroovyAction::getName, GroovyAction.ITEM_TYPE);

            // Store the new metadata
            scriptMetadataMap.put(actionName, metadata);

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
     * Build an action type from the annotation {@link Action}
     *
     * @param action Annotation containing the values to save
     */
    private void saveActionType(Action action) {
        Metadata metadata = new Metadata(null, action.id(), action.name().equals("") ? action.id() : action.name(), action.description());
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

    @Override
    public void remove(String actionName) {
        validateNotEmpty(actionName, "Action name");

        LOGGER.info("Removing script: {}", actionName);

        Map<String, ScriptMetadata> scriptMetadataMap = getScriptMetadataMap();
        
        ScriptMetadata removedMetadata = scriptMetadataMap.remove(actionName);
        
        // Clean up error tracking to prevent memory leak
        String tenantId = contextManager.getCurrentContext().getTenantId();
        Set<String> tenantErrors = loggedRefreshErrors.get(tenantId);
        if (tenantErrors != null) {
            tenantErrors.remove(actionName);
            if (tenantErrors.isEmpty()) {
                loggedRefreshErrors.remove(tenantId);
            }
        }

        // Remove from persistent storage and cache
        removeItem(actionName, GroovyAction.class, GroovyAction.ITEM_TYPE);

        if (removedMetadata != null) {
            Action actionAnnotation = getActionAnnotation(removedMetadata.getCompiledClass());
            if (actionAnnotation != null) {
                definitionsService.removeActionType(actionAnnotation.id());
            }
        }

        LOGGER.info("Script {} removed successfully", actionName);
    }

    @Override
    public Class<? extends Script> getCompiledScript(String actionName) {
        validateNotEmpty(actionName, "Script ID");

        Map<String, ScriptMetadata> scriptMetadataMap = getScriptMetadataMap();
        
        ScriptMetadata metadata = scriptMetadataMap.get(actionName);
        if (metadata == null) {
            LOGGER.warn("Script {} not found in cache", actionName);
            return null;
        }
        return metadata.getCompiledClass();
    }

    @Override
    public ScriptMetadata getScriptMetadata(String actionName) {
        validateNotEmpty(actionName, "Action name");

        Map<String, ScriptMetadata> scriptMetadataMap = getScriptMetadataMap();
        
        return scriptMetadataMap.get(actionName);
    }


}
