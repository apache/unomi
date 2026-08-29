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

package org.apache.unomi.didvc.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.services.DidService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Admin REST API for did:web lifecycle: create, resolve, list, rotate and
 * deactivate. Picked up by the Unomi RestServer via the
 * {@code osgi.jaxrs.resource} service property.
 */
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/didvc/dids")
@Component(service = DidServiceEndPoint.class, property = "osgi.jaxrs.resource=true")
public class DidServiceEndPoint {

    @Reference
    private DidService didService;

    public void setDidService(DidService didService) {
        this.didService = didService;
    }

    /**
     * Request body for DID creation.
     */
    public static class CreateRequest {
        private String tenantId;
        private String domain;
        private String path;
        private String algorithm;

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }
    }

    /**
     * Request body for key rotation.
     */
    public static class RotateRequest {
        private String algorithm;

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateRequest request) {
        if (request == null || request.getTenantId() == null || request.getDomain() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("tenantId and domain are required").build();
        }
        String algorithm = request.getAlgorithm() == null ? "EdDSA" : request.getAlgorithm();
        try {
            DidDocumentData doc = didService.createDid(request.getTenantId(), request.getDomain(),
                    request.getPath(), algorithm);
            return Response.status(Response.Status.CREATED).entity(doc).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{did}")
    public Response resolve(@PathParam("did") String did) {
        DidDocumentData doc = didService.resolveDid(did);
        if (doc == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(doc).build();
    }

    @GET
    public Response list(@QueryParam("tenantId") String tenantId) {
        List<DidDocumentData> dids = didService.listDids(tenantId);
        return Response.ok(dids).build();
    }

    @POST
    @Path("/{did}/rotate")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response rotate(@PathParam("did") String did, RotateRequest request) {
        String algorithm = request == null || request.getAlgorithm() == null ? "EdDSA" : request.getAlgorithm();
        DidDocumentData doc = didService.rotateKey(did, algorithm);
        if (doc == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(doc).build();
    }

    @DELETE
    @Path("/{did}")
    public Response deactivate(@PathParam("did") String did) {
        DidDocumentData doc = didService.deactivateDid(did);
        if (doc == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(doc).build();
    }
}
