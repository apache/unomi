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

import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionDispatcher;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.metrics.MetricAdapter;
import org.apache.unomi.metrics.MetricsService;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * An implementation of an ActionDispatcher for the Groovy language. This dispatcher will load the groovy action script matching to an
 * actionName. If a script if found, it will be executed.
 */
@Component(service = ActionDispatcher.class)
public class GroovyActionDispatcher implements ActionDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroovyActionDispatcher.class.getName());

    private static final String GROOVY_PREFIX = "groovy";

    private MetricsService metricsService;
    private GroovyActionsService groovyActionsService;
    private TracerService tracerService;

    @Reference
    public void setMetricsService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Reference
    public void setGroovyActionsService(GroovyActionsService groovyActionsService) {
        this.groovyActionsService = groovyActionsService;
    }

    @Reference
    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    public String getPrefix() {
        return GROOVY_PREFIX;
    }

    public Integer execute(Action action, Event event, String actionName) {
        RequestTracer tracer = tracerService.getCurrentTracer();
        if (!tracer.isEnabled()) {
            tracer.setEnabled(true);
        }

        tracer.startOperation("groovy-action", "Executing Groovy action", new HashMap<String, Object>() {{
            put("action.name", actionName);
            put("action.type", action.getActionTypeId());
            put("event.type", event.getEventType());
        }});

        try {
            GroovyCodeSource groovyCodeSource = groovyActionsService.getGroovyCodeSource(actionName);
            if (groovyCodeSource == null) {
                LOGGER.warn("Couldn't find a Groovy action with name {}, action will not execute !", actionName);
                tracer.trace("Action not found", null);
                return EventService.NO_CHANGE;
            }

            GroovyShell groovyShell = groovyActionsService.getGroovyShell();
            groovyShell.setVariable("action", action);
            groovyShell.setVariable("event", event);
            Script script = groovyShell.parse(groovyCodeSource);
            try {
                return new MetricAdapter<Integer>(metricsService, this.getClass().getName() + ".action.groovy." + actionName) {
                    @Override
                    public Integer execute(Object... args) throws Exception {
                        return (Integer) script.invokeMethod("execute", null);
                    }
                }.runWithTimer();
            } catch (Exception e) {
                LOGGER.error("Error executing Groovy action with key={}", actionName, e);
                tracer.trace("Error executing action", e);
                return EventService.NO_CHANGE;
            }
        } finally {
            tracer.endOperation(null, "Completed Groovy action execution");
        }
    }
}
