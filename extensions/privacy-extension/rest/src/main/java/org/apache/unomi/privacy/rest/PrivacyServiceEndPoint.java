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
 * REST endpoint for privacy / GDPR-style profile operations and server identity.
 * Covers anonymous browsing, event filters, property erasure, anonymization, and profile deletion.
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

    /**
     * Sets the privacy service.
     *
     * @param privacyService the privacy service
     */
    public void setPrivacyService(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    /**
     * Returns build/capability information for the Unomi server (first entry when multiple bundles report).
     *
     * @return server identity and capability metadata
     * @api.status 200 org.apache.unomi.api.ServerInfo Unomi server info.
     * @api.example {"serverIdentifier":"unomi","serverVersion":"3.1.0-SNAPSHOT","serverBuildNumber":"1","serverScmBranch":"main","capabilities":{},"eventTypes":[],"logoLines":[]}
     */
    @GET
    @Path("/info")
    public ServerInfo getServerInfo() {
        return privacyService.getServerInfo();
    }

    /**
     * Returns build/capability information for all reporting server bundles.
     *
     * @return list of server info records (Unomi first)
     * @api.status 200 array org.apache.unomi.api.ServerInfo Server info entries (may be a single Unomi entry).
     * @api.example [{"serverIdentifier":"unomi","serverVersion":"3.1.0-SNAPSHOT","serverBuildNumber":"1","serverScmBranch":"main"}]
     */
    @GET
    @Path("/infos")
    public List<ServerInfo> getServerInfos() {
        return privacyService.getServerInfos();
    }

    /**
     * Deletes or purges privacy-related data for a profile.
     * <p>
     * Flag precedence: {@code purgeAll=true} purges associated data then the profile;
     * else {@code withData=true} deletes profile data without full purge;
     * else only the profile record is deleted.
     *
     * @param profileId the profile identifier
     * @param withData when {@code true} (and not purgeAll), delete associated browsing data as well
     * @param purgeAll when {@code true}, purge all associated data then delete the profile
     * @return HTTP 200 when the operation completes
     * @api.status 200 empty Profile / data deletion completed.
     * @api.example {}
     */
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

    /**
     * Anonymizes the profile in the given scope (fires anonymize events / clears identifying properties).
     *
     * @param profileId the profile identifier
     * @param scope scope used when raising anonymization events
     * @api.status 204 empty Profile anonymization requested.
     * @api.example {}
     */
    @POST
    @Path("/profiles/{profileId}/anonymize")
    public void anonymizeProfile(@PathParam("profileId") String profileId, @QueryParam("scope") String scope) {
        privacyService.anonymizeProfile(profileId, scope);
    }

    /**
     * Returns whether the profile requires anonymous browsing.
     *
     * @param profileId the profile identifier
     * @return {@code true} when anonymous browsing is required
     * @api.status 200 empty Anonymous-browsing flag.
     * @api.example false
     */
    @GET
    @Path("/profiles/{profileId}/anonymousBrowsing")
    public Boolean isAnonymousBrowsing(@PathParam("profileId") String profileId) {
        return privacyService.isRequireAnonymousBrowsing(profileId);
    }

    /**
     * Turns on anonymous browsing for the profile.
     * When {@code anonymizePastBrowsing=true}, past browsing data is anonymized first.
     *
     * @param profileId the profile identifier
     * @param past when {@code true}, anonymize historical browsing data before enabling the flag
     * @param scope scope used for related events
     * @return HTTP 200 when the flag was set; HTTP 500 when the service reports failure
     * @api.status 200 empty Anonymous browsing enabled.
     * @api.status 500 empty Failed to enable anonymous browsing.
     * @api.example {}
     */
    @POST
    @Path("/profiles/{profileId}/anonymousBrowsing")
    public Response activateAnonymousBrowsing(@PathParam("profileId") String profileId, @QueryParam("anonymizePastBrowsing") @DefaultValue("false") boolean past, @QueryParam("scope") String scope) {
        if (past) {
            privacyService.anonymizeBrowsingData(profileId);
        }
        Boolean r = privacyService.setRequireAnonymousBrowsing(profileId, true, scope);
        return r ? Response.ok().build() : Response.serverError().build();
    }

    /**
     * Turns off anonymous browsing for the profile.
     *
     * @param profileId the profile identifier
     * @param scope scope used for related events
     * @return HTTP 200 when the flag was cleared; HTTP 500 when the service reports failure
     * @api.status 200 empty Anonymous browsing disabled.
     * @api.status 500 empty Failed to disable anonymous browsing.
     * @api.example {}
     */
    @DELETE
    @Path("/profiles/{profileId}/anonymousBrowsing")
    public Response deactivateAnonymousBrowsing(@PathParam("profileId") String profileId, @QueryParam("scope") String scope) {
        Boolean r = privacyService.setRequireAnonymousBrowsing(profileId, false, scope);
        return r ? Response.ok().build() : Response.serverError().build();
    }

    /**
     * Returns event type ids that are filtered (not collected) for this profile.
     *
     * @param profileId the profile identifier
     * @return filtered event type identifiers
     * @api.status 200 array empty Event type id strings (may be empty).
     * @api.example ["view","login"]
     */
    @GET
    @Path("/profiles/{profileId}/eventFilters")
    public List<String> getEventFilters(@PathParam("profileId") String profileId) {
        return privacyService.getFilteredEventTypes(profileId);
    }

    /**
     * Replaces the list of filtered event types for this profile.
     *
     * @param profileId the profile identifier
     * @param eventFilters event type ids to filter (body JSON array of strings)
     * @return HTTP 200 when stored
     * @api.status 200 empty Event filters updated.
     * @api.example ["view","login"]
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/profiles/{profileId}/eventFilters")
    public Response setEventFilters(@PathParam("profileId") String profileId, List<String> eventFilters) {
        privacyService.setFilteredEventTypes(profileId, eventFilters);
        return Response.ok().build();
    }

    /**
     * Removes a single property from the profile (privacy erasure of one field).
     *
     * @param profileId the profile identifier
     * @param propertyName property path to remove (for example {@code email} or {@code properties.email})
     * @return HTTP 200 when removed
     * @api.status 200 empty Property removed from the profile.
     * @api.example {}
     */
    @DELETE
    @Path("/profiles/{profileId}/properties/{propertyName}")
    public Response removeProperty(@PathParam("profileId") String profileId, @PathParam("propertyName") String propertyName) {
        privacyService.removeProperty(profileId, propertyName);
        return Response.ok().build();
    }

}
