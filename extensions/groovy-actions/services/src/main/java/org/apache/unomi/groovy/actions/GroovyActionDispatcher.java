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
package org.apache.unomi.groovy.actions;

import groovy.lang.Script;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionDispatcher;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.metrics.MetricAdapter;
import org.apache.unomi.metrics.MetricsService;
import org.apache.unomi.services.actions.ActionExecutorDispatcher;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance ActionDispatcher for pre-compiled Groovy scripts.
 * Executes scripts without GroovyShell overhead using isolated instances.
 */
@Component(service = ActionDispatcher.class)
public class GroovyActionDispatcher implements ActionDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroovyActionDispatcher.class.getName());
    private static final Logger GROOVY_ACTION_LOGGER = LoggerFactory.getLogger("GroovyAction");

    private static final String GROOVY_PREFIX = "groovy";

    private MetricsService metricsService;
    private GroovyActionsService groovyActionsService;
    private DefinitionsService definitionsService;
    private ActionExecutorDispatcher actionExecutorDispatcher;

    @Reference
    public void setMetricsService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Reference
    public void setGroovyActionsService(GroovyActionsService groovyActionsService) {
        this.groovyActionsService = groovyActionsService;
    }

    @Reference
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @Reference
    public void setActionExecutorDispatcher(ActionExecutorDispatcher actionExecutorDispatcher) {
        this.actionExecutorDispatcher = actionExecutorDispatcher;
    }

    public String getPrefix() {
        return GROOVY_PREFIX;
    }

    public Integer execute(Action action, Event event, String actionName) {
        Class<? extends Script> scriptClass = groovyActionsService.getCompiledScript(actionName);
        if (scriptClass == null) {
            LOGGER.warn("Couldn't find a Groovy action with name {}, action will not execute!", actionName);
            return 0;
        }
        
        try {
            Script script = scriptClass.getDeclaredConstructor().newInstance();
            setScriptVariables(script, action, event);
            
            return new MetricAdapter<Integer>(metricsService, this.getClass().getName() + ".action.groovy." + actionName) {
                @Override
                public Integer execute(Object... args) throws Exception {
                    return (Integer) script.invokeMethod("execute", null);
                }
            }.runWithTimer();
            
        } catch (Exception e) {
            LOGGER.error("Error executing Groovy action with key={}", actionName, e);
        }
        return 0;
    }
    
    /**
     * Sets required variables on script instance.
     */
    private void setScriptVariables(Script script, Action action, Event event) {
        script.setProperty("action", action);
        script.setProperty("event", event);
        script.setProperty("actionExecutorDispatcher", actionExecutorDispatcher);
        script.setProperty("definitionsService", definitionsService);
        script.setProperty("logger", GROOVY_ACTION_LOGGER);
    }
}
