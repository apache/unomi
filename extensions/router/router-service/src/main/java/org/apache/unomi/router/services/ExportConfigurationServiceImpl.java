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

import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage Configuration of Item to export
 * Created by amidani on 28/04/2017.
 */
public class ExportConfigurationServiceImpl implements ImportExportConfigurationService<ExportConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportConfigurationServiceImpl.class.getName());


    private PersistenceService persistenceService;
    private ExecutionContextManager executionContextManager;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }

    private final Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> camelConfigsToRefresh = new ConcurrentHashMap<>();

    public ExportConfigurationServiceImpl() {
        LOGGER.info("Initializing export configuration service...");
    }

    @Override
    public List<ExportConfiguration> getAll() {
        return persistenceService.getAllItems(ExportConfiguration.class);
    }

    @Override
    public ExportConfiguration load(String configId) {
        ExecutionContext context = executionContextManager.getCurrentContext();
        ExportConfiguration config = persistenceService.load(configId, ExportConfiguration.class);
        if (config != null && !context.getTenantId().equals(config.getTenantId()) && !context.isSystem()) {
            return null;
        }
        return config;
    }

    @Override
    public ExportConfiguration save(ExportConfiguration exportConfiguration, boolean updateRunningRoute) {
        ExecutionContext context = executionContextManager.getCurrentContext();
        if (exportConfiguration.getItemId() == null) {
            exportConfiguration.setItemId(UUID.randomUUID().toString());
        }
        if (exportConfiguration.getTenantId() == null) {
            exportConfiguration.setTenantId(context.getTenantId());
        } else if (!context.isSystem() && !context.getTenantId().equals(exportConfiguration.getTenantId())) {
            throw new SecurityException("Cannot save configuration for different tenant");
        }
        persistenceService.save(exportConfiguration);

        if (updateRunningRoute) {
            String tenantId = exportConfiguration.getTenantId();
            camelConfigsToRefresh.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                    .put(exportConfiguration.getItemId(), RouterConstants.CONFIG_CAMEL_REFRESH.UPDATED);
        }

        return persistenceService.load(exportConfiguration.getItemId(), ExportConfiguration.class);
    }

    @Override
    public void delete(String configId) {
        ExecutionContext context = executionContextManager.getCurrentContext();
        ExportConfiguration config = load(configId);
        if (config != null && (context.isSystem() || context.getTenantId().equals(config.getTenantId()))) {
            persistenceService.remove(configId, ExportConfiguration.class);
            String tenantId = config.getTenantId();
            camelConfigsToRefresh.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                    .put(configId, RouterConstants.CONFIG_CAMEL_REFRESH.REMOVED);
        }
    }

    @Override
    public Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> consumeConfigsToBeRefresh() {
        Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> result = new HashMap<>(camelConfigsToRefresh);
        camelConfigsToRefresh.clear();
        return result;
    }
}
