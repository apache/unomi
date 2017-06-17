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

package org.apache.unomi.router.services;

import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.services.ImportConfigurationService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Created by amidani on 28/04/2017.
 */
public class ImportConfigurationServiceImpl implements ImportConfigurationService,SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(ImportConfigurationServiceImpl.class.getName());

    private BundleContext bundleContext;
    private PersistenceService persistenceService;

    public ImportConfigurationServiceImpl() {
        logger.info("Initializing import configuration service...");
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        processBundleStartup(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                processBundleStartup(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
        logger.info("Import configuration service initialized.");
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("Import configuration service shutdown.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
    }

    private void processBundleStop(BundleContext bundleContext) {
    }


    @Override
    public List<ImportConfiguration> getImportConfigurations() {
        return persistenceService.getAllItems(ImportConfiguration.class);
    }

    @Override
    public ImportConfiguration load(String configId) {
        return persistenceService.load(configId, ImportConfiguration.class);
    }

    @Override
    public ImportConfiguration save(ImportConfiguration importConfiguration) {
        if (importConfiguration.getItemId() == null) {
            importConfiguration.setItemId(UUID.randomUUID().toString());
        }
        persistenceService.save(importConfiguration);
        return persistenceService.load(importConfiguration.getItemId(), ImportConfiguration.class);
    }

    @Override
    public void delete(String configId) {
        persistenceService.remove(configId, ImportConfiguration.class);
    }

    @Override
    public void bundleChanged(BundleEvent bundleEvent) {

    }
}
