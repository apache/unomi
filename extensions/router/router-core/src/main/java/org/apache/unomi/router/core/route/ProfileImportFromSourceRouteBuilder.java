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
package org.apache.unomi.router.core.route;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.core.exception.BadProfileDataFormatException;
import org.apache.unomi.router.core.processor.LineSplitFailureHandler;
import org.apache.unomi.router.core.processor.LineSplitProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by amidani on 26/04/2017.
 */

public class ProfileImportFromSourceRouteBuilder extends RouterAbstractRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ProfileImportFromSourceRouteBuilder.class.getName());

    private List<ImportConfiguration> importConfigurationList;
    private ImportExportConfigurationService<ImportConfiguration> importConfigurationService;

    public ProfileImportFromSourceRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    @Override
    public void configure() throws Exception {

        logger.info("Configure Recurrent Route 'From Source'");

        if (importConfigurationList == null) {
            importConfigurationList = importConfigurationService.getAll();
        }

        ProcessorDefinition prDefErr = onException(BadProfileDataFormatException.class)
                .log(LoggingLevel.ERROR, "Error processing record ${exchangeProperty.CamelSplitIndex}++ !")
                .handled(true)
                .process(new LineSplitFailureHandler());

        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
            prDefErr.to((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        } else {
            prDefErr.to((String) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        }

        //Loop on multiple import configuration
        for (final ImportConfiguration importConfiguration : importConfigurationList) {
            if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(importConfiguration.getConfigType()) &&
                    importConfiguration.getProperties().size() > 0) {
                //Prepare Split Processor
                LineSplitProcessor lineSplitProcessor = new LineSplitProcessor();
                lineSplitProcessor.setFieldsMapping((Map<String, Integer>) importConfiguration.getProperties().get("mapping"));
                lineSplitProcessor.setOverwriteExistingProfiles(importConfiguration.isOverwriteExistingProfiles());
                lineSplitProcessor.setPropertiesToOverwrite(importConfiguration.getPropertiesToOverwrite());
                lineSplitProcessor.setMergingProperty(importConfiguration.getMergingProperty());
                lineSplitProcessor.setColumnSeparator(importConfiguration.getColumnSeparator());

                String endpoint = (String) importConfiguration.getProperties().get("source");

                if (StringUtils.isNotBlank(endpoint) && allowedEndpoints.contains(endpoint.substring(0, endpoint.indexOf(':')))) {
                    ProcessorDefinition prDef = from(endpoint)
                            .routeId(importConfiguration.getItemId())// This allow identification of the route for manual start/stop
                            .autoStartup(importConfiguration.isActive())// Auto-start if the import configuration is set active
                            .onCompletion()
                            // this route is only invoked when the original route is complete as a kind
                            // of completion callback
                            .log(LoggingLevel.DEBUG, "ROUTE [" + importConfiguration.getItemId() + "] is now complete [" + new Date().toString() + "]")
                            // must use end to denote the end of the onCompletion route
                            .end()
                            .process(new Processor() {
                                @Override
                                public void process(Exchange exchange) throws Exception {
                                    importConfiguration.setStatus(RouterConstants.CONFIG_STATUS_RUNNING);
                                    importConfigurationService.save(importConfiguration);
                                }
                            })
                            .split(bodyAs(String.class).tokenize(importConfiguration.getLineSeparator()))
                            .log(LoggingLevel.DEBUG, "Splitted into ${exchangeProperty.CamelSplitSize} records")
                            .setHeader(RouterConstants.HEADER_CONFIG_TYPE, constant(configType))
                            .process(lineSplitProcessor)
                            .log(LoggingLevel.DEBUG, "Split IDX ${exchangeProperty.CamelSplitIndex} record")
                            .to("log:org.apache.unomi.router?level=DEBUG")
                            .marshal(jacksonDataFormat)
                            .convertBodyTo(String.class);

                    if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
                        prDef.to((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
                    } else {
                        prDef.to((String) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
                    }
                } else {
                    logger.error("Endpoint scheme {} is not allowed, route {} will be skipped.", endpoint.substring(0, endpoint.indexOf(':')), importConfiguration.getItemId());
                }
            }
        }
    }

    public void setImportConfigurationList(List<ImportConfiguration> importConfigurationList) {
        this.importConfigurationList = importConfigurationList;
    }

    public void setImportConfigurationService(ImportExportConfigurationService<ImportConfiguration> importConfigurationService) {
        this.importConfigurationService = importConfigurationService;
    }

}
