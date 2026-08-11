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
import org.apache.unomi.router.api.ImportExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;

import java.util.Map;

/**
 * Created by amidani on 13/06/2017.
 */
public abstract class RouterAbstractRouteBuilder extends RouteBuilder {

    protected JacksonDataFormat jacksonDataFormat;

    protected String kafkaHost;
    protected String kafkaPort;
    protected String kafkaImportTopic;
    protected String kafkaExportTopic;
    protected String kafkaImportGroupId;
    protected String kafkaExportGroupId;
    protected String kafkaConsumerCount;
    protected String kafkaAutoCommit;

    protected String configType;
    protected String allowedEndpoints;
    protected String permittedBaseDirs;

    protected ProfileService profileService;

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
            service.save(configuration, false);
        } else if (RouterConstants.CONFIG_STATUS_INVALID_ENDPOINT.equals(configuration.getStatus())) {
            configuration.setStatus(null);
            service.save(configuration, false);
        }
    }

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

    public void setJacksonDataFormat(JacksonDataFormat jacksonDataFormat) {
        this.jacksonDataFormat = jacksonDataFormat;
    }

    public void setAllowedEndpoints(String allowedEndpoints) {
        this.allowedEndpoints = allowedEndpoints;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

}
