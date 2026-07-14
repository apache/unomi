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

import org.apache.unomi.router.api.services.ImportExportConfigurationService;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Abstract JAX-RS base for router import/export configuration CRUD.
 *
 * @param <T> configuration item type
 */
public abstract class AbstractConfigurationServiceEndpoint<T> {

    protected ImportExportConfigurationService<T> configurationService;

    /**
     * Returns all router configurations of this type.
     *
     * @return all configurations (may be empty)
     * @api.status 200 array empty Configuration list (may be empty).
     */
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<T> getConfigurations() {
        return this.configurationService.getAll();
    }

    /**
     * Creates or updates a router configuration.
     *
     * @param configuration the configuration to save
     * @return the persisted configuration
     * @api.status 200 empty Configuration saved.
     */
    @POST
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public abstract T saveConfiguration(T configuration);

    /**
     * Returns the configuration with the given id.
     * When it does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     *
     * @param configId the configuration identifier
     * @return the configuration, or {@code null} when missing
     * @api.status 200 empty Configuration found, or empty body when missing.
     */
    @GET
    @Path("/{configId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public T getConfiguration(@PathParam("configId") String configId) {
        return this.configurationService.load(configId);
    }

    /**
     * Deletes the configuration with the given id.
     *
     * @param configId the configuration identifier
     * @api.status 204 empty Configuration deleted.
     */
    @DELETE
    @Path("/{configId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public abstract void deleteConfiguration(@PathParam("configId") String configId);

}
