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
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.core.bean.CollectProfileBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Created by amidani on 27/06/2017.
 */
public class ProfileExportCollectRouteBuilder extends RouterAbstractRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ProfileExportCollectRouteBuilder.class);

    private List<ExportConfiguration> exportConfigurationList;
    private ImportExportConfigurationService<ExportConfiguration> exportConfigurationService;
    private PersistenceService persistenceService;

    public ProfileExportCollectRouteBuilder(Map<String, String> kafkaProps, String configType) {
        super(kafkaProps, configType);
    }

    @Override
    public void configure() throws Exception {
        logger.info("Configure Recurrent Route 'Export :: Collect Data'");

        if (exportConfigurationList == null) {
            exportConfigurationList = exportConfigurationService.getAll();
        }

        CollectProfileBean collectProfileBean = new CollectProfileBean();
        collectProfileBean.setPersistenceService(persistenceService);


        //Loop on multiple export configuration
        for (final ExportConfiguration exportConfiguration : exportConfigurationList) {
            if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(exportConfiguration.getConfigType()) &&
                    exportConfiguration.getProperties() != null && exportConfiguration.getProperties().size() > 0) {
                if ((Map<String, String>) exportConfiguration.getProperties().get("mapping") != null) {
                    String destinationEndpoint = (String) exportConfiguration.getProperties().get("destination");
                    if (StringUtils.isNotBlank(destinationEndpoint) && allowedEndpoints.contains(destinationEndpoint.substring(0, destinationEndpoint.indexOf(':')))) {
                        ProcessorDefinition prDef = from("timer://collectProfile?fixedRate=true&period=" + (String) exportConfiguration.getProperties().get("period"))
                                .routeId(exportConfiguration.getItemId())// This allow identification of the route for manual start/stop
                                .autoStartup(exportConfiguration.isActive())
                                .bean(collectProfileBean, "extractProfileBySegment(" + exportConfiguration.getProperties().get("segment") + ")")
                                .split(body())
                                .marshal(jacksonDataFormat)
                                .convertBodyTo(String.class)
                                .setHeader(RouterConstants.HEADER_EXPORT_CONFIG, constant(exportConfiguration))
                                .log(LoggingLevel.DEBUG, "BODY : ${body}");
                        if (RouterConstants.CONFIG_TYPE_KAFKA.equals(configType)) {
                            prDef.to((KafkaEndpoint) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_EXPORT_DEPOSIT_BUFFER));
                        } else {
                            prDef.to((String) getEndpointURI(RouterConstants.DIRECTION_FROM, RouterConstants.DIRECT_EXPORT_DEPOSIT_BUFFER));
                        }
                    } else {
                        logger.error("Endpoint scheme {} is not allowed, route {} will be skipped.", destinationEndpoint.substring(0, destinationEndpoint.indexOf(':')), exportConfiguration.getItemId());
                    }
                } else {
                    logger.warn("Mapping is null in export configuration, route {} will be skipped!", exportConfiguration.getItemId());
                }
            } else {
                logger.warn("Export configuration incomplete, route {} will be skipped!", exportConfiguration.getItemId());
            }
        }
    }

    public void setExportConfigurationList(List<ExportConfiguration> exportConfigurationList) {
        this.exportConfigurationList = exportConfigurationList;
    }

    public void setExportConfigurationService(ImportExportConfigurationService<ExportConfiguration> exportConfigurationService) {
        this.exportConfigurationService = exportConfigurationService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

}
