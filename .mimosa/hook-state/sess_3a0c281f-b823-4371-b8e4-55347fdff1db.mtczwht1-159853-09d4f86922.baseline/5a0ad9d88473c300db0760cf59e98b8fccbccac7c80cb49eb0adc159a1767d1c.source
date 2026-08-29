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

package org.apache.unomi.rest.endpoints;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.services.ScopeService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * A JAX-RS endpoint to manage {@link org.apache.unomi.api.Scope}s.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(allowAllOrigins = true, allowCredentials = true)
@Path("/scopes")
@Component(service = ScopeServiceEndPoint.class, property = "osgi.jaxrs.resource=true")
public class ScopeServiceEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScopeServiceEndPoint.class.getName());

    @Reference
    private ScopeService scopeService;

    /**
     * Creates the scope service endpoint.
     */
    public ScopeServiceEndPoint() {
        LOGGER.info("Initializing scope service endpoint...");
    }

    /**
     * Sets the scope service.
     *
     * @param scopeService the scope service
     */
    public void setScopeService(ScopeService scopeService) {
        this.scopeService = scopeService;
    }

    /**
     * Returns all configured scopes.
     *
     * @return all known scopes
     * @api.status 200 array org.apache.unomi.api.Scope All scopes (may be empty).
     * @api.example [{"itemId":"systemscope","itemType":"scope","metadata":{"id":"systemscope","name":"System scope","scope":"systemscope","enabled":true}}]
     */
    @GET
    @Path("/")
    public List<Scope> getScopes() {return scopeService.getScopes();
    }

    /**
     * Persists the specified scope.
     *
     * @param scope the scope to be persisted
     * @return an empty success response
     * @api.status 200 empty Scope created or updated.
     * @api.example {"itemId":"mysite","itemType":"scope","metadata":{"id":"mysite","name":"My site","scope":"mysite","enabled":true}}
     */
    @POST
    @Path("/")
    public Response save(Scope scope) {
        scopeService.save(scope);
        return Response.ok().build();
    }

    /**
     * Returns the scope with the given ID.
     * When the scope does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     *
     * @param scopeId the scope identifier
     * @return the scope, or {@code null} when it does not exist
     * @api.status 200 org.apache.unomi.api.Scope Scope found, or empty body when missing.
     * @api.example {"itemId":"mysite","itemType":"scope","metadata":{"id":"mysite","name":"My site","scope":"mysite","enabled":true}}
     */
    @GET
    @Path("/{scopeId}")
    public Scope getScope(@PathParam("scopeId") String scopeId) {
        return scopeService.getScope(scopeId);
    }

    /**
     * Deletes the scope with the given ID.
     *
     * @param scopeId the identifier of the scope
     * @api.status 204 empty Scope deleted.
     * @api.example {"itemId":"mysite","itemType":"scope","metadata":{"id":"mysite","name":"My site","scope":"mysite","enabled":true}}
     */
    @DELETE
    @Path("/{scopeId}")
    public void delete(@PathParam("scopeId") String scopeId) {
        scopeService.delete(scopeId);
    }
}
