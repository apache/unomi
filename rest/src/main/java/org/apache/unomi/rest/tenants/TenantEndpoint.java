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
package org.apache.unomi.rest.tenants;

import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * REST endpoint for managing tenants in the Apache Unomi system.
 * Provides operations for creating, updating, deleting, and retrieving tenants,
 * as well as managing their API keys and configurations.
 */
@Component
@Path("/tenants")
public class TenantEndpoint {

    @Reference
    private TenantService tenantService;

    /**
     * Retrieves all tenants in the system.
     *
     * @return a list of all tenants
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Tenant> getTenants() {
        return tenantService.getAllTenants();
    }

    /**
     * Retrieves a specific tenant by ID.
     *
     * @param tenantId the ID of the tenant to retrieve
     * @return the requested tenant
     * @throws WebApplicationException with 404 status if tenant is not found
     */
    @GET
    @Path("/{tenantId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Tenant getTenant(@PathParam("tenantId") String tenantId) {
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant == null) {
            throw new WebApplicationException("Tenant not found", Response.Status.NOT_FOUND);
        }
        return tenant;
    }

    /**
     * Creates a new tenant.
     *
     * @param request the tenant creation request containing tenant details
     * @return the created tenant with generated API keys
     * @throws WebApplicationException with 400 status if request is invalid
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Tenant createTenant(TenantRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new WebApplicationException("Tenant name is required", Response.Status.BAD_REQUEST);
        }

        Tenant tenant = tenantService.createTenant(request.getName(), request.getProperties());
        if (tenant != null) {
            // Generate both API keys with default validity period
            ApiKey publicKey = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, null);
            ApiKey privateKey = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, null);

            // Store API keys in tenant
            List<ApiKey> apiKeys = tenant.getApiKeys();
            apiKeys.add(publicKey);
            apiKeys.add(privateKey);
            tenant.setApiKeys(apiKeys);

            // Set active API keys
            tenant.setPublicApiKey(publicKey.getKey());
            tenant.setPrivateApiKey(privateKey.getKey());

            // Save tenant with API keys
            tenantService.saveTenant(tenant);
        }
        return tenant;
    }

    /**
     * Updates an existing tenant.
     *
     * @param tenantId the ID of the tenant to update
     * @param tenant the updated tenant information
     * @return the updated tenant
     * @throws WebApplicationException with 404 status if tenant is not found
     */
    @PUT
    @Path("/{tenantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Tenant updateTenant(@PathParam("tenantId") String tenantId, Tenant tenant) {
        if (!tenantId.equals(tenant.getItemId())) {
            throw new WebApplicationException("Tenant ID mismatch", Response.Status.BAD_REQUEST);
        }
        
        if (tenantService.getTenant(tenantId) == null) {
            throw new WebApplicationException("Tenant not found", Response.Status.NOT_FOUND);
        }

        tenantService.saveTenant(tenant);
        return tenant;
    }

    /**
     * Deletes a tenant.
     *
     * @param tenantId the ID of the tenant to delete
     * @return 204 No Content on success
     * @throws WebApplicationException with 404 status if tenant is not found
     */
    @DELETE
    @Path("/{tenantId}")
    public Response deleteTenant(@PathParam("tenantId") String tenantId) {
        if (tenantService.getTenant(tenantId) == null) {
            throw new WebApplicationException("Tenant not found", Response.Status.NOT_FOUND);
        }

        tenantService.deleteTenant(tenantId);
        return Response.noContent().build();
    }

    /**
     * Generates a new API key for a tenant.
     *
     * @param tenantId the ID of the tenant
     * @param type the type of API key to generate (PUBLIC or PRIVATE)
     * @param validityDays the validity period in days (0 or null for no expiration)
     * @return the generated API key
     * @throws WebApplicationException with 404 status if tenant is not found
     */
    @POST
    @Path("/{tenantId}/apikeys")
    @Produces(MediaType.APPLICATION_JSON)
    public ApiKey generateApiKey(@PathParam("tenantId") String tenantId,
                               @QueryParam("type") ApiKey.ApiKeyType type,
                               @QueryParam("validityDays") Integer validityDays) {
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant == null) {
            throw new WebApplicationException("Tenant not found", Response.Status.NOT_FOUND);
        }

        // Convert days to milliseconds if provided
        Long validityPeriod = null;
        if (validityDays != null && validityDays > 0) {
            validityPeriod = validityDays * 24L * 60L * 60L * 1000L;
        }

        ApiKey apiKey = tenantService.generateApiKeyWithType(tenantId, type, validityPeriod);

        // Update tenant's API keys
        List<ApiKey> apiKeys = tenant.getApiKeys();
        apiKeys.add(apiKey);

        // Update active key reference
        if (type == ApiKey.ApiKeyType.PUBLIC) {
            tenant.setPublicApiKey(apiKey.getKey());
        } else if (type == ApiKey.ApiKeyType.PRIVATE) {
            tenant.setPrivateApiKey(apiKey.getKey());
        }

        tenantService.saveTenant(tenant);
        return apiKey;
    }

    /**
     * Validates an API key for a tenant.
     *
     * @param tenantId the ID of the tenant
     * @param apiKey the API key to validate
     * @param type the type of API key (PUBLIC or PRIVATE)
     * @return 200 OK if valid, 401 Unauthorized if invalid
     */
    @GET
    @Path("/{tenantId}/apikeys/validate")
    public Response validateApiKey(@PathParam("tenantId") String tenantId,
                                 @QueryParam("key") String apiKey,
                                 @QueryParam("type") ApiKey.ApiKeyType type) {
        boolean isValid = tenantService.validateApiKeyWithType(tenantId, apiKey, type);
        if (isValid) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }
}
