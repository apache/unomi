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
 * Created by amidani on 29/06/2017.
 */
public class ExportRouteCompletionProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(ExportRouteCompletionProcessor.class.getName());
    private ImportExportConfigurationService<ExportConfiguration> exportConfigurationService;
    private int executionsHistorySize;

    @Override
    public void process(Exchange exchange) throws Exception {
        // We load the conf from ES because we are going to increment the execution number
        ExportConfiguration exportConfiguration = exportConfigurationService.load(exchange.getFromRouteId());
        if (exportConfiguration == null) {
            logger.warn("Unable to complete export, config cannot not found: {}", exchange.getFromRouteId());
            return;
        }

        Map execution = new HashMap();
        execution.put(RouterConstants.KEY_EXECS_DATE, ((Date) exchange.getProperty("CamelCreatedTimestamp")).getTime());
        execution.put(RouterConstants.KEY_EXECS_EXTRACTED, exchange.getProperty("CamelSplitSize"));

        exportConfiguration = (ExportConfiguration) RouterUtils.addExecutionEntry(exportConfiguration, execution, executionsHistorySize);
        exportConfiguration.setStatus(RouterConstants.CONFIG_STATUS_COMPLETE_SUCCESS);

        exportConfigurationService.save(exportConfiguration, false);

        logger.info("Processing route {} completed.", exchange.getFromRouteId());
    }

    public void setExportConfigurationService(ImportExportConfigurationService<ExportConfiguration> exportConfigurationService) {
        this.exportConfigurationService = exportConfigurationService;
    }

    public void setExecutionsHistorySize(int executionsHistorySize) {
        this.executionsHistorySize = executionsHistorySize;
    }
}
