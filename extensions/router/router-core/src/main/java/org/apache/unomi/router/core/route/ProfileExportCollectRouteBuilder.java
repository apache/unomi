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
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.core.bean.CollectProfileBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * A Camel route builder that handles the collection of profiles for export.
 * This route builder creates routes that periodically collect profiles based on
 * segment criteria and prepare them for export processing.
 *
 * <p>Features:
 * <ul>
 *   <li>Timer-based profile collection</li>
 *   <li>Segment-based profile filtering</li>
 *   <li>Support for multiple export configurations</li>
 *   <li>Configurable collection intervals</li>
 *   <li>Security through endpoint allowlist</li>
 *   <li>Support for both Kafka and direct endpoints</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class ProfileExportCollectRouteBuilder extends RouterAbstractRouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileExportCollectRouteBuilder.class);

    /** List of export configurations to process */
    private List<ExportConfiguration> exportConfigurationList;

    /** Service for persisting and retrieving data */
    private PersistenceService persistenceService;

    private ExecutionContextManager executionContextManager;

    /**
     * Constructs a new route builder with Kafka configuration.
     *
     * @param kafkaProps map containing Kafka configuration properties
     * @param configType the type of configuration (kafka/direct)
     */
    public ProfileExportCollectRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    /**
     * Configures the routes for collecting profiles to export.
     * Creates a route for each export configuration that matches the criteria.
     *
     * <p>Each route:
     * <ul>
     *   <li>Runs on a configured timer schedule</li>
     *   <li>Collects profiles based on segment criteria</li>
     *   <li>Processes profiles for export</li>
     *   <li>Routes data to appropriate endpoints</li>
     * </ul>
     * </p>
     *
     * @throws Exception if an error occurs during route configuration
     */
    @Override
    public void configure() throws Exception {
        if (exportConfigurationList == null || exportConfigurationList.isEmpty()) {
            // Nothing to configure
            return;
        }

        LOGGER.info("Configure Recurrent Route 'Export :: Collect Data'");

        CollectProfileBean collectProfileBean = new CollectProfileBean();
        collectProfileBean.setPersistenceService(persistenceService);
        collectProfileBean.setExecutionContextManager(executionContextManager);

        //Loop on multiple export configuration
        for (final ExportConfiguration exportConfiguration : exportConfigurationList) {
            if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(exportConfiguration.getConfigType()) &&
                    exportConfiguration.getProperties() != null && exportConfiguration.getProperties().size() > 0) {
                if ((Map<String, String>) exportConfiguration.getProperties().get("mapping") != null) {
                    String destinationEndpoint = (String) exportConfiguration.getProperties().get("destination");
                    if (StringUtils.isNotBlank(destinationEndpoint) && allowedEndpoints.contains(destinationEndpoint.substring(0, destinationEndpoint.indexOf(':')))) {
                        String timerString = "timer://collectProfile?fixedRate=true&period=" + (String) exportConfiguration.getProperties().get("period");
                        if ((String) exportConfiguration.getProperties().get("delay") != null) {
                            timerString += "&delay=" + (String) exportConfiguration.getProperties().get("delay");
                        }
                        ProcessorDefinition prDef = from(timerString)
                                .routeId(exportConfiguration.getItemId())// This allow identification of the route for manual start/stop
                                .autoStartup(exportConfiguration.isActive())
                                .setHeader(RouterConstants.HEADER_TENANT_ID, constant(exportConfiguration.getTenantId()))
                                .bean(collectProfileBean, "extractProfileBySegment(" + exportConfiguration.getProperties().get("segment") + "," + exportConfiguration.getTenantId() + ")")
                                .split(body())
                                .marshal(jacksonDataFormat) // TODO: UNOMI-759 avoid unnecessary marshalling
                                .convertBodyTo(String.class)
                                .setHeader(RouterConstants.HEADER_EXPORT_CONFIG, constant(exportConfiguration))
                                .log(LoggingLevel.DEBUG, "BODY : ${body}");
                        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
                            prDef.to((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_EXPORT_DEPOSIT_BUFFER));
                        } else {
                            prDef.to((String) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_EXPORT_DEPOSIT_BUFFER));
                        }
                    } else {
                        LOGGER.error("Endpoint scheme {} is not allowed, route {} will be skipped.", destinationEndpoint.substring(0, destinationEndpoint.indexOf(':')), exportConfiguration.getItemId());
                    }
                } else {
                    LOGGER.warn("Mapping is null in export configuration, route {} will be skipped!", exportConfiguration.getItemId());
                }
            } else {
                LOGGER.warn("Export configuration incomplete, route {} will be skipped!", exportConfiguration.getItemId());
            }
        }
    }

    /**
     * Sets the list of export configurations to process.
     *
     * @param exportConfigurationList list of export configurations
     */
    public void setExportConfigurationList(List<ExportConfiguration> exportConfigurationList) {
        this.exportConfigurationList = exportConfigurationList;
    }

    /**
     * Sets the persistence service for data operations.
     *
     * @param persistenceService service for persisting and retrieving data
     */
    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * Sets the execution context manager for the route builder.
     *
     * @param executionContextManager the execution context manager to set
     */
    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }
}
