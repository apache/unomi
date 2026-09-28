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
package org.apache.unomi.router.rest;

import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.ws.rs.WebApplicationException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A configuration whose endpoint cannot be honoured must be refused when it is saved, not silently
 * accepted and then dropped when its route fails to build.
 *
 * <p>Route construction happens asynchronously, well after the REST call has answered, so a
 * configuration that only fails there is stored, answered {@code 200}, and leaves nothing but a log
 * line behind — the caller cannot tell it apart from a configuration that works. Validating at save
 * time gives the caller a synchronous, actionable answer, and keeps the rejected configuration out
 * of the store.
 *
 * <p>Only configurations that name an endpoint are concerned: a oneshot import carries no source, its
 * file being uploaded separately, and must keep being saved.
 */
public class ConfigurationEndpointValidationTest {

    @TempDir
    File tmp;

    private static final String TENANT = "acme";

    private File importRoot;
    private File exportRoot;
    private File permittedImportDir;
    private File permittedExportDir;
    private File arbitraryDir;

    private InMemoryConfigurationService<ImportConfiguration> importConfigurations;
    private InMemoryConfigurationService<ExportConfiguration> exportConfigurations;

    private ImportConfigurationServiceEndPoint importEndpoint;
    private ExportConfigurationServiceEndPoint exportEndpoint;

    @BeforeEach
    public void setUp() throws Exception {
        importRoot = newFolder("permitted-import");
        permittedImportDir = new File(importRoot, TENANT);
        assertTrue(permittedImportDir.mkdirs());
        exportRoot = newFolder("permitted-export");
        permittedExportDir = new File(exportRoot, TENANT);
        assertTrue(permittedExportDir.mkdirs());
        arbitraryDir = newFolder("arbitrary");

        InMemoryConfigSharingService configSharingService = new InMemoryConfigSharingService();
        configSharingService.setProperty(RouterConstants.CONFIG_ALLOWED_ENDPOINTS, "file,ftp,sftp,ftps");
        configSharingService.setProperty(RouterConstants.CONFIG_IMPORT_BASE_DIRS, importRoot.getAbsolutePath());
        configSharingService.setProperty(RouterConstants.CONFIG_EXPORT_BASE_DIRS, exportRoot.getAbsolutePath());

        importConfigurations = new InMemoryConfigurationService<>();
        importEndpoint = new ImportConfigurationServiceEndPoint();
        importEndpoint.setImportConfigurationService(importConfigurations);
        importEndpoint.setConfigSharingService(configSharingService);

        exportConfigurations = new InMemoryConfigurationService<>();
        exportEndpoint = new ExportConfigurationServiceEndPoint();
        exportEndpoint.setExportConfigurationService(exportConfigurations);
        exportEndpoint.setConfigSharingService(configSharingService);
    }

    @Test
    public void savingARecurrentImportWhoseSourceIsInsideThePermittedBaseDirsStoresIt() {
        ImportConfiguration saved = importEndpoint.saveConfiguration(
                recurrentImport(fileUri(permittedImportDir, "?fileName=profiles.csv")));

        assertEquals("in-bounds", saved.getItemId());
        assertTrue(importConfigurations.contains("in-bounds"),
                "the configuration should have been stored");
    }

    @Test
    public void savingARecurrentImportBeforeTheRouterPublishedItsSettingsIsNotRefused() throws Exception {
        // the router's Camel context has not started yet, so it has published nothing
        ImportConfigurationServiceEndPoint starting = new ImportConfigurationServiceEndPoint();
        starting.setImportConfigurationService(importConfigurations);
        starting.setConfigSharingService(new InMemoryConfigSharingService());

        ImportConfiguration configuration = recurrentImport(fileUri(permittedImportDir, "?fileName=profiles.csv"));

        assertUnavailable(() -> starting.saveConfiguration(configuration));
        assertFalse(importConfigurations.contains("in-bounds"),
                "nothing is stored while the endpoint cannot be judged");
    }

    @Test
    public void savingARecurrentImportWhoseRemoteSourceStagesDownloadsOutsideThePermittedBaseDirsIsRefused() {
        // ftp is an allowed scheme, but localWorkDirectory names a local directory all the same
        ImportConfiguration configuration = recurrentImport("ftp://ftp.example.com/profiles"
                + "?fileName=profiles.csv&localWorkDirectory=" + arbitraryDir.getAbsolutePath());

        assertRefused(() -> importEndpoint.saveConfiguration(configuration));
        assertFalse(importConfigurations.contains("in-bounds"),
                "a refused configuration must not be stored");
    }

    @Test
    public void savingARecurrentImportBeforeThePermittedDirectoriesArePublishedIsNotRefused() throws Exception {
        // the scheme allow-list is there, the directories are not: still nothing to judge against
        InMemoryConfigSharingService partial = new InMemoryConfigSharingService();
        partial.setProperty(RouterConstants.CONFIG_ALLOWED_ENDPOINTS, "file,ftp,sftp,ftps");
        ImportConfigurationServiceEndPoint starting = new ImportConfigurationServiceEndPoint();
        starting.setImportConfigurationService(importConfigurations);
        starting.setConfigSharingService(partial);

        ImportConfiguration configuration = recurrentImport(fileUri(permittedImportDir, "?fileName=profiles.csv"));

        assertUnavailable(() -> starting.saveConfiguration(configuration));
    }

    @Test
    public void savingARecurrentExportBeforeTheRouterPublishedItsSettingsIsNotRefused() throws Exception {
        ExportConfigurationServiceEndPoint starting = new ExportConfigurationServiceEndPoint();
        starting.setExportConfigurationService(exportConfigurations);
        starting.setConfigSharingService(new InMemoryConfigSharingService());

        ExportConfiguration configuration = recurrentExport(fileUri(permittedExportDir, "?fileName=profiles.csv"));

        assertUnavailable(() -> starting.saveConfiguration(configuration));
        assertFalse(exportConfigurations.contains("in-bounds"),
                "nothing is stored while the endpoint cannot be judged");
    }

    @Test
    public void savingARecurrentImportWhoseSourceIsOutsideThePermittedBaseDirsIsRefused() {
        ImportConfiguration configuration = recurrentImport(fileUri(arbitraryDir, "?fileName=profiles.csv"));

        assertRefused(() -> importEndpoint.saveConfiguration(configuration));
        assertFalse(importConfigurations.contains("in-bounds"),
                "a refused configuration must not be stored");
    }

    @Test
    public void savingARecurrentImportWhoseFileNameOptionEscapesThePermittedBaseDirsIsRefused() {
        ImportConfiguration configuration = recurrentImport(
                fileUri(permittedImportDir, "?fileName=../" + arbitraryDir.getName() + "/profiles.csv"));

        assertRefused(() -> importEndpoint.saveConfiguration(configuration));
    }

    @Test
    public void savingARecurrentImportWhoseSourceIsNotAUsablePathIsRefused() {
        ImportConfiguration configuration = recurrentImport(fileUri(permittedImportDir, "?fileName=profiles%00.csv"));

        assertRefused(() -> importEndpoint.saveConfiguration(configuration));
        assertFalse(importConfigurations.contains("in-bounds"),
                "a refused configuration must not be stored");
    }

    @Test
    public void savingARecurrentImportInAnotherTenantsDirectoryIsRefused() {
        File otherTenant = new File(importRoot, "beta");
        assertTrue(otherTenant.mkdirs());

        assertRefused(() -> importEndpoint.saveConfiguration(
                recurrentImport(fileUri(otherTenant, "?fileName=profiles.csv"))));
        assertFalse(importConfigurations.contains("in-bounds"),
                "a refused configuration must not be stored");
    }

    @Test
    public void savingARecurrentImportWithNoTenantIsRefused() {
        ImportConfiguration configuration = recurrentImport(fileUri(permittedImportDir, "?fileName=profiles.csv"));
        configuration.setTenantId(null);

        assertRefused(() -> importEndpoint.saveConfiguration(configuration));
        assertFalse(importConfigurations.contains("in-bounds"),
                "a configuration with no tenant must not fall back to the shared directory");
    }

    @Test
    public void savingARecurrentImportUsesTheCallerTenantWhenTheBodyNamesNone() {
        ExecutionContextManager contexts = mock(ExecutionContextManager.class);
        when(contexts.getCurrentContext()).thenReturn(new ExecutionContext(TENANT, null, null));
        importEndpoint.setExecutionContextManager(contexts);

        ImportConfiguration configuration = recurrentImport(fileUri(permittedImportDir, "?fileName=profiles.csv"));
        configuration.setTenantId(null);

        ImportConfiguration saved = importEndpoint.saveConfiguration(configuration);

        assertEquals("in-bounds", saved.getItemId());
        assertTrue(importConfigurations.contains("in-bounds"));
    }

    @Test
    public void savingARecurrentImportIsJudgedAgainstTheCallerTenantWhateverTenantTheBodyNames() {
        ExecutionContextManager contexts = mock(ExecutionContextManager.class);
        when(contexts.getCurrentContext()).thenReturn(new ExecutionContext(TENANT, null, null));
        importEndpoint.setExecutionContextManager(contexts);
        File otherTenant = new File(importRoot, "beta");
        assertTrue(otherTenant.mkdirs());

        ImportConfiguration configuration = recurrentImport(fileUri(otherTenant, "?fileName=profiles.csv"));
        configuration.setTenantId("beta");

        assertRefused(() -> importEndpoint.saveConfiguration(configuration));
        assertFalse(importConfigurations.contains("in-bounds"),
                "acme may not have a configuration judged against beta's directory by naming beta");
    }

    @Test
    public void savingARecurrentImportAsTheSystemHonoursTheTenantTheBodyNames() {
        ExecutionContextManager contexts = mock(ExecutionContextManager.class);
        when(contexts.getCurrentContext()).thenReturn(new ExecutionContext(ExecutionContext.SYSTEM_TENANT, null, null));
        importEndpoint.setExecutionContextManager(contexts);
        File otherTenant = new File(importRoot, "beta");
        assertTrue(otherTenant.mkdirs());

        ImportConfiguration configuration = recurrentImport(fileUri(otherTenant, "?fileName=profiles.csv"));
        configuration.setTenantId("beta");

        importEndpoint.saveConfiguration(configuration);

        assertTrue(importConfigurations.contains("in-bounds"),
                "the system may configure any tenant, judged against that tenant's directory");
    }

    @Test
    public void savingARecurrentImportWhoseTenantIdIsAPathIsRefused() {
        File otherTenant = new File(importRoot, "beta");
        assertTrue(otherTenant.mkdirs());
        ImportConfiguration configuration = recurrentImport(fileUri(otherTenant, "?fileName=profiles.csv"));
        configuration.setTenantId("acme/../beta");

        assertRefused(() -> importEndpoint.saveConfiguration(configuration));
        assertFalse(importConfigurations.contains("in-bounds"),
                "a tenant id that is a path must not be resolved against the shared base directory");
    }

    @Test
    public void savingARecurrentImportWhoseSourceCarriesAPropertyPlaceholderIsRefused() {
        assertRefused(() -> importEndpoint.saveConfiguration(
                recurrentImport(fileUri(permittedImportDir, "/{{env:UNOMI_UNSET_FOR_TEST:../beta}}?fileName=profiles.csv"))));
        assertFalse(importConfigurations.contains("in-bounds"),
                "Camel expands a placeholder after validation, so the configuration cannot be stored");
    }

    @Test
    public void savingAOneshotImportThatCarriesNoSourceStoresIt() {
        ImportConfiguration configuration = new ImportConfiguration();
        configuration.setItemId("oneshot");
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_ONESHOT);
        configuration.getProperties().put("mapping", Collections.singletonMap("email", 0));

        importEndpoint.saveConfiguration(configuration);

        assertTrue(importConfigurations.contains("oneshot"),
                "a oneshot import names no endpoint and must keep being stored");
    }

    @Test
    public void savingARecurrentExportWhoseDestinationIsInsideThePermittedBaseDirsStoresIt() {
        ExportConfiguration saved = exportEndpoint.saveConfiguration(
                recurrentExport(fileUri(permittedExportDir, "?fileName=profiles.csv")));

        assertEquals("in-bounds", saved.getItemId());
        assertTrue(exportConfigurations.contains("in-bounds"),
                "the configuration should have been stored");
    }

    @Test
    public void savingARecurrentExportWhoseDestinationIsOutsideThePermittedBaseDirsIsRefused() {
        ExportConfiguration configuration = recurrentExport(fileUri(arbitraryDir, "?fileName=profiles.csv"));

        assertRefused(() -> exportEndpoint.saveConfiguration(configuration));
        assertFalse(exportConfigurations.contains("in-bounds"),
                "a refused configuration must not be stored");
    }

    @Test
    public void savingARecurrentExportInAnotherTenantsDirectoryIsRefused() {
        File otherTenant = new File(exportRoot, "beta");
        assertTrue(otherTenant.mkdirs());

        assertRefused(() -> exportEndpoint.saveConfiguration(
                recurrentExport(fileUri(otherTenant, "?fileName=profiles.csv"))));
        assertFalse(exportConfigurations.contains("in-bounds"), "a refused configuration must not be stored");
    }

    @Test
    public void savingARecurrentExportIsJudgedAgainstTheExportDirectoriesNotTheImportOnes() {
        assertRefused(() -> exportEndpoint.saveConfiguration(
                recurrentExport(fileUri(permittedImportDir, "?fileName=profiles.csv"))));
        assertFalse(exportConfigurations.contains("in-bounds"), "a refused configuration must not be stored");
    }

    @Test
    public void savingARecurrentExportWithNoTenantIsRefused() {
        ExportConfiguration configuration = recurrentExport(fileUri(permittedExportDir, "?fileName=profiles.csv"));
        configuration.setTenantId(null);

        assertRefused(() -> exportEndpoint.saveConfiguration(configuration));
        assertFalse(exportConfigurations.contains("in-bounds"),
                "a configuration with no tenant must not fall back to the shared directory");
    }

    @Test
    public void savingARecurrentExportUsesTheCallerTenantWhenTheBodyNamesNone() {
        callerIs(TENANT);
        ExportConfiguration configuration = recurrentExport(fileUri(permittedExportDir, "?fileName=profiles.csv"));
        configuration.setTenantId(null);

        exportEndpoint.saveConfiguration(configuration);

        assertTrue(exportConfigurations.contains("in-bounds"));
    }

    @Test
    public void savingARecurrentExportIsJudgedAgainstTheCallerTenantWhateverTenantTheBodyNames() {
        callerIs(TENANT);
        File otherTenant = new File(exportRoot, "beta");
        assertTrue(otherTenant.mkdirs());
        ExportConfiguration configuration = recurrentExport(fileUri(otherTenant, "?fileName=profiles.csv"));
        configuration.setTenantId("beta");

        assertRefused(() -> exportEndpoint.saveConfiguration(configuration));
        assertFalse(exportConfigurations.contains("in-bounds"),
                "acme may not have a configuration judged against beta's directory by naming beta");
    }

    @Test
    public void savingARecurrentExportAsTheSystemHonoursTheTenantTheBodyNames() {
        callerIs(ExecutionContext.SYSTEM_TENANT);
        File otherTenant = new File(exportRoot, "beta");
        assertTrue(otherTenant.mkdirs());
        ExportConfiguration configuration = recurrentExport(fileUri(otherTenant, "?fileName=profiles.csv"));
        configuration.setTenantId("beta");

        exportEndpoint.saveConfiguration(configuration);

        assertTrue(exportConfigurations.contains("in-bounds"),
                "the system may configure any tenant, judged against that tenant's directory");
    }

    @Test
    public void savingARecurrentExportWhoseDestinationCarriesAPropertyPlaceholderIsRefused() {
        assertRefused(() -> exportEndpoint.saveConfiguration(
                recurrentExport(fileUri(permittedExportDir, "/{{env:UNOMI_UNSET_FOR_TEST:../beta}}?fileName=profiles.csv"))));
        assertFalse(exportConfigurations.contains("in-bounds"),
                "Camel expands a placeholder after validation, so the configuration cannot be stored");
    }

    @Test
    public void savingARecurrentImportWhoseSchemeIsNotInTheCaseItIsAllowedInIsRefused() {
        assertRefused(() -> importEndpoint.saveConfiguration(
                recurrentImport("FILE://" + permittedImportDir.getAbsolutePath() + "?fileName=profiles.csv")));
        assertFalse(importConfigurations.contains("in-bounds"),
                "Camel has no component of that name, so the route of this configuration could never be built");
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    private void assertUnavailable(Runnable save) {
        WebApplicationException e = assertThrows(WebApplicationException.class, save::run,
                "saving the configuration should not have been answered yet");
        assertEquals(503, e.getResponse().getStatus(),
                "settings that are not published yet make the service unavailable, not the configuration wrong");
    }

    /**
     * A refused configuration answers {@code 400 Bad Request}, and says why: the caller has to be able
     * to correct the endpoint from the answer alone.
     */
    private void assertRefused(Runnable save) {
        WebApplicationException e = assertThrows(WebApplicationException.class, save::run,
                "saving the configuration should have been refused");
        assertEquals(400, e.getResponse().getStatus(), "a refused configuration is a bad request");
        assertTrue(e.getMessage() != null && !e.getMessage().trim().isEmpty(), "the refusal must say why");
    }

    private File newFolder(String name) {
        File folder = new File(tmp, name);
        assertTrue(folder.mkdirs());
        return folder;
    }

    private void callerIs(String tenantId) {
        ExecutionContextManager contexts = mock(ExecutionContextManager.class);
        when(contexts.getCurrentContext()).thenReturn(new ExecutionContext(tenantId, null, null));
        exportEndpoint.setExecutionContextManager(contexts);
    }

    private String fileUri(File directory, String suffix) {
        return "file://" + directory.getAbsolutePath() + suffix;
    }

    private ImportConfiguration recurrentImport(String source) {
        ImportConfiguration configuration = new ImportConfiguration();
        configuration.setItemId("in-bounds");
        configuration.setTenantId(TENANT);
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        configuration.getProperties().put("source", source);
        configuration.getProperties().put("mapping", Collections.singletonMap("email", 0));
        return configuration;
    }

    private ExportConfiguration recurrentExport(String destination) {
        ExportConfiguration configuration = new ExportConfiguration();
        configuration.setItemId("in-bounds");
        configuration.setTenantId(TENANT);
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        configuration.getProperties().put("destination", destination);
        configuration.getProperties().put("mapping", Collections.singletonMap("0", "firstName"));
        configuration.getProperties().put("segment", "exportSegment");
        configuration.getProperties().put("period", "1m");
        return configuration;
    }

    /**
     * Stores what it is given, so that a test can tell a configuration that was persisted from one that
     * was refused before reaching the store.
     */
    private static final class InMemoryConfigurationService<T> implements ImportExportConfigurationService<T> {

        private final Map<String, T> stored = new LinkedHashMap<>();

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

    private static final class InMemoryConfigSharingService implements ConfigSharingService {

        private final Map<String, Object> properties = new HashMap<>();

        @Override
        public Object getProperty(String name) {
            return properties.get(name);
        }

        @Override
        public Object setProperty(String name, Object value) {
            return properties.put(name, value);
        }

        @Override
        public boolean hasProperty(String name) {
            return properties.containsKey(name);
        }

        @Override
        public Object removeProperty(String name) {
            return properties.remove(name);
        }

        @Override
        public Set<String> getPropertyNames() {
            return properties.keySet();
        }
    }
}
