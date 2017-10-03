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
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.api.services.ProfileExportService;
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
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A JAX-RS endpoint to manage {@link ExportConfiguration}s.
 */
@WebService
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class ExportConfigurationServiceEndPoint extends AbstractConfigurationServiceEndpoint<ExportConfiguration> {

    private static final Logger logger = LoggerFactory.getLogger(ExportConfigurationServiceEndPoint.class.getName());

    private ProfileExportService profileExportService;
    private ProfileService profileService;

    public ExportConfigurationServiceEndPoint() {
        logger.info("Initializing export configuration service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setExportConfigurationService(ImportExportConfigurationService<ExportConfiguration> exportConfigurationService) {
        configurationService = exportConfigurationService;
    }

    @WebMethod(exclude = true)
    public void setProfileExportService(ProfileExportService profileExportService) {
        this.profileExportService = profileExportService;
    }

    @WebMethod(exclude = true)
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Save the given export configuration.
     *
     * @return the export configuration saved.
     */
    public ExportConfiguration saveConfiguration(ExportConfiguration exportConfiguration) {
        ExportConfiguration exportConfigSaved = configurationService.save(exportConfiguration);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpPut httpPut = new HttpPut(configSharingService.getProperty("internalServerAddress") + "/configUpdate/exportConfigAdmin");
            StringEntity input = new StringEntity(new ObjectMapper().writeValueAsString(exportConfigSaved));
            input.setContentType(MediaType.APPLICATION_JSON);
            httpPut.setEntity(input);

            HttpResponse response = httpClient.execute(httpPut);

            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error("Failed to update the running config: Please check the acceccibilty to the URI: \n{}",
                        configSharingService.getProperty("internalServerAddress") + "/configUpdate/importConfigAdmin");
                logger.error("HTTP Status code returned {}", response.getStatusLine().getStatusCode());
                throw new PartialContentException("RUNNING_CONFIG_UPDATE_FAILED");
            }
        } catch (Exception e) {
            logger.warn("Unable to update Camel route [{}]", exportConfiguration.getItemId());
            logger.debug("Unable to update Camel route", e);
            throw new PartialContentException("RUNNING_CONFIG_UPDATE_FAILED");

        }

        return exportConfigSaved;
    }

    @Override
    public void deleteConfiguration(String configId) {
        this.configurationService.delete(configId);

        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpDelete httpDelete = new HttpDelete(configSharingService.getProperty("internalServerAddress") + "/configUpdate/exportConfigAdmin/" + configId);

            HttpResponse response = httpClient.execute(httpDelete);

            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error("Failed to update the running config: Please check the accessibility to the URI: \n{}",
                        configSharingService.getProperty("internalServerAddress") + "/configUpdate/exportConfigAdmin/" + configId);
                logger.error("HTTP Status code returned {}", response.getStatusLine().getStatusCode());
                throw new PartialContentException("RUNNING_CONFIG_UPDATE_FAILED");
            }
        } catch (Exception e) {
            logger.warn("Unable to delete Camel route [{}]", configId);
            logger.debug("Unable to delete Camel route", e);
            throw new PartialContentException("RUNNING_CONFIG_UPDATE_FAILED");

        }
    }

    /**
     * Save/Update the given import configuration.
     * Prepare the file to be processed with Camel routes
     *
     * @param exportConfiguration configuration
     * @return OK / NOK Http Code.
     */
    @POST
    @Path("/oneshot")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/csv")
    public Response processOneshotImportConfigurationCSV(ExportConfiguration exportConfiguration) {
        String csvContent = profileExportService.extractProfilesBySegment(exportConfiguration);
        Response.ResponseBuilder response = Response.ok(csvContent);
        response.header("Content-Disposition",
                "attachment; filename=Profiles_export_" + new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date()) + ".csv");
        return response.build();
    }
}
