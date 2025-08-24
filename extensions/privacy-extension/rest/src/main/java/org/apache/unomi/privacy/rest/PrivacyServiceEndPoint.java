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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * REST API end point for privacy service
 */
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/privacy")
@Component(service=PrivacyServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class PrivacyServiceEndPoint {

    @Reference
    private PrivacyService privacyService;

    public void setPrivacyService(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    @GET
    @Path("/info")
    public ServerInfo getServerInfo() {
        return privacyService.getServerInfo();
    }

    @GET
    @Path("/infos")
    public List<ServerInfo> getServerInfos() {
        return privacyService.getServerInfos();
    }

    @DELETE
    @Path("/profiles/{profileId}")
    public Response deleteProfileData(@PathParam("profileId") String profileId, @QueryParam("withData") @DefaultValue("false") boolean withData,
                                      @QueryParam("purgeAll") @DefaultValue("false") boolean purgeAll) {
        if (purgeAll) {
            privacyService.deleteProfileData(profileId,true);
        } else if (withData) {
            privacyService.deleteProfileData(profileId,false);
        } else {
            privacyService.deleteProfile(profileId);
        }
        return Response.ok().build();
    }

    @POST
    @Path("/profiles/{profileId}/anonymize")
    public void anonymizeProfile(@PathParam("profileId") String profileId, @QueryParam("scope") String scope) {
        privacyService.anonymizeProfile(profileId, scope);
    }

    @GET
    @Path("/profiles/{profileId}/anonymousBrowsing")
    public Boolean isAnonymousBrowsing(@PathParam("profileId") String profileId) {
        return privacyService.isRequireAnonymousBrowsing(profileId);
    }

    @POST
    @Path("/profiles/{profileId}/anonymousBrowsing")
    public Response activateAnonymousBrowsing(@PathParam("profileId") String profileId, @QueryParam("anonymizePastBrowsing") @DefaultValue("false") boolean past, @QueryParam("scope") String scope) {
        if (past) {
            privacyService.anonymizeBrowsingData(profileId);
        }
        Boolean r = privacyService.setRequireAnonymousBrowsing(profileId, true, scope);
        return r ? Response.ok().build() : Response.serverError().build();
    }

    @DELETE
    @Path("/profiles/{profileId}/anonymousBrowsing")
    public Response deactivateAnonymousBrowsing(@PathParam("profileId") String profileId, @QueryParam("scope") String scope) {
        Boolean r = privacyService.setRequireAnonymousBrowsing(profileId, false, scope);
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
