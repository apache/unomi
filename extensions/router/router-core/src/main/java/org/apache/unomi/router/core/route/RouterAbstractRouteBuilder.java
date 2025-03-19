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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.router.api.RouterConstants;

import java.util.Map;

/**
 * Abstract base class for all Unomi router route builders.
 * This class provides common functionality and configuration for both import
 * and export routes, supporting both Kafka and direct endpoint configurations.
 *
 * <p>Features:
 * <ul>
 *   <li>Common Kafka configuration handling</li>
 *   <li>Endpoint URI generation for both Kafka and direct modes</li>
 *   <li>Shared configuration for JSON data format</li>
 *   <li>Profile service integration</li>
 *   <li>Endpoint security through allowlist</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public abstract class RouterAbstractRouteBuilder extends RouteBuilder {

    /** JSON data format configuration */
    protected JacksonDataFormat jacksonDataFormat;

    /** Kafka broker host */
    protected String kafkaHost;
    
    /** Kafka broker port */
    protected String kafkaPort;
    
    /** Topic for import operations */
    protected String kafkaImportTopic;
    
    /** Topic for export operations */
    protected String kafkaExportTopic;
    
    /** Consumer group ID for import operations */
    protected String kafkaImportGroupId;
    
    /** Consumer group ID for export operations */
    protected String kafkaExportGroupId;
    
    /** Number of Kafka consumers */
    protected String kafkaConsumerCount;
    
    /** Auto-commit configuration for Kafka */
    protected String kafkaAutoCommit;

    /** Configuration type (kafka/direct) */
    protected String configType;
    
    /** List of allowed endpoint schemes */
    protected String allowedEndpoints;

    /** Service for profile operations */
    protected ProfileService profileService;

    /**
     * Constructs a new route builder with Kafka configuration.
     *
     * @param kafkaProps map containing Kafka configuration properties
     * @param configType the type of configuration (kafka/direct)
     */
    public RouterAbstractRouteBuilder(Map<String, String> kafkaProps, String configType) {
        this.kafkaHost = kafkaProps.get("kafkaHost");
        this.kafkaPort = kafkaProps.get("kafkaPort");
        this.kafkaImportTopic = kafkaProps.get("kafkaImportTopic");
        this.kafkaExportTopic = kafkaProps.get("kafkaExportTopic");
        this.kafkaImportGroupId = kafkaProps.get("kafkaImportGroupId");
        this.kafkaExportGroupId = kafkaProps.get("kafkaExportGroupId");
        this.kafkaConsumerCount = kafkaProps.get("kafkaConsumerCount");
        this.kafkaAutoCommit = kafkaProps.get("kafkaAutoCommit");
        this.configType = configType;
    }

    /**
     * Gets the appropriate endpoint URI based on configuration type and operation.
     * 
     * <p>This method:
     * <ul>
     *   <li>Creates Kafka endpoints with appropriate configuration when using Kafka</li>
     *   <li>Returns direct endpoint URIs when not using Kafka</li>
     *   <li>Configures consumer properties for incoming endpoints</li>
     * </ul>
     * </p>
     *
     * @param direction the direction of the endpoint (to/from)
     * @param operationDepositBuffer the operation buffer identifier
     * @return Object either a KafkaEndpoint or String depending on configuration
     */
    public Object getEndpointURI(String direction, String operationDepositBuffer) {
        Object endpoint;
        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
            String kafkaTopic = kafkaImportTopic;
            String kafkaGroupId = kafkaImportGroupId;
            if (RouterConstants.DIRECT_EXPORT_DEPOSIT_BUFFER.equals(operationDepositBuffer)) {
                kafkaTopic = kafkaExportTopic;
                kafkaGroupId = kafkaExportGroupId;
            }
            //Prepare Kafka Deposit
            StringBuilder kafkaUri = new StringBuilder("kafka:");
            kafkaUri.append(kafkaHost).append(":").append(kafkaPort).append("?topic=").append(kafkaTopic);
            if (StringUtils.isNotBlank(kafkaGroupId)) {
                kafkaUri.append("&groupId=" + kafkaGroupId);
            }
            if (RouterConstants.DIRECTION_TO.equals(direction)) {
                kafkaUri.append("&autoCommitEnable=" + kafkaAutoCommit + "&consumersCount=" + kafkaConsumerCount);
            }
            KafkaConfiguration kafkaConfiguration = new KafkaConfiguration();
            kafkaConfiguration.setBrokers(kafkaHost + ":" + kafkaPort);
            kafkaConfiguration.setTopic(kafkaTopic);
            kafkaConfiguration.setGroupId(kafkaGroupId);
            endpoint = new KafkaEndpoint(kafkaUri.toString(), new KafkaComponent(this.getContext()));
            ((KafkaEndpoint) endpoint).setConfiguration(kafkaConfiguration);
        } else {
            endpoint = operationDepositBuffer;
        }

        return endpoint;
    }

    /**
     * Sets the JSON data format configuration.
     *
     * @param jacksonDataFormat the JSON data format to use
     */
    public void setJacksonDataFormat(JacksonDataFormat jacksonDataFormat) {
        this.jacksonDataFormat = jacksonDataFormat;
    }

    /**
     * Sets the list of allowed endpoint schemes.
     *
     * @param allowedEndpoints comma-separated list of allowed endpoint schemes
     */
    public void setAllowedEndpoints(String allowedEndpoints) {
        this.allowedEndpoints = allowedEndpoints;
    }

    /**
     * Sets the profile service.
     *
     * @param profileService the service for profile operations
     */
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

}
