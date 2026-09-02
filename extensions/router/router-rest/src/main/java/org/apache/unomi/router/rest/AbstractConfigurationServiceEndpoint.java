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
import org.apache.unomi.router.api.EndpointValidator;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Created by amidani on 26/06/2017.
 */
public abstract class AbstractConfigurationServiceEndpoint<T> {

    protected ImportExportConfigurationService<T> configurationService;

    protected ConfigSharingService configSharingService;

    /**
     * Refuses the configuration when the endpoint it names cannot be honoured -- an unsupported scheme,
     * or a file path outside the directories the deployment permits.
     *
     * <p>The route that would carry the configuration is built asynchronously, long after this call has
     * answered, so a configuration refused there would be stored and answered {@code 200} with nothing
     * but a log line to show for it. Refusing here gives the caller the reason while it can still act
     * on it, and keeps the configuration out of the store.
     *
     * <p>Answers {@code 503} instead while the router has yet to publish its settings, since a
     * configuration cannot be judged against settings that are not there yet.
     *
     * @param endpointUri              the endpoint URI the configuration names
     * @param permittedBaseDirsProperty the shared property holding the base directories for this direction
     */
    protected void refuseIfEndpointCannotBeHonoured(String endpointUri, String permittedBaseDirsProperty) {
        String allowedSchemes = (String) configSharingService.getProperty(RouterConstants.CONFIG_ALLOWED_ENDPOINTS);
        String permittedBaseDirs = (String) configSharingService.getProperty(permittedBaseDirsProperty);
        if (allowedSchemes == null || permittedBaseDirs == null) {
            // The router's Camel context publishes both on start-up, and this endpoint answers before
            // it has. An absent setting is not an empty allow-list: reading it as one would refuse a
            // legitimate configuration, and blame its scheme for it. Say the truth instead -- there is
            // nothing to validate against yet -- so the caller can retry rather than correct a
            // configuration that is already right.
            String unavailable = "the router is still starting up: no endpoint can be validated yet";
            throw new ServiceUnavailableException(unavailable,
                    Response.status(Response.Status.SERVICE_UNAVAILABLE)
                            .type(MediaType.TEXT_PLAIN).entity(unavailable).build());
        }

        String refusal = EndpointValidator.validate(endpointUri, allowedSchemes, permittedBaseDirs);
        if (refusal != null) {
            throw new BadRequestException(refusal, Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN).entity(refusal).build());
        }
    }

    /**
     * Retrieves all the configurations.
     *
     * @return all the configurations.
     */
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<T> getConfigurations() {
        return this.configurationService.getAll();
    }

    @POST
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public abstract T saveConfiguration(T configuration);

    /**
     * Retrieves a configuration by id.
     *
     * @param configId config id
     * @return the configuration that matches the given id.
     */
    @GET
    @Path("/{configId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public T getConfiguration(@PathParam("configId") String configId) {
        return this.configurationService.load(configId);
    }

    /**
     * Delete a configuration by id.
     *
     * @param configId config id
     */
    @DELETE
    @Path("/{configId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public abstract void deleteConfiguration(@PathParam("configId") String configId);

}
