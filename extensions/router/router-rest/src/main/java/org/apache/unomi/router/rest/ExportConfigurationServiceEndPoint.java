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
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

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

    public ExportConfigurationServiceEndPoint() {
        logger.info("Initializing export configuration service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setExportConfigurationService(ImportExportConfigurationService<ExportConfiguration> exportConfigurationService) {
        configurationService = exportConfigurationService;
    }

    /**
     * Save the given export configuration.
     *
     * @return the export configuration saved.
     */
    public ExportConfiguration saveConfiguration(ExportConfiguration exportConfiguration) {
        ExportConfiguration exportConfigSaved = configurationService.save(exportConfiguration);
        if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(exportConfigSaved.getConfigType())) {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            try {
                HttpPut httpPut = new HttpPut("http://localhost:" + configSharingService.getProperty("internalServerPort") + "/configUpdate/exportConfigAdmin");
                StringEntity input = new StringEntity(new ObjectMapper().writeValueAsString(exportConfigSaved));
                input.setContentType(MediaType.APPLICATION_JSON);
                httpPut.setEntity(input);

                HttpResponse response = httpClient.execute(httpPut);

                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException("Failed : HTTP error code : "
                            + response.getStatusLine().getStatusCode());
                }
            } catch (IOException e) {
                logger.warn("Unable to update Camel route [{}]", exportConfiguration.getItemId());
            }
        }

        return exportConfigSaved;
    }
}
