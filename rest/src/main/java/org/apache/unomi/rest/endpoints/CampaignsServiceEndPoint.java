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

package org.apache.unomi.rest.endpoints;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.campaigns.CampaignDetail;
import org.apache.unomi.api.campaigns.events.CampaignEvent;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.GoalsService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Set;

/**
 * A JAX-RS endpoint to manage {@link Campaign}s and related information.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/campaigns")
@Component(service=CampaignsServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class CampaignsServiceEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(CampaignsServiceEndPoint.class.getName());

    @Reference
    private GoalsService goalsService;

    /**
     * Creates the campaigns service endpoint.
     */
    public CampaignsServiceEndPoint() {
        LOGGER.info("Initializing campaigns service endpoint...");
    }

    /**
     * Sets the goals service.
     *
     * @param goalsService the goals service
     */
    public void setGoalsService(GoalsService goalsService) {
        this.goalsService = goalsService;
    }

    /**
     * Returns metadata for all campaigns.
     *
     * @return campaign metadata for every stored campaign
     */
    @GET
    @Path("/")
    public Set<Metadata> getCampaignMetadatas() {
        return goalsService.getCampaignMetadatas();
    }

    /**
     * Saves the specified campaign in the context server and creates associated {@link Rule}s if the campaign is enabled.
     *
     * @param campaign the Campaign to be saved
     */
    @POST
    @Path("/")
    public void setCampaignDefinition(Campaign campaign) {
        goalsService.setCampaign(campaign);
    }

    /**
     * Returns campaign metadata matching the given query.
     *
     * @param query the query used to filter campaigns
     * @return metadata for campaigns that match the query
     */
    @POST
    @Path("/query")
    public Set<Metadata> getCampaignMetadatas(Query query) {
        return goalsService.getCampaignMetadatas(query);
    }

    /**
     * Returns detailed campaign data matching the given query.
     *
     * @param query the query specifying which campaigns to include
     * @return a paged list of campaign details
     */
    @POST
    @Path("/query/detailed")
    public PartialList<CampaignDetail> getCampaignDetails(Query query) {
        return goalsService.getCampaignDetails(query);
    }

    /**
     * Returns detailed data for the campaign with the given ID.
     *
     * @param campaignID the campaign identifier
     * @return the campaign detail, or {@code null} when no such campaign exists
     */
    @GET
    @Path("/{campaignID}/detailed")
    public CampaignDetail getCampaignDetail(@PathParam("campaignID") String campaignID) {
        return goalsService.getCampaignDetail(campaignID);
    }

    /**
     * Returns the campaign definition for the given ID.
     *
     * @param campaignID the campaign identifier
     * @return the campaign, or {@code null} when no such campaign exists
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
     * Returns campaign events matching the given query.
     *
     * @param query the query specifying which campaign events to include
     * @return a paged list of matching campaign events
     */
    @POST
    @Path("/events/query")
    public PartialList<CampaignEvent> getCampaignEvents(Query query) {
        return goalsService.getEvents(query);
    }
}
