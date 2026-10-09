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
import org.apache.unomi.router.core.route.RouterTestFixtures;
import org.apache.unomi.router.api.RouteIds;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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

    private DefaultCamelContext camelContext;
    private TenantScopedService<ImportConfiguration> importService;
    private TenantScopedService<ExportConfiguration> exportService;
    private String unswitchableTenant;

    @After
    public void tearDown() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    /**
     * The startup path itself: after a restart, the routes of every tenant's recurrent configurations
     * must exist without anyone saving the configurations again.
     */
    @Test
    public void startupBuildsTheRecurrentRoutesOfEveryTenant() throws Exception {
        RouterCamelContext router = startRouter();

        for (String tenantId : Arrays.asList("tenant-a", "tenant-b", TenantService.SYSTEM_TENANT)) {
            assertRoutesBuilt(tenantId);
        }
        assertNull("tenant context must be restored after startup", currentTenant);

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

        assertEquals("the queued removal must win over the startup retry",
                RouterConstants.CONFIG_CAMEL_REFRESH.REMOVED, importService.lastConsumed.get("tenant-a").get("crm-import"));
        assertNull(camelContext.getRouteDefinition(RouteIds.of("tenant-a", "crm-import")));
        assertNotNull(camelContext.getRouteDefinition(RouteIds.of("tenant-a", "crm-export")));
    }

    @Test
    public void tenantThatCannotBeSwitchedToDoesNotKeepTheOthersFromBeingRefreshed() throws Exception {
        RouterCamelContext router = startRouter();
        router.killExistingRoute("tenant-b", "crm-import", false);
        router.killExistingRoute("tenant-b", "crm-export", false);
        // tenant-a is refreshed first and cannot be switched to, as a tenant that was just deleted
        for (TenantScopedService<?> service : Arrays.asList(importService, exportService)) {
            String configId = service == importService ? "crm-import" : "crm-export";
            service.requeueForRefresh("tenant-a", configId, RouterConstants.CONFIG_CAMEL_REFRESH.UPDATED);
            service.requeueForRefresh("tenant-b", configId, RouterConstants.CONFIG_CAMEL_REFRESH.UPDATED);
        }
        unswitchableTenant = "tenant-a";

        router.refreshRoutes();

        assertRoutesBuilt("tenant-b");
    }

    @Test
    public void configurationWhoseRouteCannotBeBuiltDoesNotKeepTheOthersFromStarting() throws Exception {
        // an export without a period cannot be turned into a timer route
        ExportConfiguration broken = recurrentExport("broken-export", "tenant-a", new File(exportRoot(), "tenant-a"));
        broken.getProperties().remove("period");
        // active, so that the route is started, which is when its timer endpoint is found to be unusable
        broken.setActive(true);
        exportConfigsByTenant.computeIfAbsent("tenant-a", k -> new ArrayList<>()).add(broken);

        RouterCamelContext router = startRouter();

        for (String tenantId : Arrays.asList("tenant-a", "tenant-b", TenantService.SYSTEM_TENANT)) {
            assertRoutesBuilt(tenantId);
        }
        assertNull(camelContext.getRoute(RouteIds.of("tenant-a", "broken-export")));

        // the refresh retries it, then gives up and records the failure on the configuration
        for (int tick = 0; tick < 10; tick++) {
            router.refreshRoutes();
        }
        assertEquals(RouterConstants.CONFIG_STATUS_ROUTE_CREATION_FAILED, broken.getStatus());
        assertTrue("a configuration that was given up on is not retried forever", exportService.toRefresh.isEmpty());
        assertEquals("failed attempts must not leave route definitions behind", 0, camelContext.getRouteDefinitions().stream()
                .filter(route -> RouteIds.of("tenant-a", "broken-export").equals(route.getId())).count());
        assertRoutesBuilt("tenant-a");
    }

    @Test
    public void refreshesOfATenantThatCannotBeSwitchedToAreRetriedOnTheNextTicks() throws Exception {
        RouterCamelContext router = startRouter();
        router.killExistingRoute("tenant-a", "crm-import", false);
        importService.requeueForRefresh("tenant-a", "crm-import", RouterConstants.CONFIG_CAMEL_REFRESH.UPDATED);
        unswitchableTenant = "tenant-a";

        router.refreshRoutes();
        assertNull(camelContext.getRouteDefinition(RouteIds.of("tenant-a", "crm-import")));

        unswitchableTenant = null;
        router.refreshRoutes();
        assertNotNull("the refresh that could not run must not be lost",
                camelContext.getRouteDefinition(RouteIds.of("tenant-a", "crm-import")));
    }

    @Test
    public void refreshesOfATenantThatCanNeverBeSwitchedToAreGivenUpOn() throws Exception {
        RouterCamelContext router = startRouter();
        importService.requeueForRefresh("tenant-a", "crm-import", RouterConstants.CONFIG_CAMEL_REFRESH.UPDATED);
        unswitchableTenant = "tenant-a";

        for (int tick = 0; tick < 10; tick++) {
            router.refreshRoutes();
        }

        assertTrue("a tenant that is gone must not be retried on every tick forever", importService.toRefresh.isEmpty());
    }

    @Test
    public void shutdownOfARouterThatNeverStartedDoesNotFail() throws Exception {
        RouterCamelContext neverStarted = new RouterCamelContext();

        neverStarted.destroy();
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
     * Starts a router over two tenants and the system tenant, which all own a recurrent import and a recurrent
     * export under the same configuration ids: ids are only unique within a tenant. Configurations a test put
     * in place beforehand are kept.
     */
    private RouterCamelContext startRouter() throws Exception {
        File importRoot = new File(tmp.getRoot(), "permitted-import");
        File exportRoot = exportRoot();
        for (String tenantId : Arrays.asList("tenant-a", "tenant-b", TenantService.SYSTEM_TENANT)) {
            configsByTenant.computeIfAbsent(tenantId, k -> new ArrayList<>())
                    .add(recurrentImport("crm-import", tenantId, new File(importRoot, tenantId)));
            exportConfigsByTenant.computeIfAbsent(tenantId, k -> new ArrayList<>())
                    .add(recurrentExport("crm-export", tenantId, new File(exportRoot, tenantId)));
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
        exportService = new TenantScopedService<>(exportConfigsByTenant);
        router.setExportConfigurationService(exportService);
        router.setProfileService(RouterTestFixtures.noOpProfileService());
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

    private File exportRoot() {
        return new File(tmp.getRoot(), "permitted-export");
    }

    private void assertRoutesBuilt(String tenantId) {
        for (String configId : Arrays.asList("crm-import", "crm-export")) {
            assertNotNull("the route of " + configId + " must be built for " + tenantId,
                    camelContext.getRouteDefinition(RouteIds.of(tenantId, configId)));
        }
    }

    private static ImportConfiguration recurrentImport(String id, String tenantId, File directory) {
        assertTrue(directory.isDirectory() || directory.mkdirs());
        ImportConfiguration configuration = config(id, tenantId);
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        // inactive: the route is built but does not start polling during the test
        configuration.setActive(false);
        configuration.getProperties().put("source", "file://" + directory.getAbsolutePath() + "?fileName=profiles.csv");
        configuration.getProperties().put("mapping", Collections.singletonMap("0", 0));
        return configuration;
    }

    private static ExportConfiguration recurrentExport(String id, String tenantId, File directory) {
        assertTrue(directory.isDirectory() || directory.mkdirs());
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
        private final Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> toRefresh = new LinkedHashMap<>();
        private Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> lastConsumed = new LinkedHashMap<>();
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
            lastConsumed = new LinkedHashMap<>(toRefresh);
            toRefresh.clear();
            return lastConsumed;
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
            if (tenantId.equals(unswitchableTenant)) {
                throw new IllegalStateException("cannot switch to " + tenantId);
            }
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
