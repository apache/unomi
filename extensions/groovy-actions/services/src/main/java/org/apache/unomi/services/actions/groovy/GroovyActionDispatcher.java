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
package org.apache.unomi.services.actions.groovy;

import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionDispatcher;
import org.apache.unomi.metrics.MetricAdapter;
import org.apache.unomi.metrics.MetricsService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of an ActionDispatcher for the Groovy language. It will use actionName and match them against
 * groovy script file names deployed in the same directory as the action descriptors (META-INF/cxs/actions)
 */
public class GroovyActionDispatcher implements ActionDispatcher, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(GroovyActionDispatcher.class.getName());

    private Map<String, GroovyAction> groovyActionsByName = new ConcurrentHashMap<>();
    private Map<BundleContext, List<GroovyAction>> groovyActionsByBundle = new ConcurrentHashMap<>();
    private MetricsService metricsService;
    private BundleContext bundleContext;

    public void setMetricsService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public String getPrefix() {
        return "groovy";
    }

    public Integer execute(Action action, Event event, String actionName) {
        GroovyAction groovyAction = groovyActionsByName.get(actionName);
        if (groovyAction == null) {
            logger.warn("Couldn't find a Groovy action with name {}, action will not execute !", actionName);
        } else {
            try {
                return new MetricAdapter<Integer>(metricsService, this.getClass().getName() + ".action.groovy." + actionName) {
                    @Override
                    public Integer execute(Object... args) throws Exception {
                        Binding binding = new Binding();
                        binding.setVariable("groovyAction", groovyAction);
                        binding.setVariable("action", action);
                        binding.setVariable("event", event);
                        GroovyBundleResourceConnector bundleResourceConnector = new GroovyBundleResourceConnector(groovyAction.getBundleContext());
                        GroovyScriptEngine engine = new GroovyScriptEngine(bundleResourceConnector, groovyAction.getBundleContext().getBundle().adapt(BundleWiring.class).getClassLoader());
                        return (Integer) engine.run(groovyAction.getPath(), binding);
                    }
                }.runWithTimer();
            } catch (Exception e) {
                logger.error("Error executing Groovy action with key=" + actionName, e);
            }
        }

        return null;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                loadGroovyActions(bundleContext);
            }
        }

        bundleContext.addBundleListener(this);

        logger.info("Groovy Action Dispatcher initialized.");
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("Groovy Action Dispatcher shutdown.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadGroovyActions(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        unloadGroovyActions(bundleContext);
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                processBundleStartup(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                processBundleStop(event.getBundle().getBundleContext());
                break;
        }
    }


    private void addGroovyAction(BundleContext bundleContext, URL groovyActionURL) {
        GroovyAction groovyAction = new GroovyAction(groovyActionURL, bundleContext);
        if (groovyActionsByName.containsKey(groovyAction.getName())) {
            logger.warn("Found an existing Groovy action with name {}. Will overwrite it!", groovyAction.getName());
        }
        groovyActionsByName.put(groovyAction.getName(), groovyAction);
        List<GroovyAction> bundleGroovyActions = groovyActionsByBundle.get(bundleContext);
        if (bundleGroovyActions == null) {
            bundleGroovyActions = new ArrayList<>();
        }
        bundleGroovyActions.add(groovyAction);
        groovyActionsByBundle.put(bundleContext, bundleGroovyActions);
    }

    private void removeGroovyActions(BundleContext bundleContext) {
        List<GroovyAction> bundleGroovyActions = groovyActionsByBundle.get(bundleContext);
        if (bundleGroovyActions == null) {
            return;
        }
        for (GroovyAction groovyAction : bundleGroovyActions) {
            groovyActionsByName.remove(groovyAction.getName());
        }
        groovyActionsByBundle.remove(bundleContext);
    }

    private void loadGroovyActions(BundleContext bundleContext) {
        Enumeration<URL> bundleGroovyActions = bundleContext.getBundle().findEntries("META-INF/cxs/actions", "*.groovy", true);
        if (bundleGroovyActions == null) {
            return;
        }

        while (bundleGroovyActions.hasMoreElements()) {
            URL groovyActionURL = bundleGroovyActions.nextElement();
            logger.debug("Found Groovy action at " + groovyActionURL + ", loading... ");
            addGroovyAction(bundleContext, groovyActionURL);
        }
    }

    private void unloadGroovyActions(BundleContext bundleContext) {
        removeGroovyActions(bundleContext);
    }

}
