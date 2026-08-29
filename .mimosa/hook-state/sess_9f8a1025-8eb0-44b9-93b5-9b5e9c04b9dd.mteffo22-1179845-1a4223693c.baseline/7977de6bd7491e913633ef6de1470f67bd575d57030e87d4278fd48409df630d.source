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
import org.apache.unomi.didvc.api.items.ConsentGrantRecord;
import org.apache.unomi.didvc.api.items.DidSchema;
import org.apache.unomi.didvc.api.items.StatusListRecord;
import org.apache.unomi.didvc.api.items.TrustEntry;
import org.apache.unomi.didvc.api.services.ConsentBridgeService;
import org.apache.unomi.didvc.api.services.CredentialSchemaService;
import org.apache.unomi.didvc.api.services.PairwiseBindingService;
import org.apache.unomi.didvc.api.services.StatusService;
import org.apache.unomi.didvc.api.services.TrustRegistryService;import org.apache.unomi.api.security.UnomiRoles;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST APIs for credential schemas, status lists, trust entries, pairwise
 * bindings and consent grants — the platform surface the credential edge
 * calls. Pairwise resolution (reference to profile) is deliberately not
 * exposed: the identity half stays inside the platform.
 */
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/didvc")
@Component(service = DidvcRegistryEndPoint.class, property = "osgi.jaxrs.resource=true")
public class DidvcRegistryEndPoint {

    @Reference
    private CredentialSchemaService schemaService;
    @Reference
    private StatusService statusService;
    @Reference
    private TrustRegistryService trustRegistryService;
    @Reference
    private PairwiseBindingService pairwiseBindingService;
    @Reference
    private ConsentBridgeService consentBridgeService;

    public void setSchemaService(CredentialSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    public void setStatusService(StatusService statusService) {
        this.statusService = statusService;
    }

    public void setTrustRegistryService(TrustRegistryService trustRegistryService) {
        this.trustRegistryService = trustRegistryService;
    }

    public void setPairwiseBindingService(PairwiseBindingService pairwiseBindingService) {
        this.pairwiseBindingService = pairwiseBindingService;
    }

    public void setConsentBridgeService(ConsentBridgeService consentBridgeService) {
        this.consentBridgeService = consentBridgeService;
    }

    /**
     * Request body for status-list creation.
     */
    public static class CreateStatusListRequest {
        private String tenantId;
        private String issuerDid;
        private String statusPurpose;
        private Integer size;

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getIssuerDid() {
            return issuerDid;
        }

        public void setIssuerDid(String issuerDid) {
            this.issuerDid = issuerDid;
        }

        public String getStatusPurpose() {
            return statusPurpose;
        }

        public void setStatusPurpose(String statusPurpose) {
            this.statusPurpose = statusPurpose;
        }

        public Integer getSize() {
            return size;
        }

        public void setSize(Integer size) {
            this.size = size;
        }
    }

    /**
     * Request body for status-list publication.
     */
    public static class PublishRequest {
        private String kid;

        public String getKid() {
            return kid;
        }

        public void setKid(String kid) {
            this.kid = kid;
        }
    }

    /**
     * Request body for pairwise binding creation.
     */
    public static class PairwiseRequest {
        private String profileId;
        private String verifierTenantId;

        public String getProfileId() {
            return profileId;
        }

        public void setProfileId(String profileId) {
            this.profileId = profileId;
        }

        public String getVerifierTenantId() {
            return verifierTenantId;
        }

        public void setVerifierTenantId(String verifierTenantId) {
            this.verifierTenantId = verifierTenantId;
        }
    }

    // ---- schemas ----

    @POST
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Path("/schemas")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveSchema(DidSchema schema) {
        if (schema == null || schema.getItemId() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("schema itemId is required").build();
        }
        schemaService.saveSchema(schema);
        return Response.status(Response.Status.CREATED).entity(schema).build();
    }

    @GET
    @Path("/schemas/{schemaId}")
    public Response getSchema(@PathParam("schemaId") String schemaId) {
        DidSchema schema = schemaService.getSchema(schemaId);
        if (schema == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(schema).build();
    }

    @GET
    @Path("/schemas")
    public Response listSchemas(@QueryParam("tenantId") String tenantId) {
        return Response.ok(schemaService.getSchemas(tenantId)).build();
    }

    @DELETE
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Path("/schemas/{schemaId}")
    public Response deleteSchema(@PathParam("schemaId") String schemaId) {
        schemaService.deleteSchema(schemaId);
        return Response.noContent().build();
    }

    // ---- status lists ----

    @POST
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Path("/statuslists")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createStatusList(CreateStatusListRequest request) {
        if (request == null || request.getTenantId() == null || request.getIssuerDid() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("tenantId and issuerDid are required").build();
        }
        String purpose = request.getStatusPurpose() == null ? "revocation" : request.getStatusPurpose();
        int size = request.getSize() == null ? 1024 : request.getSize();
        StatusListRecord record = statusService.createStatusList(request.getTenantId(), request.getIssuerDid(),
                purpose, size);
        return Response.status(Response.Status.CREATED).entity(record).build();
    }

    @GET
    @Path("/statuslists/{statusListId}")
    public Response getStatusList(@PathParam("statusListId") String statusListId) {
        StatusListRecord record = statusService.getStatusList(statusListId);
        if (record == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(record).build();
    }

    @POST
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Path("/statuslists/{statusListId}/publish")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response publishStatusList(@PathParam("statusListId") String statusListId, PublishRequest request) {
        if (request == null || request.getKid() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("kid is required").build();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status_list", statusService.publish(statusListId, request.getKid()));
        return Response.ok(result).build();
    }

    @GET
    @Path("/statuslists/{statusListId}/revoked")
    public Response statusRevoked(@PathParam("statusListId") String statusListId, @QueryParam("index") Integer index) {
        if (index == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("index query parameter is required").build();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revoked", statusService.isRevoked(statusListId, index));
        return Response.ok(result).build();
    }

    // ---- trust entries ----

    @POST
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Path("/trust-entries")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveTrustEntry(TrustEntry entry) {
        if (entry == null || entry.getItemId() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("trust entry itemId is required").build();
        }
        trustRegistryService.saveTrustEntry(entry);
        return Response.status(Response.Status.CREATED).entity(entry).build();
    }

    @GET
    @Path("/trust-entries")
    public Response listTrustEntries(@QueryParam("verifierTenantId") String verifierTenantId) {
        List<TrustEntry> entries = trustRegistryService.getTrustEntries(verifierTenantId);
        return Response.ok(entries).build();
    }

    @DELETE
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Path("/trust-entries/{entryId}")
    public Response deleteTrustEntry(@PathParam("entryId") String entryId) {
        trustRegistryService.deleteTrustEntry(entryId);
        return Response.noContent().build();
    }

    @GET
    @Path("/trust-check")
    public Response trustCheck(@QueryParam("verifierTenantId") String verifierTenantId,
                               @QueryParam("issuerDid") String issuerDid,
                               @QueryParam("vct") String vct) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trusted", trustRegistryService.isTrusted(verifierTenantId, issuerDid, vct, new java.util.Date()));
        return Response.ok(result).build();
    }

    // ---- pairwise bindings ----

    @POST
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Path("/pairwise-bindings")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createPairwiseBinding(PairwiseRequest request) {
        if (request == null || request.getProfileId() == null || request.getVerifierTenantId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("profileId and verifierTenantId are required").build();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("opaqueReference",
                pairwiseBindingService.getOrCreateOpaqueReference(request.getProfileId(), request.getVerifierTenantId()));
        return Response.status(Response.Status.CREATED).entity(result).build();
    }

    // ---- consent grants ----

    @POST
    @RequiresRole(UnomiRoles.ADMINISTRATOR)
    @Path("/consent-grants")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveConsentGrant(ConsentGrantRecord grant) {
        if (grant == null || grant.getItemId() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("grant itemId is required").build();
        }
        consentBridgeService.saveGrant(grant);
        return Response.status(Response.Status.CREATED).entity(grant).build();
    }
}
