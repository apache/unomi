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
package org.apache.unomi.schema.listener;

import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

/**
 * An implementation of a BundleListener for the JSON schema.
 * It will load the pre-defined schema files in the folder META-INF/cxs/schemas.
 * It will load the extension of schema in the folder META-INF/cxs/schemasextensions.
 * The scripts will be stored in the ES index jsonSchema and the extension will be stored in jsonSchemaExtension
 */
public class JsonSchemaListener implements SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(JsonSchemaListener.class.getName());
    public static final String ENTRIES_LOCATION = "META-INF/cxs/schemas";

    private PersistenceService persistenceService;
    private SchemaService schemaService;
    private BundleContext bundleContext;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void postConstruct() {
        logger.info("JSON schema listener initializing...");
        logger.debug("postConstruct {}", bundleContext.getBundle());
        createIndexes();

        loadPredefinedSchemas(bundleContext, true);

        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                loadPredefinedSchemas(bundle.getBundleContext(), true);
            }
        }

        bundleContext.addBundleListener(this);
        logger.info("JSON schema listener initialized.");
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("JSON schema listener shutdown.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedSchemas(bundleContext, true);
    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedSchemas(bundleContext, false);
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                processBundleStartup(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                if (!event.getBundle().getSymbolicName().equals(bundleContext.getBundle().getSymbolicName())) {
                    processBundleStop(event.getBundle().getBundleContext());
                }
                break;
        }
    }

    public void createIndexes() {
        if (persistenceService.createIndex(JsonSchemaWrapper.ITEM_TYPE)) {
            logger.info("{} index created", JsonSchemaWrapper.ITEM_TYPE);
        } else {
            logger.info("{} index already exists", JsonSchemaWrapper.ITEM_TYPE);
        }
    }

    private void loadPredefinedSchemas(BundleContext bundleContext, boolean load) {
        Enumeration<URL> predefinedSchemas = bundleContext.getBundle().findEntries(ENTRIES_LOCATION, "*.json", true);
        if (predefinedSchemas == null) {
            return;
        }

        while (predefinedSchemas.hasMoreElements()) {
            URL predefinedSchemaURL = predefinedSchemas.nextElement();
            logger.debug("Found predefined JSON schema at {}, {}... ", predefinedSchemaURL, load ? "loading" : "unloading");
            try (InputStream schemaInputStream = predefinedSchemaURL.openStream()) {
                if (load) {
                    schemaService.loadPredefinedSchema(schemaInputStream);
                } else {
                    schemaService.unloadPredefinedSchema(schemaInputStream);
                }
            } catch (Exception e) {
                logger.error("Error while {} schema definition {}", load ? "loading" : "unloading", predefinedSchemaURL, e);
            }
        }
    }
}
