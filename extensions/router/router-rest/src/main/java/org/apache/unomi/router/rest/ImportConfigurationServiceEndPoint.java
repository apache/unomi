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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
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

/**
 * A JAX-RS endpoint to manage {@link ImportConfiguration}s.
 */
@WebService
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class ImportConfigurationServiceEndPoint extends AbstractConfigurationServiceEndpoint<ImportConfiguration> {

    private static final Logger logger = LoggerFactory.getLogger(ImportConfigurationServiceEndPoint.class.getName());

    public ImportConfigurationServiceEndPoint() {
        logger.info("Initializing import configuration service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setImportConfigurationService(ImportExportConfigurationService<ImportConfiguration> importConfigurationService) {
        configurationService = importConfigurationService;
    }

    /**
     * Save the given import configuration.
     *
     * @return the import configuration saved.
     */
    public ImportConfiguration saveConfiguration(ImportConfiguration importConfiguration) {
        ImportConfiguration importConfigSaved = configurationService.save(importConfiguration);
        if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(importConfigSaved.getConfigType())) {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            try {
                HttpPut httpPut = new HttpPut("http://localhost:" + configSharingService.getProperty("internalServerPort") + "/configUpdate/importConfigAdmin");
                StringEntity input = new StringEntity(new ObjectMapper().writeValueAsString(importConfigSaved));
                input.setContentType(MediaType.APPLICATION_JSON);
                httpPut.setEntity(input);

                HttpResponse response = httpClient.execute(httpPut);

                if (response.getStatusLine().getStatusCode() != 200) {
                    logger.error("Failed to update the running config: Please check the acceccibilty to the URI: \n{}",
                            "http://localhost234:" + configSharingService.getProperty("internalServerPort") + "/configUpdate/importConfigAdmin");
                    logger.error("HTTP Status code returned {}", response.getStatusLine().getStatusCode());
                    throw new PartialContentException("RUNNING_CONFIG_UPDATE_FAILED");
                }
            } catch (Exception e) {
                logger.warn("Unable to update Camel route [{}]", importConfiguration.getItemId());
                e.printStackTrace();
                throw new PartialContentException("RUNNING_CONFIG_UPDATE_FAILED");

            }
        }
        return importConfigSaved;
    }

    /**
     * Save/Update the given import configuration.
     * Prepare the file to be processed with Camel routes
     *
     * @return OK / NOK Http Code.
     */
    @POST
    @Path("/oneshot")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response processOneshotImportConfigurationCSV(@Multipart(value = "importConfigId") String importConfigId, @Multipart(value = "file") Attachment file) {
        try {
            java.nio.file.Path path = Paths.get(configSharingService.getProperty("oneshotImportUploadDir") + importConfigId + ".csv");
            Files.deleteIfExists(path);
            InputStream in = file.getObject(InputStream.class);

            Files.copy(in, path);

        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.ok().build();
    }
}
