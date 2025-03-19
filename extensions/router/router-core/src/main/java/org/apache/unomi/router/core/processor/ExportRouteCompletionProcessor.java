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
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.RouterUtils;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A Camel processor that handles the completion of profile export routes.
 * This processor updates the export configuration with execution statistics
 * and manages the execution history of export operations.
 *
 * <p>The processor performs the following operations:
 * <ul>
 *   <li>Records export execution statistics</li>
 *   <li>Updates the export configuration status</li>
 *   <li>Maintains execution history within configured size limits</li>
 *   <li>Persists updated configuration information</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class ExportRouteCompletionProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportRouteCompletionProcessor.class.getName());
    
    /** Service for managing export configurations */
    private ImportExportConfigurationService<ExportConfiguration> exportConfigurationService;
    
    /** Maximum number of execution history entries to maintain */
    private int executionsHistorySize;

    /**
     * Processes the completion of an export route by updating its configuration and statistics.
     * 
     * <p>This method:
     * <ul>
     *   <li>Loads the current export configuration</li>
     *   <li>Creates an execution entry with timestamp and statistics</li>
     *   <li>Updates the configuration with execution results</li>
     *   <li>Maintains the execution history size limit</li>
     *   <li>Updates the export status to complete</li>
     * </ul>
     * </p>
     *
     * @param exchange the Camel exchange containing export execution details
     * @throws Exception if an error occurs during processing
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        // We load the conf from ES because we are going to increment the execution number
        ExportConfiguration exportConfiguration = exportConfigurationService.load(exchange.getFromRouteId());
        if (exportConfiguration == null) {
            LOGGER.warn("Unable to complete export, config cannot not found: {}", exchange.getFromRouteId());
            return;
        }

        Map execution = new HashMap();
        execution.put(RouterConstants.KEY_EXECS_DATE, ((Date) exchange.getProperty("CamelCreatedTimestamp")).getTime());
        execution.put(RouterConstants.KEY_EXECS_EXTRACTED, exchange.getProperty("CamelSplitSize"));

        exportConfiguration = (ExportConfiguration) RouterUtils.addExecutionEntry(exportConfiguration, execution, executionsHistorySize);
        exportConfiguration.setStatus(RouterConstants.CONFIG_STATUS_COMPLETE_SUCCESS);

        exportConfigurationService.save(exportConfiguration, false);

        LOGGER.info("Processing route {} completed.", exchange.getFromRouteId());
    }

    /**
     * Sets the service used for managing export configurations.
     *
     * @param exportConfigurationService the service for handling export configurations
     */
    public void setExportConfigurationService(ImportExportConfigurationService<ExportConfiguration> exportConfigurationService) {
        this.exportConfigurationService = exportConfigurationService;
    }

    /**
     * Sets the maximum size of the execution history to maintain.
     *
     * @param executionsHistorySize the maximum number of execution entries to keep
     */
    public void setExecutionsHistorySize(int executionsHistorySize) {
        this.executionsHistorySize = executionsHistorySize;
    }
}
