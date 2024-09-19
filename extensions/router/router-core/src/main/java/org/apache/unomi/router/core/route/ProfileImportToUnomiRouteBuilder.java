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
import org.apache.camel.model.RouteDefinition;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.core.processor.ImportRouteCompletionProcessor;
import org.apache.unomi.router.core.processor.UnomiStorageProcessor;
import org.apache.unomi.router.core.strategy.ArrayListAggregationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by amidani on 26/04/2017.
 */
public class ProfileImportToUnomiRouteBuilder extends RouterAbstractRouteBuilder {

    private Logger logger = LoggerFactory.getLogger(ProfileImportToUnomiRouteBuilder.class.getName());

    private UnomiStorageProcessor unomiStorageProcessor;
    private ImportRouteCompletionProcessor importRouteCompletionProcessor;

    public ProfileImportToUnomiRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    @Override
    public void configure() throws Exception {

        logger.info("Configure Recurrent Route 'To Target'");

        RouteDefinition rtDef;
        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
            rtDef = from((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_TO, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        } else {
            rtDef = from((String) getEndpointURI(RouterConstants.DIRECTION_TO, RouterConstants.DIRECT_IMPORT_DEPOSIT_BUFFER));
        }
        rtDef.choice()
                .when(header(RouterConstants.HEADER_FAILED_MESSAGE).isNull())
                .unmarshal(jacksonDataFormat)
                .process(unomiStorageProcessor)
                .otherwise()
                .log(LoggingLevel.WARN, "Failed message, skip processing!")
                .end()
                .aggregate(constant(true), new ArrayListAggregationStrategy())
                .completionPredicate(exchangeProperty("CamelSplitComplete").isEqualTo("true"))
                .eagerCheckCompletion()
                .process(importRouteCompletionProcessor)
                .to("log:org.apache.unomi.router?level=DEBUG");
    }

    public void setUnomiStorageProcessor(UnomiStorageProcessor unomiStorageProcessor) {
        this.unomiStorageProcessor = unomiStorageProcessor;
    }

    public void setImportRouteCompletionProcessor(ImportRouteCompletionProcessor importRouteCompletionProcessor) {
        this.importRouteCompletionProcessor = importRouteCompletionProcessor;
    }
}
