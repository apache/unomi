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
import org.apache.commons.io.IOUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.groovy.actions.GroovyAction;
import org.apache.unomi.groovy.actions.GroovyBundleResourceConnector;
import org.apache.unomi.groovy.actions.annotations.Action;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.services.actions.ActionExecutorDispatcher;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 * Implementation of the GroovyActionService. Allows to create a groovy action from a groovy file
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
    private GroovyShell groovyShell;
    private Map<String, GroovyCodeSource> groovyCodeSourceMap;
    private ScheduledFuture<?> scheduledFuture;
    private ScheduledTask scheduledTask;

    private static final Logger LOGGER = LoggerFactory.getLogger(GroovyActionsServiceImpl.class.getName());
    private static final String BASE_SCRIPT_NAME = "BaseScript";

    private DefinitionsService definitionsService;
    private PersistenceService persistenceService;
    private SchedulerService schedulerService;
    private ExecutionContextManager contextManager;
    private ActionExecutorDispatcher actionExecutorDispatcher;
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

    @Reference
    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    @Reference
    public void setActionExecutorDispatcher(ActionExecutorDispatcher actionExecutorDispatcher) {
        this.actionExecutorDispatcher = actionExecutorDispatcher;
    }

    public GroovyShell getGroovyShell() {
        return groovyShell;
    }

    @Activate
    public void start(GroovyActionsServiceConfig config, BundleContext bundleContext) {
        LOGGER.debug("postConstruct {}", bundleContext.getBundle());

        this.config = config;
        this.bundleContext = bundleContext;
        this.groovyCodeSourceMap = new HashMap<>();

        GroovyBundleResourceConnector bundleResourceConnector = new GroovyBundleResourceConnector(bundleContext);
        GroovyClassLoader groovyLoader = new GroovyClassLoader(bundleContext.getBundle().adapt(BundleWiring.class).getClassLoader());
        this.groovyScriptEngine = new GroovyScriptEngine(bundleResourceConnector, groovyLoader);

        initializeGroovyShell();
        try {
            loadBaseScript();
        } catch (IOException e) {
            LOGGER.error("Failed to load base script", e);
        }
        initializeTimers();
        LOGGER.info("Groovy action service initialized.");
    }

    @Deactivate
    public void onDestroy() {
        LOGGER.debug("onDestroy Method called");
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(true);
        }
        if (scheduledTask != null) {
            schedulerService.cancelTask(scheduledTask.getItemId());
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

    @Override
    public void save(String actionName, String groovyScript) {
        GroovyCodeSource groovyCodeSource = buildClassScript(groovyScript, actionName);
        try {
            saveActionType(groovyShell.parse(groovyCodeSource).getClass().getMethod("execute").getAnnotation(Action.class));
            saveScript(actionName, groovyScript);
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
        if (groovyCodeSourceMap.containsKey(id)) {
            try {
                definitionsService.removeActionType(
                        groovyShell.parse(groovyCodeSourceMap.get(id)).getClass().getMethod("execute").getAnnotation(Action.class).id());
            } catch (NoSuchMethodException e) {
                LOGGER.error("Failed to delete the action type for the id {}", id, e);
            }
            persistenceService.remove(id, GroovyAction.class);
        }
    }

    @Override
    public GroovyCodeSource getGroovyCodeSource(String id) {
        return groovyCodeSourceMap.get(id);
    }

    /**
     * Build a GroovyCodeSource object and add it to the class loader of the groovyScriptEngine
     *
     * @param groovyScript groovy script as a string
     * @param actionName   Name of the action
     * @return Built GroovyCodeSource
     */
    private GroovyCodeSource buildClassScript(String groovyScript, String actionName) {
        return new GroovyCodeSource(groovyScript, actionName, "/groovy/script");
    }

    private void saveScript(String actionName, String script) {
        GroovyAction groovyScript = new GroovyAction(actionName, script);
        persistenceService.save(groovyScript);
        LOGGER.info("The script {} has been persisted.", actionName);
    }

    private void refreshGroovyActions() {
        Map<String, GroovyCodeSource> refreshedGroovyCodeSourceMap = new HashMap<>();
        persistenceService.getAllItems(GroovyAction.class).forEach(groovyAction -> refreshedGroovyCodeSourceMap
                .put(groovyAction.getName(), buildClassScript(groovyAction.getScript(), groovyAction.getName())));
        groovyCodeSourceMap = refreshedGroovyCodeSourceMap;
    }

    private void initializeTimers() {
        scheduledTask = schedulerService.newTask("groovy-actions-refresh")
            .nonPersistent()  // Cache-like refresh, should not be persisted
            .withPeriod(config.services_groovy_actions_refresh_interval(), TimeUnit.MILLISECONDS)
            .withFixedDelay() // Sequential execution
            .withSimpleExecutor(() -> contextManager.executeAsSystem(() -> {
                refreshGroovyActions();
                return null;
            }))
            .schedule();
    }
}
