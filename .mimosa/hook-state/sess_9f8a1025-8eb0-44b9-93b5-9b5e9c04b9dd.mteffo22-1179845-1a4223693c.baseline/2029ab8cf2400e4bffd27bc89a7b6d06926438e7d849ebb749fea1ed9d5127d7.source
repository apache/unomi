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
import org.apache.unomi.didvc.api.CredentialIssueRequest;
import org.apache.unomi.didvc.api.items.CredentialRecord;
import org.apache.unomi.didvc.api.services.IssuanceService;import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.rest.security.RequiresRole;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Credential issuance and lifecycle REST API, used by the credential edge
 * (OID4VCI issuer) and administrative callers. Issuance runs the full
 * orchestration pipeline (schema whitelist, consent grants, status
 * allocation, formatting).
 */
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/didvc/credentials")
@Component(service = CredentialEndPoint.class, property = "osgi.jaxrs.resource=true")
public class CredentialEndPoint {

    @Reference
    private IssuanceService issuanceService;

    public void setIssuanceService(IssuanceService issuanceService) {
        this.issuanceService = issuanceService;
    }

    @POST
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response issue(CredentialIssueRequest request) {
        if (request == null || request.getTenantId() == null || request.getSchemaId() == null
                || request.getSubjectId() == null || request.getKid() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("tenantId, schemaId, subjectId and kid are required").build();
        }
        try {
            return Response.status(Response.Status.CREATED).entity(issuanceService.issueCredential(request)).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{recordId}")
    public Response get(@PathParam("recordId") String recordId) {
        CredentialRecord record = issuanceService.getCredential(recordId);
        if (record == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(record).build();
    }

    @DELETE
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Path("/{recordId}")
    public Response revoke(@PathParam("recordId") String recordId) {
        CredentialRecord record = issuanceService.revokeCredential(recordId);
        if (record == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(record).build();
    }

    @GET
    @Path("/{recordId}/revoked")
    public Response revoked(@PathParam("recordId") String recordId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", recordId);
        result.put("revoked", issuanceService.isCredentialRevoked(recordId));
        return Response.ok(result).build();
    }

    /**
     * Request body for holder key binding.
     */
    public static class RebindRequest {
        private String holderPublicJwkJson;

        public String getHolderPublicJwkJson() {
            return holderPublicJwkJson;
        }

        public void setHolderPublicJwkJson(String holderPublicJwkJson) {
            this.holderPublicJwkJson = holderPublicJwkJson;
        }
    }

    @POST
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Path("/{recordId}/rebind")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response rebind(@PathParam("recordId") String recordId, RebindRequest request) {
        if (request == null || request.getHolderPublicJwkJson() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("holderPublicJwkJson is required").build();
        }
        CredentialRecord record = issuanceService.rebindCredential(recordId, request.getHolderPublicJwkJson());
        if (record == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(record).build();
    }
}
