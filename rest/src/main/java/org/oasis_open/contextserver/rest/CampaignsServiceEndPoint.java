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
import org.oasis_open.contextserver.api.campaigns.events.CampaignEvent;
import org.oasis_open.contextserver.api.query.Query;
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
    public void setCampaignDefinition(Campaign campaign) {
        goalsService.setCampaign(campaign);
    }

    @POST
    @Path("/query")
    public Set<Metadata> getCampaignMetadatas(Query query) {
        return goalsService.getCampaignMetadatas(query);
    }

    @GET
    @Path("/{campaignID}")
    public Campaign getCampaignDefinition(@PathParam("campaignID") String campaignID) {
        return goalsService.getCampaign(campaignID);
    }

    @DELETE
    @Path("/{campaignID}")
    public void removeCampaignDefinition(@PathParam("campaignID") String campaignID) {
        goalsService.removeCampaign(campaignID);
    }

    @GET
    @Path("/{campaignID}/match")
    public PartialList<Profile> getMatchingIndividuals(@PathParam("campaignID") String campaignId, @QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return goalsService.getMatchingIndividuals(campaignId, offset, size, sortBy);
    }

    @GET
    @Path("/{campaignID}/count")
    public long getMatchingIndividualsCount(@PathParam("campaignID") String campaignId) {
        return goalsService.getMatchingIndividualsCount(campaignId);
    }

    @POST
    @Path("/event")
    public void setCampaignEventDefinition(CampaignEvent campaignEvent) {
        goalsService.setCampaignEvent(campaignEvent);
    }

    @DELETE
    @Path("/{campaignID}/event/{eventId}")
    public void removeCampaignDefinition(@PathParam("campaignID") String campaignID, @PathParam("eventId") String campaignEventID) {
        goalsService.removeCampaignEvent(campaignEventID);
    }

    @GET
    @Path("/{campaignID}/events")
    public PartialList<CampaignEvent> getCampaignEvents(@PathParam("campaignID") String campaignId, @QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return goalsService.getEvents(campaignId, offset, size, sortBy);
    }
}
