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
package org.apache.unomi.router.core.context;

import org.apache.camel.CamelContext;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.ProfileToImport;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.core.processor.ExportRouteCompletionProcessor;
import org.apache.unomi.router.core.processor.ImportConfigByFileNameProcessor;
import org.apache.unomi.router.core.processor.ImportRouteCompletionProcessor;
import org.apache.unomi.router.core.processor.UnomiStorageProcessor;
import org.apache.unomi.router.api.RouteIds;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * UNOMI-1000: router startup must load recurring configs for every tenant.
 */
public class RouterCamelContextTenantLoadTest {

    private static final Map<String, String> NO_KAFKA = new HashMap<>();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final Map<String, List<ImportConfiguration>> configsByTenant = new HashMap<>();
    private final Map<String, List<ExportConfiguration>> exportConfigsByTenant = new HashMap<>();
    private final List<String> visitedTenants = new ArrayList<>();
    private String currentTenant;
    private String failingTenant;
    private boolean tenantListingFails;

    private RouterCamelContext context;
    private DefaultCamelContext camelContext;
    private TenantScopedService<ImportConfiguration> importService;

    @Before
    public void setUp() {
        context = new RouterCamelContext();
        context.setContextManager(new TenantTrackingContextManager());
        context.setTenantService(tenantService("tenant-a", "tenant-b"));
    }

    @After
    public void tearDown() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    public void loadsConfigurationsOfEveryTenantIncludingSystem() {
        configsByTenant.put("tenant-a", Collections.singletonList(config("import-a", "tenant-a")));
        configsByTenant.put("tenant-b", Collections.singletonList(config("import-b", "tenant-b")));
        configsByTenant.put(TenantService.SYSTEM_TENANT, Collections.singletonList(config("import-system", TenantService.SYSTEM_TENANT)));
        Set<String> tenantsToRetry = new HashSet<>();

        List<ImportConfiguration> loaded = context.loadConfigurationsAcrossTenants(
                new TenantScopedService<>(configsByTenant), context.startupTenantIds(), tenantsToRetry);

        assertEquals(Arrays.asList("import-a", "import-b", "import-system"), ids(loaded));
        assertEquals(Arrays.asList("tenant-a", "tenant-b", TenantService.SYSTEM_TENANT), visitedTenants);
        assertTrue(tenantsToRetry.isEmpty());
        assertNull("tenant context must be restored after the load", currentTenant);
    }

    /**
     * The startup path itself: after a restart, the routes of every tenant's recurrent configurations
     * must exist without anyone saving the configurations again.
     */
    @Test
    public void startupBuildsTheRecurrentRoutesOfEveryTenant() throws Exception {
        RouterCamelContext router = startRouter();

        for (String tenantId : Arrays.asList("tenant-a", "tenant-b")) {
            assertRoutesBuilt(tenantId);
        }

        // Removing one tenant's route must leave the other tenant's route of the same configuration id.
        router.killExistingRoute("tenant-a", "crm-import", false);
        assertNull(camelContext.getRouteDefinition(RouteIds.of("tenant-a", "crm-import")));
        assertNotNull("tenant-b keeps its route", camelContext.getRouteDefinition(RouteIds.of("tenant-b", "crm-import")));
    }

    @Test
    public void tenantWhoseLoadFailsAtStartupDoesNotBlockTheOthersAndIsRetried() throws Exception {
        failingTenant = "tenant-a";

        RouterCamelContext router = startRouter();

        assertRoutesBuilt("tenant-b");
        assertNull("tenant-a cannot have a route yet", camelContext.getRouteDefinition(RouteIds.of("tenant-a", "crm-import")));

        assertFalse("tenant-a still cannot be loaded", router.retryStartupLoads());
        router.refreshRoutes();
        assertNull(camelContext.getRouteDefinition(RouteIds.of("tenant-a", "crm-import")));

        failingTenant = null;
        assertTrue("nothing is left to retry", router.retryStartupLoads());
        router.refreshRoutes();
        assertRoutesBuilt("tenant-a");
        assertRoutesBuilt("tenant-b");
    }

    @Test
    public void refreshTimerRetriesWhatStartupCouldNotLoad() throws Exception {
        failingTenant = "tenant-a";
        RouterCamelContext router = startRouter();
        failingTenant = null;

        // first tick retries the load and hands the configurations to the refresh of the same tick
        router.refreshRoutes();

        assertRoutesBuilt("tenant-a");
    }

    @Test
    public void startupRetriesBackOffUntilTheTenantCanBeLoaded() throws Exception {
        failingTenant = "tenant-a";
        RouterCamelContext router = startRouter();
        router.setConfigsRefreshInterval(1000);

        router.retryStartupLoadsWhenDue(0);
        assertEquals("the first tick retries", 1, retriesOf("tenant-a"));
        router.retryStartupLoadsWhenDue(999);
        assertEquals("nothing is retried before the delay has passed", 1, retriesOf("tenant-a"));
        router.retryStartupLoadsWhenDue(1000);
        assertEquals("retried once the delay has passed", 2, retriesOf("tenant-a"));
        router.retryStartupLoadsWhenDue(2999);
        assertEquals("the delay doubles after an unsuccessful attempt", 2, retriesOf("tenant-a"));
        router.retryStartupLoadsWhenDue(3000);
        assertEquals(3, retriesOf("tenant-a"));

        // keep failing until the delay stops growing
        long now = 3000;
        for (int attempt = 0; attempt < 10; attempt++) {
            now += RouterCamelContext.MAX_STARTUP_RETRY_DELAY_MS;
            router.retryStartupLoadsWhenDue(now);
        }
        int retries = retriesOf("tenant-a");
        router.retryStartupLoadsWhenDue(now + RouterCamelContext.MAX_STARTUP_RETRY_DELAY_MS - 1);
        assertEquals("the delay is capped", retries, retriesOf("tenant-a"));

        failingTenant = null;
        router.retryStartupLoadsWhenDue(now + RouterCamelContext.MAX_STARTUP_RETRY_DELAY_MS);
        router.refreshRoutes();
        assertRoutesBuilt("tenant-a");

        retries = retriesOf("tenant-a");
        router.retryStartupLoadsWhenDue(now + 10 * RouterCamelContext.MAX_STARTUP_RETRY_DELAY_MS);
        assertEquals("nothing is retried once every tenant is started", retries, retriesOf("tenant-a"));
    }

    @Test
    public void startupRetryKeepsARefreshAlreadyQueuedForAConfiguration() throws Exception {
        failingTenant = "tenant-a";
        RouterCamelContext router = startRouter();
        failingTenant = null;
        // the configuration is deleted while its tenant is still waiting for the startup retry
        importService.requeueForRefresh("tenant-a", "crm-import", RouterConstants.CONFIG_CAMEL_REFRESH.REMOVED);

        router.refreshRoutes();

        assertNull("the queued removal must win over the startup retry",
                camelContext.getRouteDefinition(RouteIds.of("tenant-a", "crm-import")));
        assertNotNull(camelContext.getRouteDefinition(RouteIds.of("tenant-a", "crm-export")));
    }

    @Test
    public void tenantListingFailureAtStartupIsRetried() throws Exception {
        tenantListingFails = true;

        RouterCamelContext router = startRouter();

        assertNull("no tenant is known yet", camelContext.getRouteDefinition(RouteIds.of("tenant-a", "crm-import")));

        assertFalse("tenants still cannot be listed", router.retryStartupLoads());

        tenantListingFails = false;
        assertTrue("nothing is left to retry", router.retryStartupLoads());
        router.refreshRoutes();
        assertRoutesBuilt("tenant-a");
        assertRoutesBuilt("tenant-b");
    }

    @Test
    public void nothingIsReloadedOnceEveryTenantIsStarted() throws Exception {
        RouterCamelContext router = startRouter();
        visitedTenants.clear();

        router.refreshRoutes();

        assertTrue("the refresh timer must not reload tenants on every tick", visitedTenants.isEmpty());
    }

    /**
     * Starts a router over two tenants that both own a recurrent import and a recurrent export, under the same
     * configuration ids: ids are only unique within a tenant.
     */
    private RouterCamelContext startRouter() throws Exception {
        File importRoot = tmp.newFolder("permitted-import");
        File exportRoot = tmp.newFolder("permitted-export");
        for (String tenantId : Arrays.asList("tenant-a", "tenant-b")) {
            configsByTenant.put(tenantId, Collections.singletonList(
                    recurrentImport("crm-import", tenantId, new File(importRoot, tenantId))));
            exportConfigsByTenant.put(tenantId, Collections.singletonList(
                    recurrentExport("crm-export", tenantId, new File(exportRoot, tenantId))));
        }

        camelContext = new DefaultCamelContext();
        RouterCamelContext router = new RouterCamelContext() {
            @Override
            CamelContext createCamelContext() {
                return camelContext;
            }
        };
        router.setContextManager(new TenantTrackingContextManager());
        router.setTenantService(tenantService("tenant-a", "tenant-b"));
        importService = new TenantScopedService<>(configsByTenant);
        router.setImportConfigurationService(importService);
        router.setExportConfigurationService(new TenantScopedService<>(exportConfigsByTenant));
        router.setProfileService(noOpProfileService());
        router.setKafkaProps(NO_KAFKA);
        router.setConfigType(RouterConstants.CONFIG_TYPE_NOBROKER);
        router.setJacksonDataFormat(new JacksonDataFormat(ProfileToImport.class));
        router.setAllowedEndpoints("file");
        router.setPermittedImportBaseDirs(importRoot.getAbsolutePath());
        router.setPermittedExportBaseDirs(exportRoot.getAbsolutePath());
        router.setUnomiStorageProcessor(new UnomiStorageProcessor());
        router.setImportRouteCompletionProcessor(new ImportRouteCompletionProcessor());
        router.setExportRouteCompletionProcessor(new ExportRouteCompletionProcessor());
        router.setImportConfigByFileNameProcessor(new ImportConfigByFileNameProcessor());
        router.initCamel();
        return router;
    }

    /** How many times the startup retry tried to load the tenant's import configurations. */
    private int retriesOf(String tenantId) {
        return importService.loadAttempts.getOrDefault(tenantId, 0) - 1;
    }

    private void assertRoutesBuilt(String tenantId) {
        for (String configId : Arrays.asList("crm-import", "crm-export")) {
            assertNotNull("the route of " + configId + " must be built for " + tenantId,
                    camelContext.getRouteDefinition(RouteIds.of(tenantId, configId)));
        }
    }

    private static ImportConfiguration recurrentImport(String id, String tenantId, File directory) {
        assertTrue(directory.mkdirs());
        ImportConfiguration configuration = config(id, tenantId);
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        // inactive: the route is built but does not start polling during the test
        configuration.setActive(false);
        configuration.getProperties().put("source", "file://" + directory.getAbsolutePath() + "?fileName=profiles.csv");
        configuration.getProperties().put("mapping", Collections.singletonMap("0", 0));
        return configuration;
    }

    private static ExportConfiguration recurrentExport(String id, String tenantId, File directory) {
        assertTrue(directory.mkdirs());
        ExportConfiguration configuration = new ExportConfiguration();
        configuration.setItemId(id);
        configuration.setTenantId(tenantId);
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        configuration.setActive(false);
        configuration.getProperties().put("destination", "file://" + directory.getAbsolutePath() + "?fileName=profiles.csv");
        configuration.getProperties().put("mapping", Collections.singletonMap("0", "firstName"));
        configuration.getProperties().put("segment", "exportSegment");
        configuration.getProperties().put("period", "1m");
        return configuration;
    }

    private static ProfileService noOpProfileService() {
        return (ProfileService) Proxy.newProxyInstance(
                ProfileService.class.getClassLoader(),
                new Class<?>[]{ProfileService.class},
                (proxy, method, args) -> Collection.class.isAssignableFrom(method.getReturnType())
                        ? Collections.emptyList() : null);
    }

    private static List<String> ids(List<ImportConfiguration> configs) {
        return configs.stream().map(ImportConfiguration::getItemId).collect(Collectors.toList());
    }

    private static ImportConfiguration config(String id, String tenantId) {
        ImportConfiguration config = new ImportConfiguration();
        config.setItemId(id);
        config.setTenantId(tenantId);
        return config;
    }

    private TenantService tenantService(String... tenantIds) {
        return (TenantService) Proxy.newProxyInstance(
                TenantService.class.getClassLoader(),
                new Class<?>[]{TenantService.class},
                (proxy, method, args) -> {
                    if ("getAllTenants".equals(method.getName())) {
                        if (tenantListingFails) {
                            throw new IllegalStateException("persistence unavailable");
                        }
                        List<Tenant> tenants = new ArrayList<>();
                        for (String tenantId : tenantIds) {
                            Tenant tenant = new Tenant();
                            tenant.setItemId(tenantId);
                            tenants.add(tenant);
                        }
                        return tenants;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /** Returns only the configurations of the tenant the context manager is currently switched to. */
    private class TenantScopedService<T> implements ImportExportConfigurationService<T> {
        private final Map<String, List<T>> configs;
        private final Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> toRefresh = new HashMap<>();
        /** Calls to getAll() per tenant, the startup load included. */
        private final Map<String, Integer> loadAttempts = new HashMap<>();

        TenantScopedService(Map<String, List<T>> configs) {
            this.configs = configs;
        }

        @Override
        public List<T> getAll() {
            loadAttempts.merge(String.valueOf(currentTenant), 1, Integer::sum);
            if (currentTenant != null && currentTenant.equals(failingTenant)) {
                throw new IllegalStateException("cannot load configurations of " + currentTenant);
            }
            return configs.getOrDefault(currentTenant, Collections.emptyList());
        }

        @Override
        public T load(String configId) {
            return configs.getOrDefault(currentTenant, Collections.emptyList()).stream()
                    .filter(config -> configId.equals(((Item) config).getItemId())).findFirst().orElse(null);
        }

        @Override
        public T save(T configuration, boolean updateRunningRoute) {
            return configuration;
        }

        @Override
        public void delete(String configId) {
        }

        @Override
        public Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> consumeConfigsToBeRefresh() {
            Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> consumed = new HashMap<>(toRefresh);
            toRefresh.clear();
            return consumed;
        }

        @Override
        public void requeueForRefresh(String tenantId, String configId,
                                     RouterConstants.CONFIG_CAMEL_REFRESH refreshType) {
            // like the real service: a refresh already queued for the configuration is kept
            toRefresh.computeIfAbsent(tenantId, k -> new HashMap<>()).putIfAbsent(configId, refreshType);
        }
    }

    private class TenantTrackingContextManager implements ExecutionContextManager {
        @Override
        public ExecutionContext getCurrentContext() {
            return null;
        }

        @Override
        public void setCurrentContext(ExecutionContext context) {
        }

        @Override
        public <T> T executeAsSystem(Supplier<T> operation) {
            return operation.get();
        }

        @Override
        public void executeAsSystem(Runnable operation) {
            operation.run();
        }

        @Override
        public <T> T executeAsTenant(String tenantId, Supplier<T> operation) {
            visitedTenants.add(tenantId);
            String previous = currentTenant;
            currentTenant = tenantId;
            try {
                return operation.get();
            } finally {
                currentTenant = previous;
            }
        }

        @Override
        public void executeAsTenant(String tenantId, Runnable operation) {
            executeAsTenant(tenantId, () -> {
                operation.run();
                return null;
            });
        }

        @Override
        public ExecutionContext createContext(String tenantId) {
            return null;
        }
    }
}
