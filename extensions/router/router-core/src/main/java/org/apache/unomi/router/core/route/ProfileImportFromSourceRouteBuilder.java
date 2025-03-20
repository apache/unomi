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
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.api.exceptions.BadProfileDataFormatException;
import org.apache.unomi.router.core.processor.LineSplitFailureHandler;
import org.apache.unomi.router.core.processor.LineSplitProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A Camel route builder that handles the import of profiles from configured sources.
 * This route builder creates routes that process incoming profile data from various
 * sources and prepares it for import into Unomi.
 *
 * <p>Features:
 * <ul>
 *   <li>Support for multiple import configurations</li>
 *   <li>Line-by-line processing of import data</li>
 *   <li>Error handling and failure reporting</li>
 *   <li>Configuration validation and status updates</li>
 *   <li>Support for both Kafka and direct endpoints</li>
 *   <li>Graceful shutdown handling</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class ProfileImportFromSourceRouteBuilder extends RouterAbstractRouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileImportFromSourceRouteBuilder.class.getName());

    /** List of import configurations to process */
    private List<ImportConfiguration> importConfigurationList;

    /** Service for managing import configurations */
    private ImportExportConfigurationService<ImportConfiguration> importConfigurationService;

    private ExecutionContextManager executionContextManager;

    private SecurityService securityService;

    /**
     * Constructs a new route builder with Kafka configuration.
     *
     * @param kafkaProps map containing Kafka configuration properties
     * @param configType the type of configuration (kafka/direct)
     */
    public ProfileImportFromSourceRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    /**
     * Configures the routes for importing profiles from sources.
     * Creates routes for each import configuration and sets up error handling.
     *
     * <p>The routes:
     * <ul>
     *   <li>Handle data validation and format errors</li>
     *   <li>Process data line by line</li>
     *   <li>Update import status and progress</li>
     *   <li>Route processed data to appropriate endpoints</li>
     *   <li>Manage graceful completion of imports</li>
     * </ul>
     * </p>
     *
     * @throws Exception if an error occurs during route configuration
     */
    @Override
    public void configure() throws Exception {

        LOGGER.info("Configure Recurrent Route 'From Source'");

        if (importConfigurationList == null) {
            importConfigurationList = importConfigurationService.getAll();
        }

        ProcessorDefinition prDefErr = onException(BadProfileDataFormatException.class)
                .log(LoggingLevel.ERROR, "Error processing record ${exchangeProperty.CamelSplitIndex}++ !")
                .handled(true)
                .process(new LineSplitFailureHandler())
                .onException(Exception.class)
                .log(LoggingLevel.ERROR, "Failed to process file.")
                .handled(true);

        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
            prDefErr.to((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        } else {
            prDefErr.to((String) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        }

        //Loop on multiple import configuration
        for (final ImportConfiguration importConfiguration : importConfigurationList) {
            if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(importConfiguration.getConfigType()) &&
                    importConfiguration.getProperties() != null && importConfiguration.getProperties().size() > 0) {
                //Prepare Split Processor
                LineSplitProcessor lineSplitProcessor = new LineSplitProcessor();
                lineSplitProcessor.setFieldsMapping((Map<String, Integer>) importConfiguration.getProperties().get("mapping"));
                lineSplitProcessor.setOverwriteExistingProfiles(importConfiguration.isOverwriteExistingProfiles());
                lineSplitProcessor.setPropertiesToOverwrite(importConfiguration.getPropertiesToOverwrite());
                lineSplitProcessor.setMergingProperty(importConfiguration.getMergingProperty());
                lineSplitProcessor.setColumnSeparator(importConfiguration.getColumnSeparator());
                lineSplitProcessor.setHasHeader(importConfiguration.isHasHeader());
                lineSplitProcessor.setHasDeleteColumn(importConfiguration.isHasDeleteColumn());
                lineSplitProcessor.setMultiValueDelimiter(importConfiguration.getMultiValueDelimiter());
                lineSplitProcessor.setMultiValueSeparator(importConfiguration.getMultiValueSeparator());
                lineSplitProcessor.setProfilePropertyTypes(profileService.getTargetPropertyTypes("profiles"));

                String endpoint = (String) importConfiguration.getProperties().get("source");
                endpoint += "&moveFailed=.error";

                if (StringUtils.isNotBlank(endpoint) && allowedEndpoints.contains(endpoint.substring(0, endpoint.indexOf(':')))) {
                    ProcessorDefinition prDef = from(endpoint)
                            .routeId(importConfiguration.getItemId())// This allow identification of the route for manual start/stop
                            .autoStartup(importConfiguration.isActive())// Auto-start if the import configuration is set active
                            .shutdownRunningTask(ShutdownRunningTask.CompleteAllTasks)
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
                                    securityService.setCurrentSubject(securityService.createSubject(importConfiguration.getTenantId(), true));
                                    executionContextManager.executeAsTenant(importConfiguration.getTenantId(), () -> {
                                        importConfigurationService.save(importConfiguration, false);
                                        return null;
                                    });
                                }
                            })
                            .split(bodyAs(String.class).tokenize(importConfiguration.getLineSeparator()))
                            .log(LoggingLevel.DEBUG, "Splitted into ${exchangeProperty.CamelSplitSize} records")
                            .setHeader(RouterConstants.HEADER_CONFIG_TYPE, constant(configType))
                            .setHeader(RouterConstants.HEADER_TENANT_ID, constant(importConfiguration.getTenantId()))
                            .process(lineSplitProcessor)
                            .log(LoggingLevel.DEBUG, "Split IDX ${exchangeProperty.CamelSplitIndex} record")
                            .marshal(jacksonDataFormat)
                            .convertBodyTo(String.class);

                    if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
                        prDef.to((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
                    } else {
                        prDef.to((String) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
                    }
                } else {
                    LOGGER.error("Endpoint scheme {} is not allowed, route {} will be skipped.", endpoint.substring(0, endpoint.indexOf(':')), importConfiguration.getItemId());
                }
            }
        }
    }

    /**
     * Sets the list of import configurations to process.
     *
     * @param importConfigurationList list of import configurations
     */
    public void setImportConfigurationList(List<ImportConfiguration> importConfigurationList) {
        this.importConfigurationList = importConfigurationList;
    }

    /**
     * Sets the service for managing import configurations.
     *
     * @param importConfigurationService service for handling import configurations
     */
    public void setImportConfigurationService(ImportExportConfigurationService<ImportConfiguration> importConfigurationService) {
        this.importConfigurationService = importConfigurationService;
    }

    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

}
