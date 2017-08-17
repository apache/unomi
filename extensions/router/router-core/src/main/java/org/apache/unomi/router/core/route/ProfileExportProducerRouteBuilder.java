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

import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ProfileExportService;
import org.apache.unomi.router.core.processor.ExportRouteCompletionProcessor;
import org.apache.unomi.router.core.processor.LineBuildProcessor;
import org.apache.unomi.router.core.strategy.StringLinesAggregationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by amidani on 28/06/2017.
 */
public class ProfileExportProducerRouteBuilder extends RouterAbstractRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ProfileExportProducerRouteBuilder.class);

    private ExportRouteCompletionProcessor exportRouteCompletionProcessor;

    private ProfileExportService profileExportService;

    public ProfileExportProducerRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    public void setProfileExportService(ProfileExportService profileExportService) {
        this.profileExportService = profileExportService;
    }

    @Override
    public void configure() throws Exception {

        logger.info("Configure Recurrent Route 'Export :: Data Producer'");

        RouteDefinition rtDef;
        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
            rtDef = from((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_TO, RouterConstants.DIRECT_EXPORT_DEPOSIT_BUFFER));
        } else {
            rtDef = from((String) getEndpointURI(RouterConstants.DIRECTION_TO, RouterConstants.DIRECT_EXPORT_DEPOSIT_BUFFER));
        }

        LineBuildProcessor processor = new LineBuildProcessor(profileExportService);

        rtDef.unmarshal(jacksonDataFormat)
                .process(processor)
                .aggregate(constant(true), new StringLinesAggregationStrategy())
                .completionPredicate(exchangeProperty("CamelSplitSize").isEqualTo(exchangeProperty("CamelAggregatedSize")))
                .eagerCheckCompletion()
                .process(exportRouteCompletionProcessor)
                .toD("${in.header.exportConfig.getProperty('destination')}");

    }

    public void setExportRouteCompletionProcessor(ExportRouteCompletionProcessor exportRouteCompletionProcessor) {
        this.exportRouteCompletionProcessor = exportRouteCompletionProcessor;
    }

}
