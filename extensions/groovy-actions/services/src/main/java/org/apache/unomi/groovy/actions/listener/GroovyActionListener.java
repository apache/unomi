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
package org.apache.unomi.groovy.actions.listener;

import groovy.util.GroovyScriptEngine;
import org.apache.commons.io.IOUtils;
import org.apache.unomi.groovy.actions.GroovyAction;
import org.apache.unomi.groovy.actions.GroovyBundleResourceConnector;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * An implementation of a BundleListener for the Groovy language.
 * It will load the groovy files in the folder META-INF/cxs/actions.
 * The description of the action will be loaded from the ActionDescriptor annotation present in the groovy file.
 * The script will be stored in the ES index groovyAction
 */
public class GroovyActionListener implements SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(GroovyActionListener.class.getName());
    public static final String ENTRIES_LOCATION = "META-INF/cxs/actions";
    private PersistenceService persistenceService;

    private GroovyActionsService groovyActionsService;
    private BundleContext bundleContext;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setGroovyActionsService(GroovyActionsService groovyActionsService) {
        this.groovyActionsService = groovyActionsService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void postConstruct() {
        logger.debug("postConstruct {}", bundleContext.getBundle());
        createIndex();
        loadGroovyActions(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                loadGroovyActions(bundle.getBundleContext());
            }
        }

        bundleContext.addBundleListener(this);
        logger.info("Groovy Action Dispatcher initialized.");
    }

    public void preDestroy() {
        processBundleStop(bundleContext);
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
                if (!event.getBundle().getSymbolicName().equals("org.apache.unomi.groovy-actions-services")) {
                    processBundleStop(event.getBundle().getBundleContext());
                }
                break;
        }
    }

    public void createIndex() {
        if (persistenceService.createIndex(GroovyAction.ITEM_TYPE)) {
            logger.info("GroovyAction index created");
        } else {
            logger.info("GroovyAction index already exists");
        }
    }

    private void addGroovyAction(URL groovyActionURL) {
        try {
            groovyActionsService.save(IOUtils.toString(groovyActionURL.openStream()));
        } catch (IOException e) {
            logger.error("Failed to load the groovy action {}", groovyActionURL.getPath(), e);
        }
    }

    private void removeGroovyActions(BundleContext bundleContext, URL groovyActionURL) {
        GroovyBundleResourceConnector bundleResourceConnector = new GroovyBundleResourceConnector(bundleContext);
        GroovyScriptEngine engine = new GroovyScriptEngine(bundleResourceConnector,
                bundleContext.getBundle().adapt(BundleWiring.class).getClassLoader());
        try {
            Class classScript = engine.getGroovyClassLoader().parseClass(IOUtils.toString(groovyActionURL.openStream()));
            groovyActionsService.remove(classScript.getName());
            logger.info("The script {} has been removed.", classScript);
        } catch (IOException e) {
            logger.error("Failed to parse groovy action file", e);
        }
    }

    private void loadGroovyActions(BundleContext bundleContext) {
        Enumeration<URL> bundleGroovyActions = bundleContext.getBundle().findEntries(ENTRIES_LOCATION, "*.groovy", true);
        if (bundleGroovyActions == null) {
            return;
        }
        while (bundleGroovyActions.hasMoreElements()) {
            URL groovyActionURL = bundleGroovyActions.nextElement();
            logger.debug("Found Groovy action at {}, loading... ", groovyActionURL.getPath());
            addGroovyAction(groovyActionURL);
        }
    }

    private void unloadGroovyActions(BundleContext bundleContext) {
        Enumeration<URL> bundleGroovyActions = bundleContext.getBundle().findEntries(ENTRIES_LOCATION, "*.groovy", true);
        if (bundleGroovyActions == null) {
            return;
        }

        while (bundleGroovyActions.hasMoreElements()) {
            URL groovyActionURL = bundleGroovyActions.nextElement();
            logger.debug("Found Groovy action at {}, loading... ", groovyActionURL.getPath());
            removeGroovyActions(bundleContext, groovyActionURL);
        }
    }
}
