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
import org.apache.unomi.router.api.services.ImportExportConfigurationService;

import javax.jws.WebMethod;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Created by amidani on 26/06/2017.
 */
public abstract class AbstractConfigurationServiceEndpoint<T> {

    protected ImportExportConfigurationService<T> configurationService;
    protected ConfigSharingService configSharingService;

    @WebMethod(exclude = true)
    public void setConfigSharingService(ConfigSharingService configSharingService) {
        this.configSharingService = configSharingService;
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
