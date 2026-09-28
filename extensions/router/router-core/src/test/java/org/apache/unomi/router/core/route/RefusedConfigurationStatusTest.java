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

import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.ProfileToImport;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.apache.unomi.router.core.route.RouterTestFixtures.TENANT;
import static org.apache.unomi.router.core.route.RouterTestFixtures.fileUri;
import static org.apache.unomi.router.core.route.RouterTestFixtures.noOpProfileService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The permitted base directories are an operational setting, and the configurations are user data:
 * the two drift apart. A configuration that was legitimate when it was created can find itself
 * outside the permitted directories after the deployment is reconfigured, and its route then stops
 * being built.
 *
 * <p>Leaving that silent is what makes it painful — the screen keeps showing the configuration as
 * running, and the only trace is a log line nobody reads. So a configuration whose endpoint is
 * refused while its route is being built is recorded as failed, and stays in the store: it is for
 * whoever owns it to correct it or remove it, and they can only do that if they can see it.
 *
 * <p>Recording it must not schedule the configuration for a route refresh — that would have the
 * refresh rebuild the route, refuse it again, and save it again, indefinitely.
 */
public class RefusedConfigurationStatusTest {

    private static final String DEFAULT_ALLOWED_ENDPOINTS = "file,ftp,sftp,ftps";

    private static final Map<String, String> NO_KAFKA = new HashMap<>();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private DefaultCamelContext camelContext;

    private File importRoot;
    private File exportRoot;
    private File permittedImportDir;
    private File permittedExportDir;
    private File arbitraryDir;

    private RecordingConfigurationService<ImportConfiguration> importConfigurations;
    private RecordingConfigurationService<ExportConfiguration> exportConfigurations;

    /** The tenant the fake context manager is currently running as, or {@code null} outside any. */
    private String currentTenant;

    /** Whether the fake context manager is running as the system subject. */
    private boolean systemSubject;

    /** Whether the subject the tenant context was built from may save, or {@code null} outside any. */
    private Boolean tenantContextMaySave;

    @Before
    public void setUp() throws Exception {
        importRoot = tmp.newFolder("permitted-import");
        permittedImportDir = new File(importRoot, TENANT);
        assertTrue(permittedImportDir.mkdirs());
        exportRoot = tmp.newFolder("permitted-export");
        permittedExportDir = new File(exportRoot, TENANT);
        assertTrue(permittedExportDir.mkdirs());
        arbitraryDir = tmp.newFolder("arbitrary");
        camelContext = new DefaultCamelContext();
        importConfigurations = new RecordingConfigurationService<>();
        exportConfigurations = new RecordingConfigurationService<>();
    }

    @After
    public void tearDown() throws Exception {
        camelContext.stop();
    }

    @Test
    public void aRefusedImportConfigurationIsRecordedAsFailed() throws Exception {
        ImportConfiguration configuration = recurrentImport(fileUri(arbitraryDir, "?fileName=profiles.csv"));

        addImportRoutes(configuration);

        assertNull("no route should have been built", camelContext.getRouteDefinition("out-of-bounds"));
        assertEquals("the configuration should be recorded as failed",
                RouterConstants.CONFIG_STATUS_INVALID_ENDPOINT, configuration.getStatus());
        assertTrue("the configuration should have been saved so the failure is visible",
                importConfigurations.contains("out-of-bounds"));
    }

    @Test
    public void aRefusedExportConfigurationIsRecordedAsFailed() throws Exception {
        ExportConfiguration configuration = recurrentExport(fileUri(arbitraryDir, "?fileName=profiles.csv"));

        addExportRoutes(configuration);

        assertNull("no route should have been built", camelContext.getRouteDefinition("out-of-bounds"));
        assertEquals("the configuration should be recorded as failed",
                RouterConstants.CONFIG_STATUS_INVALID_ENDPOINT, configuration.getStatus());
        assertTrue("the configuration should have been saved so the failure is visible",
                exportConfigurations.contains("out-of-bounds"));
    }

    @Test
    public void aRefusedConfigurationIsRecordedAsItsOwnTenant() throws Exception {
        // Start-up builds every tenant's routes in one pass; the store refuses a save whose current
        // tenant is not the configuration's, so the mark has to be written as that tenant.
        ImportConfiguration configuration = recurrentImport(fileUri(arbitraryDir, "?fileName=profiles.csv"));
        configuration.setTenantId("beta");
        importConfigurations.currentTenant = () -> currentTenant;

        addImportRoutes(recordingContextManager(), configuration);

        assertTrue("the refusal should have been recorded", importConfigurations.contains("out-of-bounds"));
        assertEquals("the mark must be written as the configuration's tenant, whatever the batch runs as",
                "beta", importConfigurations.tenantAtLastSave);
        assertNull("the batch's own context must be restored afterwards", currentTenant);
    }

    @Test
    public void aRefusedConfigurationIsRecordedWithThePermissionToSaveIt() throws Exception {
        // The route refresh runs on a thread that has no subject. The tenant context takes the
        // permissions of the subject it is built from, so the system subject has to be there first.
        ImportConfiguration configuration = recurrentImport(fileUri(arbitraryDir, "?fileName=profiles.csv"));
        importConfigurations.maySave = () -> tenantContextMaySave;

        addImportRoutes(recordingContextManager(), configuration);

        assertEquals("the tenant context must be entered from the system subject, not the other way round",
                Boolean.TRUE, importConfigurations.maySaveAtLastSave);
    }

    @Test
    public void aContextThatCannotBeSwitchedDoesNotCostTheBatchItsOtherRoutes() throws Exception {
        ExecutionContextManager noSystemSubject = (ExecutionContextManager) Proxy.newProxyInstance(
                ExecutionContextManager.class.getClassLoader(),
                new Class<?>[]{ExecutionContextManager.class},
                (proxy, method, args) -> {
                    throw new SecurityException("Failed to obtain system subject");
                });

        addImportRoutes(noSystemSubject, recurrentImport(fileUri(arbitraryDir, "?fileName=profiles.csv")),
                inBoundsImport("in-bounds"));

        assertNull("the refused configuration still gets no route", camelContext.getRouteDefinition("out-of-bounds"));
        assertNotNull("failing to switch context must not cost the other configurations their routes",
                camelContext.getRouteDefinition("in-bounds"));
    }

    @Test
    public void aStoreThatCannotRecordTheRefusalDoesNotCostTheBatchItsOtherRoutes() throws Exception {
        importConfigurations.unwritable = true;

        addImportRoutes(recurrentImport(fileUri(arbitraryDir, "?fileName=profiles.csv")),
                inBoundsImport("in-bounds"));

        assertNull("the refused configuration still gets no route", camelContext.getRouteDefinition("out-of-bounds"));
        assertNotNull("failing to record the refusal must not cost the other configurations their routes",
                camelContext.getRouteDefinition("in-bounds"));
    }

    @Test
    public void recordingARefusedImportConfigurationDoesNotScheduleARouteRefresh() throws Exception {
        addImportRoutes(recurrentImport(fileUri(arbitraryDir, "?fileName=profiles.csv")));

        assertFalse("refreshing the route would refuse and save it again, without end",
                importConfigurations.lastSaveAskedForARouteRefresh);
    }

    @Test
    public void recordingARefusedExportConfigurationDoesNotScheduleARouteRefresh() throws Exception {
        addExportRoutes(recurrentExport(fileUri(arbitraryDir, "?fileName=profiles.csv")));

        assertFalse("refreshing the route would refuse and save it again, without end",
                exportConfigurations.lastSaveAskedForARouteRefresh);
    }

    @Test
    public void anImportConfigurationRecoversWhenItsEndpointBecomesAcceptableAgain() throws Exception {
        ImportConfiguration configuration = recurrentImport(fileUri(permittedImportDir, "?fileName=profiles.csv"));
        configuration.setStatus(RouterConstants.CONFIG_STATUS_INVALID_ENDPOINT);

        addImportRoutes(configuration);

        assertNull("restoring the permitted directories must bring the configuration back on its own",
                configuration.getStatus());
        assertTrue("the recovery must be persisted", importConfigurations.contains("out-of-bounds"));
    }

    @Test
    public void anExportConfigurationRecoversWhenItsEndpointBecomesAcceptableAgain() throws Exception {
        ExportConfiguration configuration = recurrentExport(fileUri(permittedExportDir, "?fileName=profiles.csv"));
        configuration.setStatus(RouterConstants.CONFIG_STATUS_INVALID_ENDPOINT);

        addExportRoutes(configuration);

        assertNull("restoring the permitted directories must bring the configuration back on its own",
                configuration.getStatus());
        assertTrue("the recovery must be persisted", exportConfigurations.contains("out-of-bounds"));
    }

    @Test
    public void aConfigurationThatFailedItsLastRunKeepsThatStatusWhenItsRouteIsRebuilt() throws Exception {
        ImportConfiguration configuration = recurrentImport(fileUri(permittedImportDir, "?fileName=profiles.csv"));
        configuration.setStatus(RouterConstants.CONFIG_STATUS_COMPLETE_ERRORS);

        addImportRoutes(configuration);

        assertEquals("a failed run is a different matter, and its record must survive",
                RouterConstants.CONFIG_STATUS_COMPLETE_ERRORS, configuration.getStatus());
    }

    @Test
    public void anAcceptedImportConfigurationIsLeftAlone() throws Exception {
        ImportConfiguration configuration = recurrentImport(fileUri(permittedImportDir, "?fileName=profiles.csv"));
        configuration.setItemId("in-bounds");

        addImportRoutes(configuration);

        assertNull("an accepted configuration keeps the status it had", configuration.getStatus());
        assertFalse("an accepted configuration is not saved while its route is built",
                importConfigurations.contains("in-bounds"));
    }

    @Test
    public void anAcceptedExportConfigurationIsLeftAlone() throws Exception {
        ExportConfiguration configuration = recurrentExport(fileUri(permittedExportDir, "?fileName=profiles.csv"));
        configuration.setItemId("in-bounds");

        addExportRoutes(configuration);

        assertNull("an accepted configuration keeps the status it had", configuration.getStatus());
        assertFalse("an accepted configuration is not saved while its route is built",
                exportConfigurations.contains("in-bounds"));
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    private ImportConfiguration inBoundsImport(String itemId) {
        ImportConfiguration configuration = recurrentImport(fileUri(permittedImportDir, "?fileName=profiles.csv"));
        configuration.setItemId(itemId);
        return configuration;
    }

    private ImportConfiguration recurrentImport(String source) {
        return RouterTestFixtures.recurrentImport("out-of-bounds", source);
    }

    private ExportConfiguration recurrentExport(String destination) {
        return RouterTestFixtures.recurrentExport("out-of-bounds", destination);
    }

    private void addImportRoutes(ImportConfiguration... configurations) throws Exception {
        addImportRoutes(null, configurations);
    }

    private void addImportRoutes(ExecutionContextManager contextManager, ImportConfiguration... configurations) throws Exception {
        ProfileImportFromSourceRouteBuilder builder =
                new ProfileImportFromSourceRouteBuilder(NO_KAFKA, RouterConstants.CONFIG_TYPE_NOBROKER);
        builder.setImportConfigurationList(java.util.Arrays.asList(configurations));
        builder.setImportConfigurationService(importConfigurations);
        builder.setProfileService(noOpProfileService());
        builder.setJacksonDataFormat(new JacksonDataFormat(ProfileToImport.class));
        builder.setAllowedEndpoints(DEFAULT_ALLOWED_ENDPOINTS);
        builder.setPermittedImportBaseDirs(importRoot.getAbsolutePath());
        builder.setExecutionContextManager(contextManager);
        builder.setContext(camelContext);
        camelContext.addRoutes(builder);
    }

    /**
     * Runs an operation as the tenant it is asked to, and remembers which one that is for as long as
     * the operation lasts, the way the real manager swaps the thread's context. {@code executeAsSystem}
     * installs the system subject for as long as its operation lasts, and {@code executeAsTenant}
     * builds its context from the subject that is there when it is called, as the real ones do: a
     * tenant context entered without the system subject carries no permission to save.
     */
    private ExecutionContextManager recordingContextManager() {
        return (ExecutionContextManager) Proxy.newProxyInstance(
                ExecutionContextManager.class.getClassLoader(),
                new Class<?>[]{ExecutionContextManager.class},
                (proxy, method, args) -> {
                    if ("executeAsSystem".equals(method.getName())) {
                        boolean previous = systemSubject;
                        systemSubject = true;
                        try {
                            return invokeContextOperation(args[0]);
                        } finally {
                            systemSubject = previous;
                        }
                    }
                    if (!"executeAsTenant".equals(method.getName())) {
                        return null;
                    }
                    String previous = currentTenant;
                    Boolean previousMaySave = tenantContextMaySave;
                    currentTenant = (String) args[0];
                    tenantContextMaySave = systemSubject;
                    try {
                        return invokeContextOperation(args[1]);
                    } finally {
                        currentTenant = previous;
                        tenantContextMaySave = previousMaySave;
                    }
                });
    }

    private static Object invokeContextOperation(Object operation) {
        if (operation instanceof Runnable) {
            ((Runnable) operation).run();
            return null;
        }
        return ((Supplier<?>) operation).get();
    }

    private void addExportRoutes(ExportConfiguration... configurations) throws Exception {
        ProfileExportCollectRouteBuilder builder =
                new ProfileExportCollectRouteBuilder(NO_KAFKA, RouterConstants.CONFIG_TYPE_NOBROKER);
        builder.setExportConfigurationList(java.util.Arrays.asList(configurations));
        builder.setExportConfigurationService(exportConfigurations);
        builder.setJacksonDataFormat(new JacksonDataFormat(ProfileToImport.class));
        builder.setAllowedEndpoints(DEFAULT_ALLOWED_ENDPOINTS);
        builder.setPermittedExportBaseDirs(exportRoot.getAbsolutePath());
        builder.setContext(camelContext);
        camelContext.addRoutes(builder);
    }

    /**
     * Stores what it is given, and remembers whether the last save asked for the running route to be
     * refreshed — a refused configuration must not, or the refresh loops.
     */
    private static final class RecordingConfigurationService<T> implements ImportExportConfigurationService<T> {

        private final Map<String, T> stored = new LinkedHashMap<>();

        private boolean lastSaveAskedForARouteRefresh;

        /** Where the store reads the current tenant from; the real one refuses a save from another. */
        private Supplier<String> currentTenant = () -> null;

        private String tenantAtLastSave;

        /** Whether the current context may save; the real store refuses a save from one that may not. */
        private Supplier<Boolean> maySave = () -> null;

        private Boolean maySaveAtLastSave;

        /** Stands in for a store that cannot be written to -- Elasticsearch unreachable at start-up. */
        private boolean unwritable;

        boolean contains(String configId) {
            return stored.containsKey(configId);
        }

        @Override
        public List<T> getAll() {
            return new ArrayList<>(stored.values());
        }

        @Override
        public T load(String configId) {
            return stored.get(configId);
        }

        @Override
        public T save(T configuration, boolean updateRunningRoute) {
            if (unwritable) {
                throw new IllegalStateException("the store is unreachable");
            }
            lastSaveAskedForARouteRefresh = updateRunningRoute;
            tenantAtLastSave = currentTenant.get();
            maySaveAtLastSave = maySave.get();
            stored.put(itemIdOf(configuration), configuration);
            return configuration;
        }

        @Override
        public void delete(String configId) {
            stored.remove(configId);
        }

        @Override
        public Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> consumeConfigsToBeRefresh() {
            return Collections.emptyMap();
        }

        @Override
        public void requeueForRefresh(String tenantId, String configId, RouterConstants.CONFIG_CAMEL_REFRESH refreshType) {
        }

        private String itemIdOf(T configuration) {
            return configuration instanceof ImportConfiguration
                    ? ((ImportConfiguration) configuration).getItemId()
                    : ((ExportConfiguration) configuration).getItemId();
        }
    }
}
