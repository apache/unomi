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

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
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
@Component(service = SynchronousBundleListener.class)
public class GroovyActionListener implements SynchronousBundleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroovyActionListener.class.getName());
    public static final String ENTRIES_LOCATION = "META-INF/cxs/actions";

    private GroovyActionsService groovyActionsService;
    private BundleContext bundleContext;
    private ExecutionContextManager contextManager;

    @Reference
    public void setGroovyActionsService(GroovyActionsService groovyActionsService) {
        this.groovyActionsService = groovyActionsService;
    }

    @Reference
    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    @Activate
    public void postConstruct(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        LOGGER.debug("postConstruct {}", bundleContext.getBundle());
        loadGroovyActions(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                loadGroovyActions(bundle.getBundleContext());
            }
        }

        bundleContext.addBundleListener(this);
        LOGGER.info("Groovy Action Dispatcher initialized.");
    }

    @Deactivate
    public void preDestroy() {
        processBundleStop(bundleContext);
        bundleContext.removeBundleListener(this);
        LOGGER.info("Groovy Action Dispatcher shutdown.");
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
        contextManager.executeAsSystem(() -> {
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
            return null;
        });
    }

    private void addGroovyAction(URL groovyActionURL) {
        try {
            groovyActionsService.save(FilenameUtils.getName(groovyActionURL.getPath()).replace(".groovy", ""),
                    IOUtils.toString(groovyActionURL.openStream()));
        } catch (IOException e) {
            LOGGER.error("Failed to load the groovy action {}", groovyActionURL.getPath(), e);
        }
    }

    private void removeGroovyAction(URL groovyActionURL) {
        String actionName = FilenameUtils.getName(groovyActionURL.getPath()).replace(".groovy", "");
        groovyActionsService.remove(actionName);
        LOGGER.info("The script {} has been removed.", actionName);
    }

    private void loadGroovyActions(BundleContext bundleContext) {
        Enumeration<URL> bundleGroovyActions = bundleContext.getBundle().findEntries(ENTRIES_LOCATION, "*.groovy", true);
        if (bundleGroovyActions == null) {
            return;
        }
        while (bundleGroovyActions.hasMoreElements()) {
            URL groovyActionURL = bundleGroovyActions.nextElement();
            LOGGER.debug("Found Groovy action at {}, loading... ", groovyActionURL.getPath());
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
            LOGGER.debug("Found Groovy action at {}, loading... ", groovyActionURL.getPath());
            removeGroovyAction(groovyActionURL);
        }
    }
}
