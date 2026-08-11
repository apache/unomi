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

import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.ws.rs.WebApplicationException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File permittedImportDir;
    private File permittedExportDir;
    private File arbitraryDir;

    private InMemoryConfigurationService<ImportConfiguration> importConfigurations;
    private InMemoryConfigurationService<ExportConfiguration> exportConfigurations;

    private ImportConfigurationServiceEndPoint importEndpoint;
    private ExportConfigurationServiceEndPoint exportEndpoint;

    @Before
    public void setUp() throws Exception {
        permittedImportDir = tmp.newFolder("permitted-import");
        permittedExportDir = tmp.newFolder("permitted-export");
        arbitraryDir = tmp.newFolder("arbitrary");

        InMemoryConfigSharingService configSharingService = new InMemoryConfigSharingService();
        configSharingService.setProperty(RouterConstants.CONFIG_ALLOWED_ENDPOINTS, "file,ftp,sftp,ftps");
        configSharingService.setProperty(RouterConstants.CONFIG_IMPORT_BASE_DIRS, permittedImportDir.getAbsolutePath());
        configSharingService.setProperty(RouterConstants.CONFIG_EXPORT_BASE_DIRS, permittedExportDir.getAbsolutePath());

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
        assertTrue("the configuration should have been stored", importConfigurations.contains("in-bounds"));
    }

    @Test
    public void savingARecurrentImportWhoseSourceIsOutsideThePermittedBaseDirsIsRefused() {
        ImportConfiguration configuration = recurrentImport(fileUri(arbitraryDir, "?fileName=profiles.csv"));

        assertRefused(() -> importEndpoint.saveConfiguration(configuration));
        assertFalse("a refused configuration must not be stored", importConfigurations.contains("in-bounds"));
    }

    @Test
    public void savingARecurrentImportWhoseFileNameOptionEscapesThePermittedBaseDirsIsRefused() {
        ImportConfiguration configuration = recurrentImport(
                fileUri(permittedImportDir, "?fileName=../" + arbitraryDir.getName() + "/profiles.csv"));

        assertRefused(() -> importEndpoint.saveConfiguration(configuration));
    }

    @Test
    public void savingAOneshotImportThatCarriesNoSourceStoresIt() {
        ImportConfiguration configuration = new ImportConfiguration();
        configuration.setItemId("oneshot");
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_ONESHOT);
        configuration.getProperties().put("mapping", Collections.singletonMap("email", 0));

        importEndpoint.saveConfiguration(configuration);

        assertTrue("a oneshot import names no endpoint and must keep being stored",
                importConfigurations.contains("oneshot"));
    }

    @Test
    public void savingARecurrentExportWhoseDestinationIsInsideThePermittedBaseDirsStoresIt() {
        ExportConfiguration saved = exportEndpoint.saveConfiguration(
                recurrentExport(fileUri(permittedExportDir, "?fileName=profiles.csv")));

        assertEquals("in-bounds", saved.getItemId());
        assertTrue("the configuration should have been stored", exportConfigurations.contains("in-bounds"));
    }

    @Test
    public void savingARecurrentExportWhoseDestinationIsOutsideThePermittedBaseDirsIsRefused() {
        ExportConfiguration configuration = recurrentExport(fileUri(arbitraryDir, "?fileName=profiles.csv"));

        assertRefused(() -> exportEndpoint.saveConfiguration(configuration));
        assertFalse("a refused configuration must not be stored", exportConfigurations.contains("in-bounds"));
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * A refused configuration answers {@code 400 Bad Request}, and says why: the caller has to be able
     * to correct the endpoint from the answer alone.
     */
    private void assertRefused(Runnable save) {
        try {
            save.run();
            fail("saving the configuration should have been refused");
        } catch (WebApplicationException e) {
            assertEquals("a refused configuration is a bad request", 400, e.getResponse().getStatus());
            assertTrue("the refusal must say why", e.getMessage() != null && !e.getMessage().trim().isEmpty());
        }
    }

    private String fileUri(File directory, String suffix) {
        return "file://" + directory.getAbsolutePath() + suffix;
    }

    private ImportConfiguration recurrentImport(String source) {
        ImportConfiguration configuration = new ImportConfiguration();
        configuration.setItemId("in-bounds");
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        configuration.getProperties().put("source", source);
        configuration.getProperties().put("mapping", Collections.singletonMap("email", 0));
        return configuration;
    }

    private ExportConfiguration recurrentExport(String destination) {
        ExportConfiguration configuration = new ExportConfiguration();
        configuration.setItemId("in-bounds");
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
        public Map<String, RouterConstants.CONFIG_CAMEL_REFRESH> consumeConfigsToBeRefresh() {
            return Collections.emptyMap();
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
