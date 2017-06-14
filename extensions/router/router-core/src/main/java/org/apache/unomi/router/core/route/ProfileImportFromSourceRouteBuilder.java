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
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.core.RouterConstants;
import org.apache.unomi.router.core.exception.BadProfileDataFormatException;
import org.apache.unomi.router.core.processor.LineSplitFailureHandler;
import org.apache.unomi.router.core.processor.LineSplitProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Created by amidani on 26/04/2017.
 */

public class ProfileImportFromSourceRouteBuilder extends ProfileImportAbstractRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ProfileImportFromSourceRouteBuilder.class.getName());

    private List<ImportConfiguration> importConfigurationList;


    public ProfileImportFromSourceRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    @Override
    public void configure() throws Exception {

        logger.info("Configure Recurrent Route 'From Source'");

        //Loop on multiple import configuration
        for (ImportConfiguration importConfiguration : importConfigurationList) {
            if (importConfiguration.getProperties().size() > 0 &&
                    StringUtils.isNotEmpty((String) importConfiguration.getProperties().get("source"))) {
                //Prepare Split Processor
                LineSplitProcessor lineSplitProcessor = new LineSplitProcessor();
                lineSplitProcessor.setFieldsMapping((Map<String, Integer>) importConfiguration.getProperties().get("mapping"));
                lineSplitProcessor.setOverwriteExistingProfiles(importConfiguration.isOverwriteExistingProfiles());
                lineSplitProcessor.setPropertiesToOverwrite(importConfiguration.getPropertiesToOverwrite());
                lineSplitProcessor.setMergingProperty(importConfiguration.getMergingProperty());
                lineSplitProcessor.setColumnSeparator(importConfiguration.getColumnSeparator());

                onException(BadProfileDataFormatException.class)
                        .log(LoggingLevel.ERROR, "Error processing record ${exchangeProperty.CamelSplitIndex}++ !")
                        .handled(true)
                        .process(new LineSplitFailureHandler())
                        .to("direct:errors");

                errorHandler(deadLetterChannel("direct:errors"));

                ProcessorDefinition prDef = from((String) importConfiguration.getProperties().get("source"))
                        .routeId(importConfiguration.getItemId())// This allow identification of the route for manual start/stop
                        .autoStartup(importConfiguration.isActive())// Auto-start if the import configuration is set active
                        .split(bodyAs(String.class).tokenize(importConfiguration.getLineSeparator()))
                        .log(LoggingLevel.INFO, "Splitted into ${exchangeProperty.CamelSplitSize} records")
                        .setHeader(RouterConstants.HEADER_PROFILES_COUNT, exchangeProperty("CamelSplitSize}"))
                        .setHeader(RouterConstants.HEADER_CONFIG_TYPE, constant(configType))
                        .process(lineSplitProcessor)
                        .log(LoggingLevel.INFO, "Split IDX ${exchangeProperty.CamelSplitIndex} record")
                        .to("log:org.apache.unomi.router?level=INFO")
                        .marshal(jacksonDataFormat)
                        .convertBodyTo(String.class);

                if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
                    prDef.to((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_FROM));
                } else {
                    prDef.to((String) getEndpointURI(RouterConstants.DIRECTION_FROM));
                }

                from("direct:errors").to("log:org.apache.unomi.router?level=ERROR");
            }
        }
    }

    public void setImportConfigurationList(List<ImportConfiguration> importConfigurationList) {
        this.importConfigurationList = importConfigurationList;
    }

}
