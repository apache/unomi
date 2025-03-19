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
import org.apache.unomi.router.api.*;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A Camel processor that handles the completion of profile import routes.
 * This processor manages the final stage of import operations, collecting statistics,
 * handling errors, and updating the import configuration with execution results.
 *
 * <p>The processor performs the following operations:
 * <ul>
 *   <li>Collects import statistics (success, failure, ignore counts)</li>
 *   <li>Manages error reporting with configurable limits</li>
 *   <li>Updates import configuration status</li>
 *   <li>Maintains execution history</li>
 *   <li>Handles both one-shot and recurring imports</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class ImportRouteCompletionProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportRouteCompletionProcessor.class.getName());
    
    /** Service for managing import configurations */
    private ImportExportConfigurationService<ImportConfiguration> importConfigurationService;
    
    /** Maximum number of execution history entries to maintain */
    private int executionsHistorySize;
    
    /** Maximum number of errors to report per execution */
    private int execErrReportSize;

    /**
     * Processes the completion of an import route by collecting statistics and updating configuration.
     * 
     * <p>This method:
     * <ul>
     *   <li>Identifies the import configuration (one-shot or recurring)</li>
     *   <li>Counts successful, failed, and ignored imports</li>
     *   <li>Collects error information up to the configured limit</li>
     *   <li>Updates the import configuration with execution results</li>
     *   <li>Sets the final status based on success/failure counts</li>
     * </ul>
     * </p>
     *
     * @param exchange the Camel exchange containing import results
     * @throws Exception if an error occurs during processing
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        String importConfigId = null;
        ImportConfiguration importConfigOneShot = (ImportConfiguration) exchange.getIn().getHeader(RouterConstants.HEADER_IMPORT_CONFIG_ONESHOT);
        if (importConfigOneShot != null) {
            importConfigId = importConfigOneShot.getItemId();
        } else {
            importConfigId = exchange.getFromRouteId();
        }
        ImportConfiguration importConfiguration = importConfigurationService.load(importConfigId);
        long successCount = 0;
        long failureCount = 0;
        long ignoreCount = 0;
        List<ImportLineError> errors = new ArrayList<ImportLineError>();

        for (Object line : exchange.getIn().getBody(ArrayList.class)) {
            if (line instanceof ProfileToImport) {
                successCount++;
            } else if (line instanceof ImportLineError) {
                failureCount++;
                if (errors.size() < execErrReportSize) {
                    errors.add(((ImportLineError) line));
                }
            } else {
                ignoreCount++;
            }
        }

        Integer nbTotal = (Integer) exchange.getProperty("CamelSplitSize");
        if (importConfiguration.isHasHeader()) {
            nbTotal--;
        }

        Map execution = new HashMap();
        execution.put("date", ((Date) exchange.getProperty("CamelCreatedTimestamp")).getTime());
        execution.put("totalLinesNb", nbTotal);
        execution.put("successCount", successCount);
        execution.put("failureCount", failureCount);
        execution.put("errors", errors);

        importConfiguration = (ImportConfiguration) RouterUtils.addExecutionEntry(importConfiguration, execution, executionsHistorySize);

        //Set running to false, route is complete
        if (failureCount > 0 && successCount > 0) {
            importConfiguration.setStatus(RouterConstants.CONFIG_STATUS_COMPLETE_WITH_ERRORS);
        } else if (failureCount > 0 && successCount == 0) {
            importConfiguration.setStatus(RouterConstants.CONFIG_STATUS_COMPLETE_ERRORS);
        } else if (failureCount == 0 && successCount > 0) {
            importConfiguration.setStatus(RouterConstants.CONFIG_STATUS_COMPLETE_SUCCESS);
        }
        importConfigurationService.save(importConfiguration, false);
        LOGGER.info("Processing route {} completed. completion date: {}.", exchange.getFromRouteId(), new Date());
    }

    /**
     * Sets the service used for managing import configurations.
     *
     * @param importConfigurationService the service for handling import configurations
     */
    public void setImportConfigurationService(ImportExportConfigurationService<ImportConfiguration> importConfigurationService) {
        this.importConfigurationService = importConfigurationService;
    }

    /**
     * Sets the maximum size of the execution history to maintain.
     *
     * @param executionsHistorySize the maximum number of execution entries to keep
     */
    public void setExecutionsHistorySize(int executionsHistorySize) {
        this.executionsHistorySize = executionsHistorySize;
    }

    /**
     * Sets the maximum number of errors to report per execution.
     *
     * @param execErrReportSize the maximum number of errors to store per execution
     */
    public void setExecErrReportSize(int execErrReportSize) {
        this.execErrReportSize = execErrReportSize;
    }
}
