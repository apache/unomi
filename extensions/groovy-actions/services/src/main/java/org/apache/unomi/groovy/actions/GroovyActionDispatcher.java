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

import groovy.lang.GroovyObject;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionDispatcher;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.metrics.MetricAdapter;
import org.apache.unomi.metrics.MetricsService;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of an ActionDispatcher for the Groovy language. This dispatcher will load the groovy action script matching to an
 * actionName. If a script if found, it will be executed.
 */
public class GroovyActionDispatcher implements ActionDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(GroovyActionDispatcher.class.getName());

    private static final String GROOVY_PREFIX = "groovy";

    private MetricsService metricsService;

    private GroovyActionsService groovyActionsService;

    private BundleContext bundleContext;

    public void setMetricsService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public void setGroovyActionsService(GroovyActionsService groovyActionsService) {
        this.groovyActionsService = groovyActionsService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public String getPrefix() {
        return GROOVY_PREFIX;
    }

    public Integer execute(Action action, Event event, String actionName) {
        GroovyObject groovyObject = groovyActionsService.getGroovyObject(actionName);
        if (groovyObject == null) {
            logger.warn("Couldn't find a Groovy action with name {}, action will not execute !", actionName);
        } else {
            try {
                return new MetricAdapter<Integer>(metricsService, this.getClass().getName() + ".action.groovy." + actionName) {
                    @Override
                    public Integer execute(Object... args) throws Exception {
                        return Integer.valueOf((String) groovyObject.invokeMethod("execute", new Object[] { action, event }));
                    }
                }.runWithTimer();
            } catch (Exception e) {
                logger.error("Error executing Groovy action with key=" + actionName, e);
            }
        }
        return null;
    }
}
