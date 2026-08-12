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
package org.apache.unomi.itests;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * A recurrent import or export configuration using the {@code file} scheme must resolve inside the base
 * directory the deployment permits — {@code config.import.baseDir} and {@code config.export.baseDir},
 * set for these tests in {@code org.apache.unomi.router.cfg}.
 *
 * <p>The unit tests decide the containment rules. What only a running Unomi can show is that the
 * settings actually reach the two places that need them: the REST layer, which refuses a configuration
 * as it is saved, and the route builders, which refuse one that is already stored. Those two read the
 * setting through different paths, and neither is exercised by a unit test.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileImportExportContainmentIT extends BaseIT {

    private static final String IMPORT_CONFIGURATION_URL = "/cxs/importConfiguration";
    private static final String EXPORT_CONFIGURATION_URL = "/cxs/exportConfiguration";

    /** Permitted by org.apache.unomi.router.cfg. */
    private static final String PERMITTED_IMPORT_DIR = "data/tmp/recurrent_import";
    private static final String PERMITTED_EXPORT_DIR = "data/tmp/recurrent_export";

    /** Not permitted by anything: a sibling of the two above. */
    private static final String ARBITRARY_DIR = "data/tmp/it-containment-arbitrary";

    private String createdImportConfigId;
    private String createdExportConfigId;

    @After
    public void cleanup() {
        if (createdImportConfigId != null) {
            importConfigurationService.delete(createdImportConfigId);
            createdImportConfigId = null;
        }
        if (createdExportConfigId != null) {
            exportConfigurationService.delete(createdExportConfigId);
            createdExportConfigId = null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Refused as it is saved
    // ---------------------------------------------------------------------------------------------

    @Test
    public void savingAnImportConfigurationOutsideThePermittedDirectoryIsRefused() throws Exception {
        ImportConfiguration configuration = recurrentImport("it-import-refused",
                fileUri(ARBITRARY_DIR, "?fileName=containment-it.csv"));

        Response response = postJson(IMPORT_CONFIGURATION_URL, configuration);

        Assert.assertEquals("a configuration whose source cannot be honoured is a bad request",
                400, response.status);
        Assert.assertFalse("the refusal must say why, so the caller can correct it",
                response.body.trim().isEmpty());
        Assert.assertNull("a refused configuration must not be stored",
                importConfigurationService.load("it-import-refused"));
    }

    @Test
    public void savingAnExportConfigurationOutsideThePermittedDirectoryIsRefused() throws Exception {
        ExportConfiguration configuration = recurrentExport("it-export-refused",
                fileUri(ARBITRARY_DIR, "?fileName=containment-it.csv"));

        Response response = postJson(EXPORT_CONFIGURATION_URL, configuration);

        Assert.assertEquals("a configuration whose destination cannot be honoured is a bad request",
                400, response.status);
        Assert.assertFalse("the refusal must say why, so the caller can correct it",
                response.body.trim().isEmpty());
        Assert.assertNull("a refused configuration must not be stored",
                exportConfigurationService.load("it-export-refused"));
    }

    @Test
    public void savingAnImportConfigurationInsideThePermittedDirectoryIsAccepted() throws Exception {
        createdImportConfigId = "it-import-accepted";
        ImportConfiguration configuration = recurrentImport(createdImportConfigId,
                fileUri(PERMITTED_IMPORT_DIR, "?fileName=containment-it.csv&consumer.delay=10m"));

        Response response = postJson(IMPORT_CONFIGURATION_URL, configuration);

        Assert.assertEquals("a configuration inside the permitted directory is legitimate",
                200, response.status);
        keepTrying("the accepted configuration should have been stored",
                () -> importConfigurationService.load(createdImportConfigId), c -> c != null, 1000, 20);
    }

    // ---------------------------------------------------------------------------------------------
    // Already stored, refused when its route is built
    // ---------------------------------------------------------------------------------------------

    @Test
    public void aStoredImportConfigurationOutsideThePermittedDirectoryIsMarkedAndConsumesNothing() throws Exception {
        File arbitraryDir = new File(ARBITRARY_DIR);
        Assert.assertTrue("could not prepare the test fixture", arbitraryDir.exists() || arbitraryDir.mkdirs());
        File waiting = new File(arbitraryDir, "must-not-be-consumed.csv");
        Files.write(waiting.toPath(), "email,firstName\nnobody@example.com,Nobody\n".getBytes(StandardCharsets.UTF_8));

        createdImportConfigId = "it-import-stored-out-of-bounds";
        ImportConfiguration configuration = recurrentImport(createdImportConfigId,
                fileUri(ARBITRARY_DIR, "?fileName=must-not-be-consumed.csv"));

        // bypasses the REST layer on purpose: this is the configuration that was already there when the
        // deployment's permitted directories changed
        importConfigurationService.save(configuration, true);

        keepTrying("a configuration whose route cannot be built must be marked, not silently dropped",
                () -> importConfigurationService.load(createdImportConfigId),
                c -> c != null && RouterConstants.CONFIG_STATUS_INVALID_ENDPOINT.equals(c.getStatus()), 1000, 30);

        Assert.assertTrue("no route may consume a source outside the permitted directory", waiting.exists());
    }

    @Test
    public void aStoredExportConfigurationOutsideThePermittedDirectoryIsMarkedAndWritesNothing() throws Exception {
        File arbitraryDir = new File(ARBITRARY_DIR);
        Assert.assertTrue("could not prepare the test fixture", arbitraryDir.exists() || arbitraryDir.mkdirs());
        File shouldNeverAppear = new File(arbitraryDir, "export-must-not-appear.csv");
        Files.deleteIfExists(shouldNeverAppear.toPath());

        createdExportConfigId = "it-export-stored-out-of-bounds";
        ExportConfiguration configuration = recurrentExport(createdExportConfigId,
                fileUri(ARBITRARY_DIR, "?fileName=export-must-not-appear.csv"));

        exportConfigurationService.save(configuration, true);

        keepTrying("a configuration whose route cannot be built must be marked, not silently dropped",
                () -> exportConfigurationService.load(createdExportConfigId),
                c -> c != null && RouterConstants.CONFIG_STATUS_INVALID_ENDPOINT.equals(c.getStatus()), 1000, 30);

        Assert.assertFalse("no route may write to a destination outside the permitted directory",
                shouldNeverAppear.exists());
    }

    @Test
    public void aMarkedConfigurationRecoversOnItsOwnWhenItsEndpointBecomesAcceptableAgain() throws Exception {
        createdImportConfigId = "it-import-recovering";
        ImportConfiguration configuration = recurrentImport(createdImportConfigId,
                fileUri(ARBITRARY_DIR, "?fileName=containment-it.csv"));
        importConfigurationService.save(configuration, true);

        ImportConfiguration marked = keepTrying("the configuration should first be marked",
                () -> importConfigurationService.load(createdImportConfigId),
                c -> c != null && RouterConstants.CONFIG_STATUS_INVALID_ENDPOINT.equals(c.getStatus()), 1000, 30);

        // what operations would do: put the endpoint back where it is permitted
        marked.getProperties().put("source", fileUri(PERMITTED_IMPORT_DIR, "?fileName=containment-it.csv&consumer.delay=10m"));
        importConfigurationService.save(marked, true);

        keepTrying("restoring the endpoint must clear the mark without anyone touching the status",
                () -> importConfigurationService.load(createdImportConfigId),
                c -> c != null && c.getStatus() == null, 1000, 30);
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    private String fileUri(String directory, String suffix) {
        return "file://" + new File(directory).getAbsolutePath() + suffix;
    }

    /**
     * Executes the request against the shared client rather than through {@code executeHttpRequest},
     * which consumes the response body to log it whenever the status is not {@code 200} — these tests
     * need to read that body themselves.
     */
    private Response postJson(String url, Object body) throws Exception {
        HttpPost request = new HttpPost(getFullUrl(url));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            return new Response(response.getStatusLine().getStatusCode(),
                    response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity()));
        }
    }

    private static final class Response {
        private final int status;
        private final String body;

        private Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private ImportConfiguration recurrentImport(String itemId, String source) {
        ImportConfiguration configuration = new ImportConfiguration();
        configuration.setItemId(itemId);
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        configuration.setColumnSeparator(",");
        configuration.setActive(true);

        Map<String, Integer> mapping = new HashMap<>();
        mapping.put("email", 0);
        mapping.put("firstName", 1);
        configuration.getProperties().put("mapping", mapping);
        configuration.getProperties().put("source", source);
        configuration.setMergingProperty("email");
        return configuration;
    }

    private ExportConfiguration recurrentExport(String itemId, String destination) {
        ExportConfiguration configuration = new ExportConfiguration();
        configuration.setItemId(itemId);
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        configuration.setColumnSeparator(";");
        configuration.setMultiValueDelimiter("()");
        configuration.setMultiValueSeparator(";");
        configuration.setActive(true);

        Map<String, String> mapping = new HashMap<>();
        mapping.put("0", "firstName");
        configuration.getProperties().put("mapping", mapping);
        configuration.getProperties().put("segment", "itContainmentSegment");
        configuration.getProperties().put("period", "1m");
        configuration.getProperties().put("destination", destination);
        return configuration;
    }
}
