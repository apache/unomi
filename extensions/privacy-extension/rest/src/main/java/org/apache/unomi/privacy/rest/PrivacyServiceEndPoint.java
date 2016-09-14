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
    public void anonymizeProfile(@PathParam("profileId") String profileId) {
        privacyService.anonymizeProfile(profileId);
    }

    @GET
    @Path("/profiles/{profileId}/anonymousBrowsing")
    public Boolean isAnonymousBrowsing(@PathParam("profileId") String profileId) {
        return privacyService.isRequireAnonymousBrowsing(profileId);
    }

    @POST
    @Path("/profiles/{profileId}/anonymousBrowsing")
    public Response activateAnonymousBrowsing(@PathParam("profileId") String profileId, @QueryParam("anonymizePastBrowsing") @DefaultValue("false") boolean past) {
        if (past) {
            privacyService.anonymizeBrowsingData(profileId);
        }
        Boolean r = privacyService.setRequireAnonymousBrowsing(profileId, true);
        return r ? Response.ok().build() : Response.serverError().build();
    }

    @DELETE
    @Path("/profiles/{profileId}/anonymousBrowsing")
    public Response deactivateAnonymousBrowsing(@PathParam("profileId") String profileId) {
        Boolean r = privacyService.setRequireAnonymousBrowsing(profileId, false);
        return r ? Response.ok().build() : Response.serverError().build();
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
