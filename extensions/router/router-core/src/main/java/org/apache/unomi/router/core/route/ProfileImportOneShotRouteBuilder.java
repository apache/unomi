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
import org.apache.unomi.router.core.exception.BadProfileDataFormatException;
import org.apache.unomi.router.core.processor.ImportConfigByFileNameProcessor;
import org.apache.unomi.router.core.processor.LineSplitFailureHandler;
import org.apache.unomi.router.core.processor.LineSplitProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by amidani on 22/05/2017.
 */
public class ProfileImportOneShotRouteBuilder extends RouterAbstractRouteBuilder {

    private Logger logger = LoggerFactory.getLogger(ProfileImportOneShotRouteBuilder.class.getName());
    private ImportConfigByFileNameProcessor importConfigByFileNameProcessor;
    private String uploadDir;

    public ProfileImportOneShotRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    @Override
    public void configure() throws Exception {

        logger.info("Configure OneShot Route...");

        ProcessorDefinition prDefErr = onException(BadProfileDataFormatException.class)
                .log(LoggingLevel.ERROR, "Error processing record ${exchangeProperty.CamelSplitIndex}++ !")
                .handled(true)
                .process(new LineSplitFailureHandler());

        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
            prDefErr.to((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        } else {
            prDefErr.to((String) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        }

        LineSplitProcessor lineSplitProcessor = new LineSplitProcessor();
        lineSplitProcessor.setProfileService(profileService);

        ProcessorDefinition prDef = from("file://" + uploadDir + "?include=.*.csv&consumer.delay=1m")
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

    public void setImportConfigByFileNameProcessor(ImportConfigByFileNameProcessor importConfigByFileNameProcessor) {
        this.importConfigByFileNameProcessor = importConfigByFileNameProcessor;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
}
