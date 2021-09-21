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
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the GroovyActionService. Allows to create a groovy action from a groovy file
 */
public class GroovyActionsServiceImpl implements GroovyActionsService {

    private BundleContext bundleContext;

    private GroovyScriptEngine groovyScriptEngine;

    private GroovyShell groovyShell;

    private Map<String, GroovyCodeSource> groovyCodeSourceMap;

    private static final Logger logger = LoggerFactory.getLogger(GroovyActionsServiceImpl.class.getName());

    private static final String NECESSARY_IMPORT =
            "import org.apache.unomi.api.services.EventService\n" + "import org.apache.unomi.groovy.actions.annotations.Action\n"
                    + "import org.apache.unomi.groovy.actions.annotations.Parameter\n";

    private static final String BASE_SCRIPT_NAME = "BaseScript";

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

    public GroovyScriptEngine getGroovyScriptEngine() {
        return groovyScriptEngine;
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
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.setScriptBaseClass(BASE_SCRIPT_NAME);
        groovyScriptEngine.setConfig(compilerConfiguration);
        groovyShell = new GroovyShell(groovyScriptEngine.getGroovyClassLoader(), compilerConfiguration);
        groovyShell.setVariable("actionExecutorDispatcher", actionExecutorDispatcher);
        groovyShell.setVariable("definitionsService", definitionsService);
        try {
            loadBaseScript();
        } catch (IOException e) {
            logger.error("Failed to load base script", e);
        }
        initializeTimers();
        logger.info("Groovy action service initialized.");
    }

    private void loadBaseScript() throws IOException {
        Enumeration<URL> groovyBaseScripts = bundleContext.getBundle().findEntries("META-INF/base", "BaseScript.groovy", true);
        if (groovyBaseScripts == null) {
            return;
        }
        while (groovyBaseScripts.hasMoreElements()) {
            URL groovyActionURL = groovyBaseScripts.nextElement();
            logger.debug("Found Groovy base script at {}, loading... ", groovyActionURL.getPath());
            GroovyCodeSource groovyCodeSource = new GroovyCodeSource(IOUtils.toString(groovyActionURL.openStream()), BASE_SCRIPT_NAME,
                    "/groovy/script");
            groovyCodeSourceMap.put(BASE_SCRIPT_NAME, groovyCodeSource);
            groovyScriptEngine.getGroovyClassLoader().parseClass(groovyCodeSource, true);
        }
    }

    @Override
    public void save(String actionName, String groovyScript) {
        handleFile(actionName, groovyScript);
    }

    private void handleFile(String actionName, String groovyScript) {
        buildClassScript(groovyScript, actionName);
        try {
            saveActionType(
                    groovyScriptEngine.getGroovyClassLoader().loadClass(actionName).getMethod("execute").getAnnotation(Action.class));
            saveScript(actionName, groovyScript);
            logger.info("The script {} has been loaded.", actionName);
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            logger.error("Failed to save the script {}", actionName, e);
        }
    }

    private void saveActionType(Action action) {
        Metadata metadata = new Metadata(null, action.id(), action.name().equals("") ? action.id() : action.name(), action.description());
        metadata.setHidden(action.hidden());
        metadata.setReadOnly(true);
        metadata.setSystemTags(new HashSet<>(Arrays.asList(action.systemTags())));
        ActionType actionType = new ActionType(metadata);
        actionType.setActionExecutor(action.actionExecutor());

        actionType.setParameters(Stream.of(action.parameters())
                .map(parameter -> new org.apache.unomi.api.Parameter(parameter.id(), parameter.type(), parameter.multivalued()))
                .collect(Collectors.toList()));
        definitionsService.setActionType(actionType);
    }

    @Override
    public void remove(String id) {
        removeActionType(id);
        persistenceService.remove(id, GroovyAction.class);
    }

    @Override
    public GroovyCodeSource getGroovyCodeSource(String id) {
        return groovyCodeSourceMap.get(id);
    }

    private void removeActionType(String actionId) {
        try {
            definitionsService.removeActionType(groovyScriptEngine.getGroovyClassLoader().loadClass(actionId).getMethod("execute").getAnnotation(Action.class).id());
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            logger.error("Failed to remove the groovy action {}", actionId, e);
        }
    }

    private GroovyCodeSource buildClassScript(String groovyScript, String actionName) {
        String script = NECESSARY_IMPORT + groovyScript;
        GroovyCodeSource groovyCodeSource = new GroovyCodeSource(script, actionName, "/groovy/script");
        groovyScriptEngine.getGroovyClassLoader().parseClass(groovyCodeSource, true);
        return groovyCodeSource;
    }

    private void saveScript(String name, String script) {
        GroovyAction groovyScript = new GroovyAction(name, script);
        persistenceService.save(groovyScript);
    }

    private void refreshGroovyActions() {
        GroovyCodeSource baseScript = groovyCodeSourceMap.get(BASE_SCRIPT_NAME);
        groovyCodeSourceMap = new HashMap<>();
        groovyCodeSourceMap.put(BASE_SCRIPT_NAME, baseScript);
        persistenceService.getAllItems(GroovyAction.class).forEach(groovyAction -> groovyCodeSourceMap
                .put(groovyAction.getName(), buildClassScript(groovyAction.getScript(), groovyAction.getName())));
    }

    private void initializeTimers() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                refreshGroovyActions();
            }
        };
        schedulerService.getScheduleExecutorService().scheduleWithFixedDelay(task, 0, groovyActionsRefreshInterval, TimeUnit.MILLISECONDS);
    }
}
