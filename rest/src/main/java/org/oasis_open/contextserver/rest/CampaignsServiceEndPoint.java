package org.oasis_open.contextserver.rest;

/*
 * #%L
 * context-server-rest
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.campaigns.Campaign;
import org.oasis_open.contextserver.api.services.GoalsService;
import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Set;

@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class CampaignsServiceEndPoint {

    private GoalsService goalsService;

    public CampaignsServiceEndPoint() {
        System.out.println("Initializing campaigns service endpoint...");
    }

    @WebMethod(exclude=true)
    public void setGoalsService(GoalsService goalsService) {
        this.goalsService = goalsService;
    }

    @GET
    @Path("/")
    public Set<Metadata> getCampaignMetadatas() {
        return goalsService.getCampaignMetadatas();
    }

    @POST
    @Path("/")
    public void setCampaignDefinition(Campaign segment) {
        goalsService.setCampaign(segment);
    }

    @GET
    @Path("/{scope}")
    public Set<Metadata> getCampaignMetadatas(@PathParam("scope") String scope) {
        return goalsService.getCampaignMetadatas(scope);
    }

    @GET
    @Path("/{scope}/{campaignID}")
    public Campaign getCampaignDefinition(@PathParam("scope") String scope, @PathParam("campaignID") String campaignID) {
        return goalsService.getCampaign(scope, campaignID);
    }

    @DELETE
    @Path("/{scope}/{campaignID}")
    public void removeCampaignDefinition(@PathParam("scope") String scope, @PathParam("campaignID") String campaignID) {
        goalsService.removeCampaign(scope, campaignID);
    }

    @GET
    @Path("/{scope}/{campaignID}/match")
    public PartialList<Profile> getMatchingIndividuals(@PathParam("scope") String scope, @PathParam("campaignID") String campaignId, @QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return goalsService.getMatchingIndividuals(scope, campaignId, offset, size, sortBy);
    }

    @GET
    @Path("/{scope}/{campaignID}/count")
    public long getMatchingIndividualsCount(@PathParam("scope") String scope, @PathParam("campaignID") String campaignId) {
        return goalsService.getMatchingIndividualsCount(scope, campaignId);
    }

}
