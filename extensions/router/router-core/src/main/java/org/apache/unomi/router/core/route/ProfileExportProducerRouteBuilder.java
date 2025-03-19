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

import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ProfileExportService;
import org.apache.unomi.router.core.processor.ExportRouteCompletionProcessor;
import org.apache.unomi.router.core.processor.LineBuildProcessor;
import org.apache.unomi.router.core.strategy.StringLinesAggregationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A Camel route builder that handles the production of export data from collected profiles.
 * This route builder creates routes that process collected profiles and formats them
 * for export to the configured destination.
 *
 * <p>Features:
 * <ul>
 *   <li>Profile data transformation to export format</li>
 *   <li>Line-by-line processing with aggregation</li>
 *   <li>Support for multiple export destinations</li>
 *   <li>Completion handling and status updates</li>
 *   <li>Support for both Kafka and direct endpoints</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class ProfileExportProducerRouteBuilder extends RouterAbstractRouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileExportProducerRouteBuilder.class);

    /** Processor for handling export completion */
    private ExportRouteCompletionProcessor exportRouteCompletionProcessor;

    /** Service for profile export operations */
    private ProfileExportService profileExportService;

    /**
     * Constructs a new route builder with Kafka configuration.
     *
     * @param kafkaProps map containing Kafka configuration properties
     * @param configType the type of configuration (kafka/direct)
     */
    public ProfileExportProducerRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    /**
     * Sets the profile export service.
     *
     * @param profileExportService service for handling profile exports
     */
    public void setProfileExportService(ProfileExportService profileExportService) {
        this.profileExportService = profileExportService;
    }

    /**
     * Configures the routes for producing export data.
     * Creates a route that processes collected profiles and prepares them for export.
     * 
     * <p>The route:
     * <ul>
     *   <li>Unmarshals incoming profile data</li>
     *   <li>Processes profiles into export format</li>
     *   <li>Aggregates lines for batch processing</li>
     *   <li>Handles export completion</li>
     *   <li>Routes data to configured destinations</li>
     * </ul>
     * </p>
     *
     * @throws Exception if an error occurs during route configuration
     */
    @Override
    public void configure() throws Exception {

        LOGGER.info("Configure Recurrent Route 'Export :: Data Producer'");

        RouteDefinition rtDef;
        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
            rtDef = from((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_TO, RouterConstants.DIRECT_EXPORT_DEPOSIT_BUFFER));
        } else {
            rtDef = from((String) getEndpointURI(RouterConstants.DIRECTION_TO, RouterConstants.DIRECT_EXPORT_DEPOSIT_BUFFER));
        }

        rtDef.unmarshal(jacksonDataFormat) // TODO: UNOMI-759 avoid unnecessary marshalling
                .process(new LineBuildProcessor(profileExportService))
                .aggregate(constant(true), new StringLinesAggregationStrategy())
                .completionPredicate(exchangeProperty("CamelSplitSize").isEqualTo(exchangeProperty("CamelAggregatedSize")))
                .eagerCheckCompletion()
                .process(exportRouteCompletionProcessor)
                .toD("${in.header.exportConfig.getProperty('destination')}");

    }

    /**
     * Sets the processor for handling export completion.
     *
     * @param exportRouteCompletionProcessor processor for export completion handling
     */
    public void setExportRouteCompletionProcessor(ExportRouteCompletionProcessor exportRouteCompletionProcessor) {
        this.exportRouteCompletionProcessor = exportRouteCompletionProcessor;
    }

}
