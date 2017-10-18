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
package org.apache.unomi.router.services;

import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.IRouterCamelContext;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Created by amidani on 28/04/2017.
 */
public class ExportConfigurationServiceImpl extends AbstractConfigurationServiceImpl implements ImportExportConfigurationService<ExportConfiguration> {

    private static final Logger logger = LoggerFactory.getLogger(ExportConfigurationServiceImpl.class.getName());

    public ExportConfigurationServiceImpl() {
        logger.info("Initializing export configuration service...");
    }

    @Override
    public List<ExportConfiguration> getAll() {
        return persistenceService.getAllItems(ExportConfiguration.class);
    }

    @Override
    public ExportConfiguration load(String configId) {
        return persistenceService.load(configId, ExportConfiguration.class);
    }

    @Override
    public ExportConfiguration save(ExportConfiguration exportConfiguration) {
        if (exportConfiguration.getItemId() == null) {
            exportConfiguration.setItemId(UUID.randomUUID().toString());
        }
        try {
            routerCamelContext.updateProfileReaderRoute(exportConfiguration);
        } catch (Exception e) {
            logger.error("Error when trying to save/update running Apache Camel Route: {}", exportConfiguration.getItemId());
        }
        persistenceService.save(exportConfiguration);
        return persistenceService.load(exportConfiguration.getItemId(), ExportConfiguration.class);
    }

    @Override
    public void delete(String configId) {
        try {
            routerCamelContext.killExistingRoute(configId);
        } catch (Exception e) {
            logger.error("Error when trying to delete running Apache Camel Route: {}", configId);
        }
        persistenceService.remove(configId, ExportConfiguration.class);
    }

    @Override
    public void setRouterCamelContext(IRouterCamelContext routerCamelContext) {
        super.setRouterCamelContext(routerCamelContext);
    }
}
