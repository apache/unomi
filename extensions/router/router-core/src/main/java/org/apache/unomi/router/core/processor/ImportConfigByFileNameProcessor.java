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
 * Created by amidani on 22/05/2017.
 */
public class ImportConfigByFileNameProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(ImportConfigByFileNameProcessor.class.getName());

    private ImportExportConfigurationService<ImportConfiguration> importConfigurationService;

    @Override
    public void process(Exchange exchange) throws Exception {

        String fileName = exchange.getIn().getBody(GenericFile.class).getFileName();
        String importConfigId = fileName.substring(0, fileName.indexOf('.'));
        ImportConfiguration importConfiguration = importConfigurationService.load(importConfigId);
        if(importConfiguration != null) {
            logger.debug("Set a header with import configuration found for ID : {}", importConfigId);
            exchange.getIn().setHeader(RouterConstants.HEADER_IMPORT_CONFIG_ONESHOT, importConfiguration);
        } else {
            logger.warn("No import configuration found with ID : {}", importConfigId);
            exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
        }
    }

    public void setImportConfigurationService(ImportExportConfigurationService<ImportConfiguration> importConfigurationService) {
        this.importConfigurationService = importConfigurationService;
    }
}
