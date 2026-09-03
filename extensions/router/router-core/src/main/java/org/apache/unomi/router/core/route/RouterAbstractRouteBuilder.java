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
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.router.api.ImportExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Abstract base class for all Unomi router route builders.
 * This class provides common functionality and configuration for both import
 * and export routes, supporting Kafka ({@link RouterConstants#CONFIG_TYPE_KAFKA}) and in-process
 * {@code direct:} buffer endpoints when configured as {@link RouterConstants#CONFIG_TYPE_NOBROKER}.
 *
 * <p>Features:
 * <ul>
 *   <li>Common Kafka configuration handling</li>
 *   <li>Endpoint URI generation for Kafka topics or in-vm {@code direct:} buffers</li>
 *   <li>Shared configuration for JSON data format</li>
 *   <li>Profile service integration</li>
 *   <li>Endpoint security through allowlist</li>
 * </ul>
 *
 * @since 1.0
 */
public abstract class RouterAbstractRouteBuilder extends RouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouterAbstractRouteBuilder.class);

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

    /** Router transport mode ({@link RouterConstants#CONFIG_TYPE_KAFKA} or {@link RouterConstants#CONFIG_TYPE_NOBROKER}) */
    protected String configType;

    /** List of allowed endpoint schemes */
    protected String allowedEndpoints;
    protected String permittedBaseDirs;

    /** Service for profile operations */
    protected ProfileService profileService;

    /**
     * Constructs a new route builder with Kafka configuration.
     *
     * @param kafkaProps map containing Kafka configuration properties
     * @param configType {@link RouterConstants#CONFIG_TYPE_KAFKA} or {@link RouterConstants#CONFIG_TYPE_NOBROKER}
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
     * Records, on the configuration itself, whether the endpoint it names can be honoured.
     *
     * <p>The permitted directories are an operational setting and the configurations are user data, so
     * the two drift apart: a configuration that was legitimate when it was created can be refused after
     * the deployment is reconfigured. Refusing it silently leaves the owner with a configuration that
     * looks fine and does nothing, so the refusal is written where they will see it. It is theirs to
     * correct or remove — nothing is deleted here.
     *
     * <p>The other way round matters just as much: restoring the permitted directories must bring the
     * configuration back on its own, without anyone having to touch it. Only the status this method
     * sets is cleared, so the record of a run that genuinely failed survives.
     *
     * <p>The configuration is saved without asking for its running route to be refreshed: the refresh
     * would rebuild the route, refuse it again and save it again, without end.
     *
     * @param configuration the configuration whose endpoint was examined
     * @param service       the service holding that kind of configuration
     * @param refusal       the reason the endpoint was refused, or {@code null} if it can be honoured
     */
    protected <T extends ImportExportConfiguration> void recordEndpointOutcome(
            T configuration, ImportExportConfigurationService<T> service, String refusal) {
        if (refusal != null) {
            configuration.setStatus(RouterConstants.CONFIG_STATUS_INVALID_ENDPOINT);
            saveQuietly(configuration, service);
        } else if (RouterConstants.CONFIG_STATUS_INVALID_ENDPOINT.equals(configuration.getStatus())) {
            configuration.setStatus(null);
            saveQuietly(configuration, service);
        }
    }

    /**
     * Saves the mark, and keeps a failure to itself.
     *
     * <p>This runs inside {@code configure()}, which builds the routes of every configuration of the
     * batch. An exception thrown here would leave {@code addRoutes} and cost all of them their routes
     * — the very failure this validation exists to prevent, over the report of a refusal rather than
     * the refusal itself. The store may be unreachable at start-up; the mark is worth what it costs,
     * and no more.
     */
    private <T extends ImportExportConfiguration> void saveQuietly(T configuration, ImportExportConfigurationService<T> service) {
        try {
            service.save(configuration, false);
        } catch (RuntimeException e) {
            LOGGER.error("Could not record the endpoint outcome on configuration {}; its route is built "
                    + "or skipped as decided, only the record of it is missing", configuration.getItemId(), e);
        }
    }

    /**
     * Records the outcome under the configuration's own tenant.
     *
     * <p>Startup builds every tenant's routes in one pass, and the configuration service refuses a save
     * whose current tenant is not the configuration's. Writing the mark from the wrong tenant would
     * either fail or store it in another tenant's index. When no context manager is available the mark
     * is written as it stands, which is what the unit tests do.
     */
    protected <T extends ImportExportConfiguration> void recordEndpointOutcome(
            T configuration, ImportExportConfigurationService<T> service, String refusal,
            ExecutionContextManager executionContextManager) {
        String tenantId = configuration.getTenantId();
        if (executionContextManager != null && tenantId != null && !tenantId.isEmpty()) {
            executionContextManager.executeAsTenant(tenantId, () -> recordEndpointOutcome(configuration, service, refusal));
        } else {
            recordEndpointOutcome(configuration, service, refusal);
        }
    }

    /**
     * Gets the appropriate endpoint URI based on configuration type and operation.
     *
     * @param direction the direction of the endpoint (to/from)
     * @param operationDepositBuffer the operation buffer identifier
     * @return either a KafkaEndpoint or a direct-endpoint URI, depending on configuration
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
