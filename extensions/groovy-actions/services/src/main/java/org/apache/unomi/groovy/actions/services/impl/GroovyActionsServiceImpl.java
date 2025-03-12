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
import org.apache.unomi.groovy.actions.annotations.Action;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.services.actions.ActionExecutorDispatcher;
import org.apache.unomi.services.common.cache.AbstractMultiTypeCachingService;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.osgi.framework.Bundle;
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
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
 * Implementation of the GroovyActionService. Allows to create a groovy action from a groovy file
 */
@Component(service = GroovyActionsService.class, configurationPid = "org.apache.unomi.groovy.actions")
@Designate(ocd = GroovyActionsServiceImpl.GroovyActionsServiceConfig.class)
public class GroovyActionsServiceImpl extends AbstractMultiTypeCachingService implements GroovyActionsService {

    @ObjectClassDefinition(name = "Groovy actions service config", description = "The configuration for the Groovy actions service")
    public @interface GroovyActionsServiceConfig {
        int services_groovy_actions_refresh_interval() default 1000;
    }

    private GroovyScriptEngine groovyScriptEngine;
    private GroovyShell groovyShell;
    private Map<String, Map<String, GroovyCodeSource>> groovyCodeSourceMapByTenant = new ConcurrentHashMap<>();
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
            .withPostProcessor(this::postProcessGroovyAction)
            .withStreamProcessor(this::processGroovyScript)
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

    @Override
    public GroovyShell getGroovyShell() {
        return groovyShell;
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
                            // Apply post-processor if defined
                            if (config.getPostProcessor() != null) {
                                config.getPostProcessor().accept(finalItem);
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
            String groovyScript = IOUtils.toString(inputStream);
            
            // Create the GroovyAction instance
            GroovyAction groovyAction = new GroovyAction(actionName, groovyScript);
            
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

        initializeGroovyShell();
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
        GroovyCodeSource groovyCodeSource = new GroovyCodeSource(IOUtils.toString(groovyBaseScriptURL.openStream()), BASE_SCRIPT_NAME, "/groovy/script");
        groovyScriptEngine.getGroovyClassLoader().parseClass(groovyCodeSource, true);
    }

    /**
     * Initialize the groovyShell object and define the configuration which contains the name of the base script
     */
    private void initializeGroovyShell() {
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.addCompilationCustomizers(createImportCustomizer());

        compilerConfiguration.setScriptBaseClass(BASE_SCRIPT_NAME);
        groovyScriptEngine.setConfig(compilerConfiguration);
        groovyShell = new GroovyShell(groovyScriptEngine.getGroovyClassLoader(), compilerConfiguration);
        groovyShell.setVariable("actionExecutorDispatcher", actionExecutorDispatcher);
        groovyShell.setVariable("definitionsService", definitionsService);
        groovyShell.setVariable("logger", LoggerFactory.getLogger("GroovyAction"));
    }

    private ImportCustomizer createImportCustomizer() {
        ImportCustomizer importCustomizer = new ImportCustomizer();
        importCustomizer.addImports("org.apache.unomi.api.services.EventService", "org.apache.unomi.groovy.actions.annotations.Action",
                "org.apache.unomi.groovy.actions.annotations.Parameter");
        return importCustomizer;
    }

    /**
     * Post-process a GroovyAction, creating a GroovyCodeSource and potentially registering an ActionType
     * 
     * @param groovyAction the GroovyAction to process
     */
    private void postProcessGroovyAction(GroovyAction groovyAction) {
        try {
            String actionName = groovyAction.getName();
            String script = groovyAction.getScript();
            
            GroovyCodeSource groovyCodeSource = new GroovyCodeSource(script, actionName, "/groovy/script");
            
            // Store the code source in our tenant map for runtime access
            String tenantId = contextManager.getCurrentContext().getTenantId();
            Map<String, GroovyCodeSource> groovyCodeSourceMap = groovyCodeSourceMapByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
            groovyCodeSourceMap.put(actionName, groovyCodeSource);
            
            // Try to extract the Action annotation and register the ActionType
            try {
                Action actionAnnotation = groovyShell.parse(groovyCodeSource).getClass().getMethod("execute").getAnnotation(Action.class);
                if (actionAnnotation != null) {
                    saveActionType(actionAnnotation);
                }
            } catch (NoSuchMethodException e) {
                LOGGER.warn("Failed to extract Action annotation from Groovy script {}: {}", actionName, e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.error("Error post-processing Groovy action {}: {}", groovyAction.getName(), e.getMessage(), e);
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
                .withPostProcessor(this::postProcessGroovyAction)
                .withStreamProcessor(this::processGroovyScript)
                .build();
            
            return Collections.singleton(updatedConfig);
        }
        
        return Collections.singleton(groovyActionTypeConfig);
    }

    @Override
    public void save(String actionName, String groovyScript) {
        GroovyCodeSource groovyCodeSource = buildClassScript(groovyScript, actionName);
        try {
            saveActionType(groovyShell.parse(groovyCodeSource).getClass().getMethod("execute").getAnnotation(Action.class));
            
            // Create and save the GroovyAction
            GroovyAction groovyAction = new GroovyAction(actionName, groovyScript);
            saveItem(groovyAction, GroovyAction::getName, GroovyAction.ITEM_TYPE);
            
            // Also update our code source map for immediate use
            String tenantId = contextManager.getCurrentContext().getTenantId();
            Map<String, GroovyCodeSource> groovyCodeSourceMap = groovyCodeSourceMapByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
            groovyCodeSourceMap.put(actionName, groovyCodeSource);
            
            LOGGER.info("The script {} has been loaded.", actionName);
        } catch (NoSuchMethodException e) {
            LOGGER.error("Failed to save the script {}", actionName, e);
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
    public void remove(String id) {
        try {
            // Get the code source to extract action type ID before removal
            String tenantId = contextManager.getCurrentContext().getTenantId();
            Map<String, GroovyCodeSource> groovyCodeSourceMap = groovyCodeSourceMapByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
            GroovyCodeSource codeSource = groovyCodeSourceMap.get(id);
            
            if (codeSource != null) {
                try {
                    Action actionAnnotation = groovyShell.parse(codeSource).getClass().getMethod("execute").getAnnotation(Action.class);
                    if (actionAnnotation != null) {
                        definitionsService.removeActionType(actionAnnotation.id());
                    }
                } catch (NoSuchMethodException e) {
                    LOGGER.error("Failed to get action annotation for removal: {}", id, e);
                }
            }
            
            // Remove from persistent storage and cache
            removeItem(id, GroovyAction.class, GroovyAction.ITEM_TYPE);
            
            // Remove from our code source map
            if (groovyCodeSourceMap.containsKey(id)) {
                groovyCodeSourceMap.remove(id);
            }
            
            LOGGER.info("The script {} has been removed.", id);
        } catch (Exception e) {
            LOGGER.error("Error removing Groovy action: {}", id, e);
        }
    }

    @Override
    public GroovyCodeSource getGroovyCodeSource(String id) {
        String tenantId = contextManager.getCurrentContext().getTenantId();
        Map<String, GroovyCodeSource> groovyCodeSourceMap = groovyCodeSourceMapByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        return groovyCodeSourceMap.get(id);
    }

    /**
     * Build a GroovyCodeSource object
     *
     * @param groovyScript groovy script as a string
     * @param actionName   Name of the action
     * @return Built GroovyCodeSource
     */
    private GroovyCodeSource buildClassScript(String groovyScript, String actionName) {
        return new GroovyCodeSource(groovyScript, actionName, "/groovy/script");
    }
}
