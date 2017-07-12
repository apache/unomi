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
