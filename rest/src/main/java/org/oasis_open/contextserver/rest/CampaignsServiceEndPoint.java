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
import org.oasis_open.contextserver.api.campaigns.Campaign;
import org.oasis_open.contextserver.api.campaigns.CampaignDetail;
import org.oasis_open.contextserver.api.campaigns.events.CampaignEvent;
import org.oasis_open.contextserver.api.query.Query;
import org.oasis_open.contextserver.api.services.GoalsService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Set;

/**
 * A JAX-RS endpoint to manage {@link Campaign}s and related information.
 */
@Path("/campaigns")
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

    /**
     * Retrieves the set of Metadata associated with existing campaigns.
     *
     * @return the set of Metadata associated with existing campaigns
     */
    @GET
    @Path("/")
    public Set<Metadata> getCampaignMetadatas() {
        return goalsService.getCampaignMetadatas();
    }

    /**
     * Saves the specified campaign in the context server and creates associated {@link org.oasis_open.contextserver.api.rules.Rule}s if the campaign is enabled.
     *
     * @param campaign the Campaign to be saved
     */
    @POST
    @Path("/")
    public void setCampaignDefinition(Campaign campaign) {
        goalsService.setCampaign(campaign);
    }

    /**
     * Retrieves the set of Metadata associated with existing campaign matching the specified {@link Query}
     *
     * @param query the Query used to filter the campagins which metadata we want to retrieve
     * @return the set of Metadata associated with existing campaigns matching the specified {@link Query}
     */
    @POST
    @Path("/query")
    public Set<Metadata> getCampaignMetadatas(Query query) {
        return goalsService.getCampaignMetadatas(query);
    }

    /**
     * Retrieves campaign details for campaigns matching the specified query.
     *
     * @param query the query specifying which campaigns to retrieve
     * @return a {@link PartialList} of campaign details for the campaigns matching the specified query
     */
    @POST
    @Path("/query/detailed")
    public PartialList<CampaignDetail> getCampaignDetails(Query query) {
        return goalsService.getCampaignDetails(query);
    }

    /**
     * Retrieves the {@link CampaignDetail} associated with the campaign identified with the specified identifier
     *
     * @param campaignID the identifier of the campaign for which we want to retrieve the details
     * @return the CampaignDetail for the campaign identified by the specified identifier or {@code null} if no such campaign exists
     */
    @GET
    @Path("/{campaignID}/detailed")
    public CampaignDetail getCampaignDetail(@PathParam("campaignID") String campaignID) {
        return goalsService.getCampaignDetail(campaignID);
    }

    /**
     * Retrieves the campaign identified by the specified identifier
     *
     * @param campaignID the identifier of the campaign we want to retrieve
     * @return the campaign associated with the specified identifier or {@code null} if no such campaign exists
     */
    @GET
    @Path("/{campaignID}")
    public Campaign getCampaignDefinition(@PathParam("campaignID") String campaignID) {
        return goalsService.getCampaign(campaignID);
    }

    /**
     * Removes the campaign associated with the specified identifier, also removing associated rules if needed.
     *
     * @param campaignID the identifier of the campaign to be removed
     */
    @DELETE
    @Path("/{campaignID}")
    public void removeCampaignDefinition(@PathParam("campaignID") String campaignID) {
        goalsService.removeCampaign(campaignID);
    }

    /**
     * Saves the specified campaign event in the context server.
     *
     * @param campaignEvent the CampaignEvent to be saved
     */
    @POST
    @Path("/event")
    public void setCampaignEventDefinition(CampaignEvent campaignEvent) {
        goalsService.setCampaignEvent(campaignEvent);
    }

    /**
     * Removes the campaign event associated with the specified identifier.
     *
     * @param campaignEventID the identifier of the campaign event to be removed
     */
    @DELETE
    @Path("/event/{eventId}")
    public void removeCampaignEventDefinition(@PathParam("eventId") String campaignEventID) {
        goalsService.removeCampaignEvent(campaignEventID);
    }

    /**
     * Retrieves {@link CampaignEvent}s matching the specified query.
     *
     * @param query the Query specifying which CampaignEvents to retrieve
     * @return a {@link PartialList} of campaign events matching the specified query
     */
    @POST
    @Path("/events/query")
    public PartialList<CampaignEvent> getCampaignEvents(Query query) {
        return goalsService.getEvents(query);
    }
}
