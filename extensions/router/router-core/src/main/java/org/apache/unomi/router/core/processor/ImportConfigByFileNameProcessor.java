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
package org.apache.unomi.router.core.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.api.RouterConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel processor that retrieves import configurations based on file names.
 * This processor extracts the configuration ID from the filename and loads
 * the corresponding import configuration for processing.
 *
 * <p>The processor expects filenames in the format:
 * <pre>configurationId.extension</pre>
 * where the configurationId matches an existing import configuration.</p>
 *
 * <p>Features:
 * <ul>
 *   <li>Extracts configuration ID from filename</li>
 *   <li>Loads corresponding import configuration</li>
 *   <li>Sets configuration in exchange header for processing</li>
 *   <li>Handles missing configurations gracefully</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class ImportConfigByFileNameProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportConfigByFileNameProcessor.class.getName());

    /** Service for managing import configurations */
    private ImportExportConfigurationService<ImportConfiguration> importConfigurationService;

    /**
     * Processes the exchange by loading an import configuration based on the filename.
     * 
     * <p>This method:
     * <ul>
     *   <li>Extracts the filename from the exchange body</li>
     *   <li>Parses the configuration ID from the filename</li>
     *   <li>Attempts to load the corresponding import configuration</li>
     *   <li>Sets the configuration in the exchange header if found</li>
     *   <li>Stops route processing if no configuration is found</li>
     * </ul>
     * </p>
     *
     * @param exchange the Camel exchange containing the file to process
     * @throws Exception if an error occurs during processing
     */
    @Override
    public void process(Exchange exchange) throws Exception {

        String fileName = exchange.getIn().getBody(GenericFile.class).getFileName();
        String importConfigId = fileName.substring(0, fileName.indexOf('.'));
        ImportConfiguration importConfiguration = importConfigurationService.load(importConfigId);
        if(importConfiguration != null) {
            LOGGER.debug("Set a header with import configuration found for ID : {}", importConfigId);
            exchange.getIn().setHeader(RouterConstants.HEADER_IMPORT_CONFIG_ONESHOT, importConfiguration);
        } else {
            LOGGER.warn("No import configuration found with ID : {}", importConfigId);
            exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
        }
    }

    /**
     * Sets the service used for managing import configurations.
     *
     * @param importConfigurationService the service for handling import configurations
     */
    public void setImportConfigurationService(ImportExportConfigurationService<ImportConfiguration> importConfigurationService) {
        this.importConfigurationService = importConfigurationService;
    }
}
