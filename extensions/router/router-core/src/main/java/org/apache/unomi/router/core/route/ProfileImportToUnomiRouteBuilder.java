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
import org.apache.camel.model.RouteDefinition;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.core.processor.ImportRouteCompletionProcessor;
import org.apache.unomi.router.core.processor.UnomiStorageProcessor;
import org.apache.unomi.router.core.strategy.ArrayListAggregationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A Camel route builder that handles the final stage of profile imports by storing
 * processed profile data into Apache Unomi's storage system.
 * 
 * <p>Features:
 * <ul>
 *   <li>Final processing of imported profiles</li>
 *   <li>Integration with Unomi's storage system</li>
 *   <li>Support for both Kafka and direct endpoints</li>
 *   <li>Import completion handling</li>
 *   <li>Error handling and reporting</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class ProfileImportToUnomiRouteBuilder extends RouterAbstractRouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileImportToUnomiRouteBuilder.class.getName());

    /** Processor for storing profiles in Unomi */
    private UnomiStorageProcessor unomiStorageProcessor;
    
    /** Processor for handling import completion */
    private ImportRouteCompletionProcessor importRouteCompletionProcessor;

    /**
     * Constructs a new route builder with Kafka configuration.
     *
     * @param kafkaProps map containing Kafka configuration properties
     * @param configType the type of configuration (kafka/direct)
     */
    public ProfileImportToUnomiRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    /**
     * Configures the route for storing imported profiles in Unomi.
     * Creates a route that processes incoming profile data and stores it in Unomi's storage system.
     * 
     * <p>The route:
     * <ul>
     *   <li>Receives processed profile data</li>
     *   <li>Stores profiles in Unomi's storage system</li>
     *   <li>Handles import completion</li>
     *   <li>Manages error reporting</li>
     * </ul>
     * </p>
     *
     * @throws Exception if an error occurs during route configuration
     */
    @Override
    public void configure() throws Exception {

        LOGGER.info("Configure Recurrent Route 'To Target'");

        RouteDefinition rtDef;
        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
            rtDef = from((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_TO, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        } else {
            rtDef = from((String) getEndpointURI(RouterConstants.DIRECTION_TO, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        }
        rtDef.choice()
                .when(header(RouterConstants.HEADER_FAILED_MESSAGE).isNull())
                .unmarshal(jacksonDataFormat)
                .process(unomiStorageProcessor)
                .otherwise()
                .log(LoggingLevel.WARN, "Failed message, skip processing!")
                .end()
                .aggregate(constant(true), new ArrayListAggregationStrategy())
                .completionPredicate(exchangeProperty("CamelSplitComplete").isEqualTo("true"))
                .eagerCheckCompletion()
                .process(importRouteCompletionProcessor)
                .to("log:org.apache.unomi.router?level=DEBUG");
    }

    /**
     * Sets the processor for storing profiles in Unomi.
     *
     * @param unomiStorageProcessor processor for Unomi storage operations
     */
    public void setUnomiStorageProcessor(UnomiStorageProcessor unomiStorageProcessor) {
        this.unomiStorageProcessor = unomiStorageProcessor;
    }

    /**
     * Sets the processor for handling import completion.
     *
     * @param importRouteCompletionProcessor processor for import completion operations
     */
    public void setImportRouteCompletionProcessor(ImportRouteCompletionProcessor importRouteCompletionProcessor) {
        this.importRouteCompletionProcessor = importRouteCompletionProcessor;
    }
}
