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
package org.apache.unomi.router.core.processor;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.ProfileToImport;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.api.RouteIds;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * The completion processors find the configuration to update from the route the exchange comes from,
 * whose id carries the tenant as well as the configuration id.
 */
public class RouteCompletionProcessorTest {

    @Test
    public void importCompletionUpdatesTheConfigurationOfTheRoute() throws Exception {
        ImportConfiguration configuration = new ImportConfiguration();
        configuration.setItemId("crm:import");
        configuration.setTenantId("tenant-a");
        StoredConfigurations<ImportConfiguration> store = new StoredConfigurations<>();
        store.byId.put("crm:import", configuration);
        ImportRouteCompletionProcessor processor = new ImportRouteCompletionProcessor();
        processor.setImportConfigurationService(store);
        processor.setExecutionsHistorySize(5);

        Exchange exchange = exchangeFromRoute(RouteIds.of("tenant-a", "crm:import"));
        exchange.setProperty("CamelSplitSize", 1);
        exchange.getIn().setBody(new ArrayList<>(Collections.singletonList(new ProfileToImport())));
        processor.process(exchange);

        assertSame("the configuration of the route must be the one saved", configuration, store.lastSaved);
        assertEquals(RouterConstants.CONFIG_STATUS_COMPLETE_SUCCESS, configuration.getStatus());
    }

    @Test
    public void exportCompletionUpdatesTheConfigurationOfTheRoute() throws Exception {
        ExportConfiguration configuration = new ExportConfiguration();
        configuration.setItemId("crm:export");
        configuration.setTenantId("tenant-a");
        StoredConfigurations<ExportConfiguration> store = new StoredConfigurations<>();
        store.byId.put("crm:export", configuration);
        ExportRouteCompletionProcessor processor = new ExportRouteCompletionProcessor();
        processor.setExportConfigurationService(store);
        processor.setExecutionsHistorySize(5);

        Exchange exchange = exchangeFromRoute(RouteIds.of("tenant-a", "crm:export"));
        exchange.setProperty("CamelSplitSize", 1);
        processor.process(exchange);

        assertSame("the configuration of the route must be the one saved", configuration, store.lastSaved);
        assertEquals(RouterConstants.CONFIG_STATUS_COMPLETE_SUCCESS, configuration.getStatus());
    }

    private static Exchange exchangeFromRoute(String routeId) {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setFromRouteId(routeId);
        exchange.setProperty("CamelCreatedTimestamp", new Date());
        return exchange;
    }

    /** Hands out the configurations it holds by configuration id, as the real service does within a tenant. */
    private static final class StoredConfigurations<T> implements ImportExportConfigurationService<T> {

        private final Map<String, T> byId = new HashMap<>();
        private T lastSaved;

        @Override
        public List<T> getAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public T load(String configId) {
            return byId.get(configId);
        }

        @Override
        public T save(T configuration, boolean updateRunningRoute) {
            lastSaved = configuration;
            return configuration;
        }

        @Override
        public void delete(String configId) {
        }

        @Override
        public Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> consumeConfigsToBeRefresh() {
            return Collections.emptyMap();
        }

        @Override
        public void requeueForRefresh(String tenantId, String configId, RouterConstants.CONFIG_CAMEL_REFRESH refreshType) {
        }
    }
}
