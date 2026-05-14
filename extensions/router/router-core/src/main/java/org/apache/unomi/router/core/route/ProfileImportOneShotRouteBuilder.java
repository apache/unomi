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

import org.apache.camel.LoggingLevel;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.exceptions.BadProfileDataFormatException;
import org.apache.unomi.router.core.processor.ImportConfigByFileNameProcessor;
import org.apache.unomi.router.core.processor.LineSplitFailureHandler;
import org.apache.unomi.router.core.processor.LineSplitProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A Camel route builder that handles one-time profile imports from files.
 * This route builder creates routes that process CSV files dropped into a
 * monitored directory for one-time import operations.
 *
 * <p>Features:
 * <ul>
 *   <li>File-based import processing</li>
 *   <li>Configuration lookup from filename</li>
 *   <li>CSV file processing with error handling</li>
 *   <li>Support for both Kafka and direct endpoints</li>
 *   <li>Automatic file movement after processing</li>
 *   <li>Error reporting and failed file handling</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class ProfileImportOneShotRouteBuilder extends RouterAbstractRouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileImportOneShotRouteBuilder.class.getName());

    /** Processor for extracting import configuration from filenames */
    private ImportConfigByFileNameProcessor importConfigByFileNameProcessor;

    /** Directory to monitor for import files */
    private String uploadDir;

    /**
     * Constructs a new route builder with Kafka configuration.
     *
     * @param kafkaProps map containing Kafka configuration properties
     * @param configType the type of configuration (kafka/direct)
     */
    public ProfileImportOneShotRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    /**
     * Configures the route for one-shot profile imports.
     * Creates a route that monitors a directory for CSV files and processes them for import.
     *
     * <p>The route:
     * <ul>
     *   <li>Monitors upload directory for CSV files</li>
     *   <li>Extracts configuration from filename</li>
     *   <li>Processes file contents line by line</li>
     *   <li>Handles validation and format errors</li>
     *   <li>Routes processed data to appropriate endpoints</li>
     * </ul>
     * </p>
     *
     * @throws Exception if an error occurs during route configuration
     */
    @Override
    public void configure() throws Exception {

        LOGGER.info("Configure OneShot Route...");

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

        LineSplitProcessor lineSplitProcessor = new LineSplitProcessor();
        lineSplitProcessor.setProfilePropertyTypes(profileService.getTargetPropertyTypes("profiles"));

        ProcessorDefinition prDef = from("file://" + uploadDir + "?recursive=true&moveFailed=.error&include=.*.csv")
                .routeId(RouterConstants.IMPORT_ONESHOT_ROUTE_ID)
                .autoStartup(true)
                .process(importConfigByFileNameProcessor)
                .split(bodyAs(String.class).tokenize("${in.header.importConfigOneShot.getLineSeparator}"))
                .setHeader(RouterConstants.HEADER_CONFIG_TYPE, constant(configType))
                .process(lineSplitProcessor)
                .to("log:org.apache.unomi.router?level=DEBUG")
                .marshal(jacksonDataFormat)
                .convertBodyTo(String.class);
        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
            prDef.to((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        } else {
            prDef.to((String) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        }
    }

    /**
     * Sets the processor for handling import configuration by filename.
     *
     * @param importConfigByFileNameProcessor processor for filename-based configuration
     */
    public void setImportConfigByFileNameProcessor(ImportConfigByFileNameProcessor importConfigByFileNameProcessor) {
        this.importConfigByFileNameProcessor = importConfigByFileNameProcessor;
    }

    /**
     * Sets the directory to monitor for import files.
     *
     * @param uploadDir path to the directory to monitor
     */
    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
}
