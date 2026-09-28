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

import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.router.api.EndpointValidator;
import org.apache.unomi.router.api.ImportExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Abstract JAX-RS base for router import/export configuration CRUD.
 *
 * @param <T> configuration item type
 */
public abstract class AbstractConfigurationServiceEndpoint<T extends ImportExportConfiguration> {

    protected ImportExportConfigurationService<T> configurationService;

    protected ConfigSharingService configSharingService;

    protected ExecutionContextManager executionContextManager;

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
     * @param endpointUri               the endpoint URI the configuration names
     * @param permittedBaseDirsProperty the shared property holding the base directories for this direction
     * @param tenantId                  the tenant the configuration will be stored for
     */
    private void refuseIfEndpointCannotBeHonoured(String endpointUri, String permittedBaseDirsProperty, String tenantId) {
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

        String refusal = EndpointValidator.validateForTenant(endpointUri, allowedSchemes, permittedBaseDirs, tenantId);
        if (refusal != null) {
            throw new BadRequestException(refusal, Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN).entity(refusal).build());
        }
    }

    /**
     * The tenant the file endpoint is confined to.
     *
     * <p>A caller who is not the system is confined to their own tenant, whatever tenant the body names:
     * the configuration service will reject a different one, and judging the path against the claimed
     * tenant would let the check pass for a directory the caller does not own. A system caller may name
     * the tenant. When the body names none, the current context supplies it.
     */
    private String tenantToConfine(String configuredTenantId) {
        ExecutionContext context = executionContextManager == null ? null : executionContextManager.getCurrentContext();
        if (context != null && !context.isSystem()) {
            return context.getTenantId();
        }
        if (configuredTenantId != null && !configuredTenantId.trim().isEmpty()) {
            return configuredTenantId;
        }
        return context == null ? null : context.getTenantId();
    }

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

    /** The property of the configuration that holds its endpoint URI. */
    protected abstract String endpointProperty();

    /** The shared property that holds the base directories permitted for this direction. */
    protected abstract String permittedBaseDirsProperty();

    /**
     * Creates or updates a router configuration. The endpoint of a recurrent configuration is validated
     * here, before the store is reached, so that no configuration type can be saved without it.
     *
     * @param configuration the configuration to save
     * @return the persisted configuration
     * @api.status 200 empty Configuration saved.
     */
    @POST
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public T saveConfiguration(T configuration) {
        if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(configuration.getConfigType())) {
            refuseIfEndpointCannotBeHonoured((String) configuration.getProperties().get(endpointProperty()),
                    permittedBaseDirsProperty(), tenantToConfine(configuration.getTenantId()));
        }
        return configurationService.save(configuration, true);
    }

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
