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

import groovy.lang.GroovyCodeSource;
import groovy.util.GroovyScriptEngine;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.groovy.actions.GroovyAction;
import org.apache.unomi.groovy.actions.GroovyBundleResourceConnector;
import org.apache.unomi.groovy.actions.annotations.Action;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the GroovyActionService. Allows to create a groovy action from a groovy file
 */
public class GroovyActionsServiceImpl implements GroovyActionsService {

    private BundleContext bundleContext;

    private static final Logger logger = LoggerFactory.getLogger(GroovyActionsServiceImpl.class.getName());

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Reference
    private DefinitionsService definitionsService;

    @Reference
    private PersistenceService persistenceService;

    @Reference
    private SchedulerService schedulerService;

    private List<GroovyAction> groovyActions;

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

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        initializeTimers();
        logger.info("Groovy action service initialized.");
    }

    @Override
    public void save(String actionName, String groovyScript) {
        handleFile(actionName, groovyScript);
    }

    private void handleFile(String actionName, String groovyScript) {
        GroovyBundleResourceConnector bundleResourceConnector = new GroovyBundleResourceConnector(bundleContext);

        GroovyCodeSource groovyCodeSource = new GroovyCodeSource(groovyScript, actionName, "/groovy/script");
        GroovyScriptEngine engine = new GroovyScriptEngine(bundleResourceConnector,
                bundleContext.getBundle().adapt(BundleWiring.class).getClassLoader());
        Class classScript = engine.getGroovyClassLoader().parseClass(groovyCodeSource);
        saveActionType((Action) classScript.getAnnotation(Action.class));

        saveScript(actionName, groovyScript);
        logger.info("The script {} has been loaded.", actionName);
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
        persistenceService.remove(id, GroovyAction.class);
    }

    @Override
    public GroovyAction getGroovyAction(String id) {
        return groovyActions.stream().filter(groovyAction -> groovyAction.getItemId().equals(id)).findFirst().orElse(null);
    }

    private void saveScript(String name, String script) {
        GroovyAction groovyScript = new GroovyAction(name, script);
        persistenceService.save(groovyScript);
    }

    private void refreshGroovyActions() {
        groovyActions = persistenceService.getAllItems(GroovyAction.class);
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
