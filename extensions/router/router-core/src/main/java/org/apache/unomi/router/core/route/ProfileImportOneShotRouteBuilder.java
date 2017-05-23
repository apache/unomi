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
import org.apache.unomi.router.core.processor.ImportConfigByFileNameProcessor;
import org.apache.unomi.router.core.processor.LineSplitProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by amidani on 22/05/2017.
 */
public class ProfileImportOneShotRouteBuilder extends RouteBuilder {

    private Logger logger = LoggerFactory.getLogger(ProfileImportOneShotRouteBuilder.class.getName());

    private ImportConfigByFileNameProcessor importConfigByFileNameProcessor;
    private JacksonDataFormat jacksonDataFormat;
    private String uploadDir;
    private String kafkaHost;
    private String kafkaPort;
    private String kafkaImportTopic;
    private String kafkaImportGroupId;

    private final String IMPORT_ONESHOT_ROUTE_ID = "ONE_SHOT_ROUTE";

    public ProfileImportOneShotRouteBuilder(Map<String, String> kafkaProps) {
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

        LineSplitProcessor lineSplitProcessor = new LineSplitProcessor();


        from("file://"+uploadDir+"?include=.*.csv&consumer.delay=1m")
                .routeId(IMPORT_ONESHOT_ROUTE_ID)
                .autoStartup(true)
                .process(importConfigByFileNameProcessor)
                .split(bodyAs(String.class).tokenize("${in.header.importConfigOneShot.getLineSeparator}"))
                .process(lineSplitProcessor)
                .to("log:org.apache.unomi.router?level=INFO")
                .marshal(jacksonDataFormat)
                .convertBodyTo(String.class)
                .to(endpoint);
    }

    public void setImportConfigByFileNameProcessor(ImportConfigByFileNameProcessor importConfigByFileNameProcessor) {
        this.importConfigByFileNameProcessor = importConfigByFileNameProcessor;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public void setJacksonDataFormat(JacksonDataFormat jacksonDataFormat) {
        this.jacksonDataFormat = jacksonDataFormat;
    }
}
