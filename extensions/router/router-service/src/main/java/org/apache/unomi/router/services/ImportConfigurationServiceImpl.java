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
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage Configuration of object to import
 * Created by amidani on 28/04/2017.
 */
public class ImportConfigurationServiceImpl implements ImportExportConfigurationService<ImportConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportConfigurationServiceImpl.class.getName());

    private PersistenceService persistenceService;
    private ExecutionContextManager executionContextManager;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }

    private final Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> camelConfigsToRefresh = new ConcurrentHashMap<>();

    public ImportConfigurationServiceImpl() {
        LOGGER.info("Initializing import configuration service...");
    }

    @Override
    public List<ImportConfiguration> getAll() {
        return persistenceService.getAllItems(ImportConfiguration.class);
    }

    @Override
    public ImportConfiguration load(String configId) {
        ExecutionContext context = executionContextManager.getCurrentContext();
        ImportConfiguration config = persistenceService.load(configId, ImportConfiguration.class);
        if (config != null && !context.getTenantId().equals(config.getTenantId()) && !context.isSystem()) {
            return null;
        }
        return config;
    }

    @Override
    public ImportConfiguration save(ImportConfiguration importConfiguration, boolean updateRunningRoute) {
        ExecutionContext context = executionContextManager.getCurrentContext();
        if (importConfiguration.getItemId() == null) {
            importConfiguration.setItemId(UUID.randomUUID().toString());
        }
        if (importConfiguration.getTenantId() == null) {
            importConfiguration.setTenantId(context.getTenantId());
        } else if (!context.isSystem() && !context.getTenantId().equals(importConfiguration.getTenantId())) {
            throw new SecurityException("Cannot save configuration for different tenant");
        }
        if (updateRunningRoute) {
            String tenantId = importConfiguration.getTenantId();
            camelConfigsToRefresh.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                    .put(importConfiguration.getItemId(), RouterConstants.CONFIG_CAMEL_REFRESH.UPDATED);
        }
        persistenceService.save(importConfiguration);
        return persistenceService.load(importConfiguration.getItemId(), ImportConfiguration.class);
    }

    @Override
    public void delete(String configId) {
        ExecutionContext context = executionContextManager.getCurrentContext();
        ImportConfiguration config = load(configId);
        if (config != null && (context.isSystem() || context.getTenantId().equals(config.getTenantId()))) {
            persistenceService.remove(configId, ImportConfiguration.class);
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
