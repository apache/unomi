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
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Profile;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by amidani on 27/06/2017.
 */
public class ProfileExportCollectRouteBuilder extends RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ProfileExportCollectRouteBuilder.class);

    private List<ExportConfiguration> exportConfigurationList;
    private ImportExportConfigurationService<ExportConfiguration> exportConfigurationService;
    private PersistenceService persistenceService;

    private String allowedEndpoints;

    @Override
    public void configure() throws Exception {
        logger.info("Configure Recurrent Route 'Export :: Collect Data'");

        if (exportConfigurationList == null) {
            exportConfigurationList = exportConfigurationService.getAll();
        }

        //Loop on multiple export configuration
        for (final ExportConfiguration exportConfiguration : exportConfigurationList) {
            String endpoint = (String) exportConfiguration.getProperties().get("destination");

            if (StringUtils.isNotBlank(endpoint) && allowedEndpoints.contains(endpoint.substring(0, endpoint.indexOf(':')))) {
                List<Profile> profilesCollected = persistenceService.query("segments", (String) exportConfiguration.getProperties().get("segments"),
                         null, Profile.class);
                logger.info("Collected +++{}+++ profiles.", profilesCollected.size());
            } else {
                logger.error("Endpoint scheme {} is not allowed, route {} will be skipped.", endpoint.substring(0, endpoint.indexOf(':')), exportConfiguration.getItemId());
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

    public void setAllowedEndpoints(String allowedEndpoints) {
        this.allowedEndpoints = allowedEndpoints;
    }

}
