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
        String importConfigId = null;
        ExportConfiguration exportConfig = (ExportConfiguration) exchange.getIn().getHeader(RouterConstants.HEADER_EXPORT_CONFIG);

        Map execution = new HashMap();
        execution.put("date", ((Date) exchange.getProperty("CamelCreatedTimestamp")).getTime());
        execution.put("extractedProfiles", exchange.getProperty("CamelSplitSize"));

        ExportConfiguration exportConfiguration = exportConfigurationService.load(exportConfig.getItemId());

        if (exportConfiguration.getExecutions().size() >= executionsHistorySize) {
            int oldestExecIndex = 0;
            long oldestExecDate = (Long) exportConfiguration.getExecutions().get(0).get("date");
            for (int i = 1; i < exportConfiguration.getExecutions().size(); i++) {
                if ((Long) exportConfiguration.getExecutions().get(i).get("date") < oldestExecDate) {
                    oldestExecDate = (Long) exportConfiguration.getExecutions().get(i).get("date");
                    oldestExecIndex = i;
                }
            }
            exportConfiguration.getExecutions().remove(oldestExecIndex);
        }

        exportConfiguration.getExecutions().add(execution);
        exportConfigurationService.save(exportConfiguration);

        logger.info("Processing route {} completed.", exchange.getFromRouteId());
    }

    public void setExportConfigurationService(ImportExportConfigurationService<ExportConfiguration> exportConfigurationService) {
        this.exportConfigurationService = exportConfigurationService;
    }

    public void setExecutionsHistorySize(int executionsHistorySize) {
        this.executionsHistorySize = executionsHistorySize;
    }
}
