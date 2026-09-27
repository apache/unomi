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

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.router.api.RouterConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The one-shot upload must land where the Camel route reads it: under the current tenant.
 */
class ImportConfigurationOneshotUploadTest {

    @TempDir
    Path uploadDir;

    @Test
    void writesTheCsvUnderTheCurrentTenantDirectory() throws Exception {
        ImportConfigurationServiceEndPoint endpoint = endpointWith(new ExecutionContext("acme", null, null));
        Attachment file = mock(Attachment.class);
        doReturn(new ByteArrayInputStream("email\n".getBytes(StandardCharsets.UTF_8)))
                .when(file).getObject(InputStream.class);

        Response response = endpoint.processOneshotImportConfigurationCSV("profile-csv", file);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Path written = uploadDir.resolve("acme").resolve("profile-csv.csv");
        assertEquals("email\n", new String(Files.readAllBytes(written), StandardCharsets.UTF_8));
    }

    @Test
    void missingContextIsRejectedWithoutWriting() throws Exception {
        ImportConfigurationServiceEndPoint endpoint = endpointWith(null);

        Response response = endpoint.processOneshotImportConfigurationCSV("profile-csv", mock(Attachment.class));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertFalse(Files.exists(uploadDir.resolve("profile-csv.csv")));
    }

    private ImportConfigurationServiceEndPoint endpointWith(ExecutionContext context) throws Exception {
        ImportConfigurationServiceEndPoint endpoint = new ImportConfigurationServiceEndPoint();
        ConfigSharingService configSharingService = mock(ConfigSharingService.class);
        when(configSharingService.getProperty(RouterConstants.IMPORT_ONESHOT_UPLOAD_DIR)).thenReturn(uploadDir.toString());
        ExecutionContextManager executionContextManager = mock(ExecutionContextManager.class);
        when(executionContextManager.getCurrentContext()).thenReturn(context);
        endpoint.setConfigSharingService(configSharingService);
        endpoint.setExecutionContextManager(executionContextManager);
        return endpoint;
    }
}
