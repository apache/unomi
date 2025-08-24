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
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

/**
 * A JAX-RS endpoint to manage {@link ImportConfiguration}s.
 */
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/importConfiguration")
@Component(service=ImportConfigurationServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class ImportConfigurationServiceEndPoint extends AbstractConfigurationServiceEndpoint<ImportConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportConfigurationServiceEndPoint.class.getName());

    @Reference
    protected ConfigSharingService configSharingService;

    public void setConfigSharingService(ConfigSharingService configSharingService) {
        this.configSharingService = configSharingService;
    }

    public ImportConfigurationServiceEndPoint() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        LOGGER.info("Initializing import configuration service endpoint...");
    }

    @Reference(target="(configDiscriminator=IMPORT)")
    public void setImportConfigurationService(ImportExportConfigurationService<ImportConfiguration> importConfigurationService) {
        configurationService = importConfigurationService;
    }

    /**
     * Save the given import configuration.
     *
     * @return the import configuration saved.
     */
    @Override
    public ImportConfiguration saveConfiguration(ImportConfiguration importConfiguration) {

        ImportConfiguration importConfigSaved = configurationService.save(importConfiguration, true);

        return importConfigSaved;
    }

    @Override
    public void deleteConfiguration(String configId) {
        this.configurationService.delete(configId);
    }

    /**
     * Save/Update the given import configuration.
     * Prepare the file to be processed with Camel routes
     *
     * @param file           file
     * @param importConfigId config
     * @return OK / NOK Http Code.
     */
    @POST
    @Path("/oneshot")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response processOneshotImportConfigurationCSV(@Multipart(value = "importConfigId") @NotNull @Pattern(regexp = "^[a-zA-Z0-9_.\\-]{1,255}$") String importConfigId,
                                                         @Multipart(value = "file") Attachment file) {
        try {
            java.nio.file.Path path = Paths.get(configSharingService.getProperty(RouterConstants.IMPORT_ONESHOT_UPLOAD_DIR) + importConfigId + ".csv");
            Files.deleteIfExists(path);
            InputStream in = file.getObject(InputStream.class);

            Files.copy(in, path);

        } catch (IOException e) {
            LOGGER.error("Error processing one shot configuration CSV", e);
            return Response.serverError().build();
        }
        return Response.ok().build();
    }
}
