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
import org.apache.unomi.api.services.SchedulerService;
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
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 * Implementation of the GroovyActionService. Allows to create a groovy action from a groovy file
 */
public class GroovyActionsServiceImpl implements GroovyActionsService {

    private BundleContext bundleContext;

    private GroovyScriptEngine groovyScriptEngine;

    private GroovyShell groovyShell;

    private Map<String, GroovyCodeSource> groovyCodeSourceMap;

    private ScheduledFuture<?> scheduledFuture;

    private static final Logger logger = LoggerFactory.getLogger(GroovyActionsServiceImpl.class.getName());

    private static final String BASE_SCRIPT_NAME = "BaseScript";
    private static final String GROOVY_SOURCE_CODE_ID_SUFFIX = "-groovySourceCode";

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Reference
    private DefinitionsService definitionsService;

    @Reference
    private PersistenceService persistenceService;

    @Reference
    private SchedulerService schedulerService;

    @Reference
    private ActionExecutorDispatcher actionExecutorDispatcher;

    private Integer groovyActionsRefreshInterval = 1000;

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setGroovyActionsRefreshInterval(Integer groovyActionsRefreshInterval) {
        this.groovyActionsRefreshInterval = groovyActionsRefreshInterval;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setActionExecutorDispatcher(ActionExecutorDispatcher actionExecutorDispatcher) {
        this.actionExecutorDispatcher = actionExecutorDispatcher;
    }

    public GroovyShell getGroovyShell() {
        return groovyShell;
    }

    public void postConstruct() {
        logger.debug("postConstruct {}", bundleContext.getBundle());
        groovyCodeSourceMap = new HashMap<>();
        GroovyBundleResourceConnector bundleResourceConnector = new GroovyBundleResourceConnector(bundleContext);

        GroovyClassLoader groovyLoader = new GroovyClassLoader(bundleContext.getBundle().adapt(BundleWiring.class).getClassLoader());
        groovyScriptEngine = new GroovyScriptEngine(bundleResourceConnector, groovyLoader);

        initializeGroovyShell();
        try {
            loadBaseScript();
        } catch (IOException e) {
            logger.error("Failed to load base script", e);
        }
        initializeTimers();
        logger.info("Groovy action service initialized.");
    }

    public void onDestroy() {
        logger.debug("onDestroy Method called");
        scheduledFuture.cancel(true);
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
        logger.debug("Found Groovy base script at {}, loading... ", groovyBaseScriptURL.getPath());
        GroovyCodeSource groovyCodeSource = new GroovyCodeSource(IOUtils.toString(groovyBaseScriptURL.openStream()), BASE_SCRIPT_NAME,
                "/groovy/script");
        groovyCodeSourceMap.put(BASE_SCRIPT_NAME, groovyCodeSource);
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
            logger.info("The script {} has been loaded.", actionName);
        } catch (NoSuchMethodException e) {
            logger.error("Failed to save the script {}", actionName, e);
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
        String groovySourceCodeId = getGroovyCodeSourceIdForActionId(id);
        try {
            definitionsService.removeActionType(
                    groovyShell.parse(groovyCodeSourceMap.get(groovySourceCodeId)).getClass().getMethod("execute").getAnnotation(Action.class).id());
        } catch (NoSuchMethodException e) {
            logger.error("Failed to delete the action type for the id {}", id, e);
        }
        persistenceService.remove(groovySourceCodeId, GroovyAction.class);
        groovyCodeSourceMap.remove(groovySourceCodeId);
    }

    @Override
    public GroovyCodeSource getGroovyCodeSource(String id) {
        return groovyCodeSourceMap.get(getGroovyCodeSourceIdForActionId(id));
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

    /**
     * We use a suffix for avoiding id conflict between the actionType and the groovyAction in ElasticSearch
     * Since those items are now stored in the same ES index
     * @param actionName name/id of the actionType
     * @return id of the groovyAction source code for query/save/storage usage.
     */
    private String getGroovyCodeSourceIdForActionId(String actionName) {
        return actionName + GROOVY_SOURCE_CODE_ID_SUFFIX;
    }

    private void saveScript(String actionName, String script) {
        GroovyAction groovyScript = new GroovyAction(getGroovyCodeSourceIdForActionId(actionName), script);
        persistenceService.save(groovyScript);
    }

    private void refreshGroovyActions() {
        Map<String, GroovyCodeSource> refreshedGroovyCodeSourceMap = new HashMap<>();
        GroovyCodeSource baseScript = groovyCodeSourceMap.get(BASE_SCRIPT_NAME);
        refreshedGroovyCodeSourceMap.put(BASE_SCRIPT_NAME, baseScript);
        persistenceService.getAllItems(GroovyAction.class).forEach(groovyAction -> refreshedGroovyCodeSourceMap
                .put(groovyAction.getName(), buildClassScript(groovyAction.getScript(), groovyAction.getName())));
        groovyCodeSourceMap = refreshedGroovyCodeSourceMap;
    }

    private void initializeTimers() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                refreshGroovyActions();
            }
        };
        scheduledFuture = schedulerService.getScheduleExecutorService().scheduleWithFixedDelay(task, 0, groovyActionsRefreshInterval,
                TimeUnit.MILLISECONDS);
    }
}
