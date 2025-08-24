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

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.api.services.ProfileExportService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A JAX-RS endpoint to manage {@link ExportConfiguration}s.
 */
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/exportConfiguration")
@Component(service=ExportConfigurationServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class ExportConfigurationServiceEndPoint extends AbstractConfigurationServiceEndpoint<ExportConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportConfigurationServiceEndPoint.class.getName());

    @Reference
    private ProfileExportService profileExportService;

    @Reference
    private ProfileService profileService;

    public ExportConfigurationServiceEndPoint() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        LOGGER.info("Initializing export configuration service endpoint...");
    }

    @Reference(target="(configDiscriminator=EXPORT)")
    public void setExportConfigurationService(ImportExportConfigurationService<ExportConfiguration> exportConfigurationService) {
        configurationService = exportConfigurationService;
    }

    public void setProfileExportService(ProfileExportService profileExportService) {
        this.profileExportService = profileExportService;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Save the given export configuration.
     *
     * @return the export configuration saved.
     */
    @Override
    public ExportConfiguration saveConfiguration(ExportConfiguration exportConfiguration) {
        ExportConfiguration exportConfigSaved = configurationService.save(exportConfiguration, true);

        return exportConfigSaved;
    }

    @Override
    public void deleteConfiguration(String configId) {
        this.configurationService.delete(configId);
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
