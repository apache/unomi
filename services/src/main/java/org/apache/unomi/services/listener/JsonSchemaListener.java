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
package org.apache.unomi.services.listener;

import org.apache.unomi.api.schema.UnomiJSONSchema;
import org.apache.unomi.api.services.SchemaRegistry;
import org.apache.unomi.persistence.spi.PersistenceService;
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
 * The script will be stored in the ES index jsonSchemas
 */
public class JsonSchemaListener implements SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(JsonSchemaListener.class.getName());
    public static final String ENTRIES_LOCATION = "META-INF/cxs/schemas";

    private PersistenceService persistenceService;

    private SchemaRegistry schemaRegistry;

    private BundleContext bundleContext;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setSchemaRegistry(SchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void postConstruct() {
        logger.info("JSON schema listener initializing...");
        logger.debug("postConstruct {}", bundleContext.getBundle());
        createIndex();

        loadPredefinedSchemas(bundleContext);

        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                saveSchemas(bundle.getBundleContext());
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
        saveSchemas(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        unloadSchemas(bundleContext);
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

    public void createIndex() {
        if (persistenceService.createIndex(UnomiJSONSchema.ITEM_TYPE)) {
            logger.info("{} index created", UnomiJSONSchema.ITEM_TYPE);
        } else {
            logger.info("{} index already exists", UnomiJSONSchema.ITEM_TYPE);
        }
    }

    private void saveSchemas(BundleContext bundleContext) {
        Enumeration<URL> predefinedSchemas = bundleContext.getBundle().findEntries(ENTRIES_LOCATION, "*.json", true);
        if (predefinedSchemas == null) {
            return;
        }

        while (predefinedSchemas.hasMoreElements()) {
            URL predefinedSchemaURL = predefinedSchemas.nextElement();
            logger.debug("Found JSON schema at {}, loading... ", predefinedSchemaURL);

            try (InputStream schemaInputStream = predefinedSchemaURL.openStream()) {
                schemaRegistry.saveSchema(schemaInputStream);
            } catch (Exception e) {
                logger.error("Error while loading schema definition {}", predefinedSchemaURL, e);
            }
        }
    }

    private void loadPredefinedSchemas(BundleContext bundleContext) {
        Enumeration<URL> predefinedSchemas = bundleContext.getBundle().findEntries(ENTRIES_LOCATION, "*.json", true);
        if (predefinedSchemas == null) {
            return;
        }

        while (predefinedSchemas.hasMoreElements()) {
            URL predefinedSchemaURL = predefinedSchemas.nextElement();
            logger.debug("Found predefined JSON schema at {}, loading... ", predefinedSchemaURL);
            try (InputStream schemaInputStream = predefinedSchemaURL.openStream()) {
                schemaRegistry.loadPredefinedSchema(schemaInputStream);
            } catch (Exception e) {
                logger.error("Error while loading schema definition {}", predefinedSchemaURL, e);
            }
        }
    }

    private void unloadSchemas(BundleContext bundleContext) {
        Enumeration<URL> predefinedSchemas = bundleContext.getBundle().findEntries(ENTRIES_LOCATION, "*.json", true);
        if (predefinedSchemas == null) {
            return;
        }

        while (predefinedSchemas.hasMoreElements()) {
            URL predefinedSchemaURL = predefinedSchemas.nextElement();
            logger.debug("Found predefined JSON schema at {}, loading... ", predefinedSchemaURL);

            try (InputStream schemaInputStream = predefinedSchemaURL.openStream()) {
                schemaRegistry.deleteSchema(schemaInputStream);
            } catch (Exception e) {
                logger.error("Error while removing schema at {}", predefinedSchemaURL, e);
            }
        }
    }
}
