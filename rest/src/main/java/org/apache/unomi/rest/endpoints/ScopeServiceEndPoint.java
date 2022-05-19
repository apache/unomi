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
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.services.ScopeService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * A JAX-RS endpoint to manage {@link org.apache.unomi.api.Scope}s.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(allowAllOrigins = true, allowCredentials = true)
@Path("/scopes")
@Component(service = ScopeServiceEndPoint.class, property = "osgi.jaxrs.resource=true")
public class ScopeServiceEndPoint {

    private static final Logger logger = LoggerFactory.getLogger(ScopeServiceEndPoint.class.getName());

    @Reference
    private ScopeService scopeService;

    public ScopeServiceEndPoint() {
        logger.info("Initializing scope service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setScopeService(ScopeService scopeService) {
        this.scopeService = scopeService;
    }

    /**
     * Retrieves the scopes metadatas by default.
     *
     * @return a List of the scope metadata
     */
    @GET
    @Path("/")
    public List<Metadata> getScopesMetadatas() {
        return scopeService.getScopesMetadatas();
    }

    /**
     * Persists the specified scope.
     *
     * @param scope the scope to be persisted
     */
    @POST
    @Path("/")
    public Response save(Scope scope) {
        scopeService.save(scope);
        return Response.ok().build();
    }

    /**
     * Retrieves the scope identified by the specified identifier.
     *
     * @param scopeId the identifier of the scope we want to retrieve
     * @return the scope identified by the specified identifier or {@code null} if no such scope exists.
     */
    @GET
    @Path("/{scopeId}")
    public Scope getScope(@PathParam("scopeId") String scopeId) {
        return scopeService.getScope(scopeId);
    }

    /**
     * Deletes a scope.
     *
     * @param scopeId the identifier of the scope
     */
    @DELETE
    @Path("/{scopeId}")
    public void delete(@PathParam("scopeId") String scopeId) {
        scopeService.delete(scopeId);
    }
}
