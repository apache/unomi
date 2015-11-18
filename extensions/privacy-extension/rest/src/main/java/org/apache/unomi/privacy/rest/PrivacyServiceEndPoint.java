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

package org.apache.unomi.privacy.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.ServerInfo;
import org.apache.unomi.api.services.PrivacyService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * REST API end point for privacy service
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class PrivacyServiceEndPoint {

    private PrivacyService privacyService;

    @WebMethod(exclude = true)
    public void setPrivacyService(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    @GET
    @Path("/info")
    public ServerInfo getServerInfo() {
        return privacyService.getServerInfo();
    }

    @DELETE
    @Path("/profiles/{profileId}")
    public Response deleteProfileData(@PathParam("profileId") String profileId, @QueryParam("withData") @DefaultValue("false") boolean withData) {
        if (withData) {
            privacyService.deleteProfileData(profileId);
        } else {
            privacyService.deleteProfile(profileId);
        }
        return Response.ok().build();
    }

    @POST
    @Path("/profiles/{profileId}/anonymize")
    public Response anonymizeBrowsingData(@PathParam("profileId") String profileId) {
        String newProfileId = privacyService.anonymizeBrowsingData(profileId);
        if (!profileId.equals(newProfileId)) {
            return Response.ok()
                    .cookie(new NewCookie("context-profile-id", newProfileId, "/", null, null, NewCookie.DEFAULT_MAX_AGE, false))
                    .entity(newProfileId)
                    .build();
        }
        return Response.serverError().build();
    }

    @GET
    @Path("/profiles/{profileId}/anonymous")
    public Boolean isAnonymous(@PathParam("profileId") String profileId) {
        return privacyService.isAnonymous(profileId);
    }

    @POST
    @Path("/profiles/{profileId}/anonymous")
    public Response activateAnonymousSurfing(@PathParam("profileId") String profileId) {
        privacyService.setAnonymous(profileId, true);
        return Response.ok().build();
    }

    @DELETE
    @Path("/profiles/{profileId}/anonymous")
    public Response deactivateAnonymousSurfing(@PathParam("profileId") String profileId) {
        privacyService.setAnonymous(profileId, false);
        return Response.ok().build();
    }

    @GET
    @Path("/profiles/{profileId}/eventFilters")
    public List<String> getEventFilters(@PathParam("profileId") String profileId) {
        return privacyService.getFilteredEventTypes(profileId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/profiles/{profileId}/eventFilters")
    public Response setEventFilters(@PathParam("profileId") String profileId, List<String> eventFilters) {
        privacyService.setFilteredEventTypes(profileId, eventFilters);
        return Response.ok().build();
    }

    @DELETE
    @Path("/profiles/{profileId}/properties/{propertyName}")
    public Response removeProperty(@PathParam("profileId") String profileId, @PathParam("propertyName") String propertyName) {
        privacyService.removeProperty(profileId, propertyName);
        return Response.ok().build();
    }

}
