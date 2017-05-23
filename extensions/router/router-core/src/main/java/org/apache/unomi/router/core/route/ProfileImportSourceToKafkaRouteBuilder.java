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
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.core.processor.LineSplitProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Created by amidani on 26/04/2017.
 */

public class ProfileImportSourceToKafkaRouteBuilder extends RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ProfileImportSourceToKafkaRouteBuilder.class.getName());

    private List<ImportConfiguration> importConfigurationList;
    private JacksonDataFormat jacksonDataFormat;
    private String kafkaHost;
    private String kafkaPort;
    private String kafkaImportTopic;
    private String kafkaImportGroupId;

    public ProfileImportSourceToKafkaRouteBuilder(Map<String, String> kafkaProps) {
        kafkaHost = kafkaProps.get("kafkaHost");
        kafkaPort = kafkaProps.get("kafkaPort");
        kafkaImportTopic = kafkaProps.get("kafkaImportTopic");
        kafkaImportGroupId = kafkaProps.get("kafkaImportGroupId");
    }

    @Override
    public void configure() throws Exception {
        //Prepare Kafka Deposit
        StringBuilder kafkaUri = new StringBuilder("kafka:");
        kafkaUri.append(kafkaHost).append(":").append(kafkaPort).append("?topic=").append(kafkaImportTopic);
        if(StringUtils.isNotBlank(kafkaImportGroupId)) {
            kafkaUri.append("&groupId="+ kafkaImportGroupId);
        }

        KafkaConfiguration kafkaConfiguration = new KafkaConfiguration();
        kafkaConfiguration.setBrokers(kafkaHost+":"+kafkaPort);
        kafkaConfiguration.setTopic(kafkaImportTopic);
        kafkaConfiguration.setGroupId(kafkaImportGroupId);
        KafkaEndpoint endpoint = new KafkaEndpoint(kafkaUri.toString(), new KafkaComponent(this.getContext()));
        endpoint.setConfiguration(kafkaConfiguration);

        //Loop on multiple import configuration
        for(ImportConfiguration importConfiguration : importConfigurationList) {
            if(importConfiguration.getProperties().size() > 0 &&
                    StringUtils.isNotEmpty((String) importConfiguration.getProperties().get("source"))) {
                //Prepare Split Processor
                LineSplitProcessor lineSplitProcessor = new LineSplitProcessor();
                lineSplitProcessor.setFieldsMapping((Map<String, Integer>) importConfiguration.getProperties().get("mapping"));
                lineSplitProcessor.setOverwriteExistingProfiles(importConfiguration.isOverwriteExistingProfiles());
                lineSplitProcessor.setPropertiesToOverwrite(importConfiguration.getPropertiesToOverwrite());
                lineSplitProcessor.setMergingProperty(importConfiguration.getMergingProperty());
                lineSplitProcessor.setColumnSeparator(importConfiguration.getColumnSeparator());

                from((String) importConfiguration.getProperties().get("source"))
                        .routeId(importConfiguration.getItemId())// This allow identification of the route for manual start/stop
                        .autoStartup(importConfiguration.isActive())// Auto-start if the import configuration is set active
                        .split(bodyAs(String.class).tokenize(importConfiguration.getLineSeparator()))
                        .process(lineSplitProcessor)
                        .to("log:org.apache.unomi.router?level=INFO")
                        .marshal(jacksonDataFormat)
                        .convertBodyTo(String.class)
                        .to(endpoint);
            }
        }
    }

    public void setImportConfigurationList(List<ImportConfiguration> importConfigurationList) {
        this.importConfigurationList = importConfigurationList;
    }

    public void setJacksonDataFormat(JacksonDataFormat jacksonDataFormat) {
        this.jacksonDataFormat = jacksonDataFormat;
    }

    public void setKafkaHost(String kafkaHost) {
        this.kafkaHost = kafkaHost;
    }

    public void setKafkaPort(String kafkaPort) {
        this.kafkaPort = kafkaPort;
    }

    public void setKafkaImportTopic(String kafkaImportTopic) {
        this.kafkaImportTopic = kafkaImportTopic;
    }

    public void setKafkaImportGroupId(String kafkaImportGroupId) {
        this.kafkaImportGroupId = kafkaImportGroupId;
    }

}
