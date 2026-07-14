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
     * @api.status 200 array org.apache.unomi.api.Metadata Campaign metadata for all stored campaigns (may be empty).
     * @api.example [{"id":"summer-sale","name":"Summer Sale","scope":"mysite","enabled":true}]
     */
    @GET
    @Path("/")
    public Set<Metadata> getCampaignMetadatas() {
        return goalsService.getCampaignMetadatas();
    }

    /**
     * Saves the specified campaign in the context server and creates associated {@link Rule}s if the campaign is enabled.
     * Body is a full {@link Campaign}: {@code metadata}, {@code entryCondition} (JSON field {@code type} + {@code parameterValues}),
     * date range, cost, and optional {@code primaryGoal}.
     *
     * @param campaign the Campaign to be saved
     * @api.status 204 empty Campaign created or updated.
     * @api.status 400 empty Invalid or unreadable campaign body.
     * @api.example {"itemId":"summer-sale","itemType":"campaign","metadata":{"id":"summer-sale","name":"Summer Sale","scope":"mysite","enabled":true},"startDate":"2024-06-01T00:00:00.000Z","endDate":"2024-08-31T23:59:59.000Z","entryCondition":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}},"cost":5000.0,"currency":"USD","primaryGoal":"checkout-goal","timezone":"UTC"}
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
     * @api.status 200 array org.apache.unomi.api.Metadata Matching campaign metadata (may be empty).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example [{"id":"summer-sale","name":"Summer Sale","scope":"mysite","enabled":true}]
     */
    @POST
    @Path("/query")
    public Set<Metadata> getCampaignMetadatas(Query query) {
        return goalsService.getCampaignMetadatas(query);
    }

    /**
     * Returns detailed campaign data matching the given query.
     * Each list element includes engagement metrics and the embedded {@link Campaign}.
     *
     * @param query the query specifying which campaigns to include
     * @return a paged list of campaign details
     * @api.status 200 org.apache.unomi.api.PartialList Campaign details page (list items are CampaignDetail).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example {"list":[{"engagedProfiles":120,"campaignSessionViews":450,"campaignSessionSuccess":38,"numberOfGoals":2,"conversionRate":0.084,"campaign":{"itemId":"summer-sale","itemType":"campaign","metadata":{"id":"summer-sale","name":"Summer Sale","scope":"mysite","enabled":true}}}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @POST
    @Path("/query/detailed")
    public PartialList<CampaignDetail> getCampaignDetails(Query query) {
        return goalsService.getCampaignDetails(query);
    }

    /**
     * Returns detailed data for the campaign with the given ID.
     * When the campaign does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     *
     * @param campaignID the campaign identifier
     * @return the campaign detail, or {@code null} when no such campaign exists
     * @api.status 200 org.apache.unomi.api.campaigns.CampaignDetail Campaign detail found, or empty body when missing.
     * @api.example {"engagedProfiles":120,"campaignSessionViews":450,"campaignSessionSuccess":38,"numberOfGoals":2,"conversionRate":0.084,"campaign":{"itemId":"summer-sale","itemType":"campaign","metadata":{"id":"summer-sale","name":"Summer Sale","scope":"mysite","enabled":true}}}
     */
    @GET
    @Path("/{campaignID}/detailed")
    public CampaignDetail getCampaignDetail(@PathParam("campaignID") String campaignID) {
        return goalsService.getCampaignDetail(campaignID);
    }

    /**
     * Returns the campaign definition for the given ID.
     * When the campaign does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     *
     * @param campaignID the campaign identifier
     * @return the campaign, or {@code null} when no such campaign exists
     * @api.status 200 org.apache.unomi.api.campaigns.Campaign Campaign found, or empty body when missing.
     * @api.example {"itemId":"summer-sale","itemType":"campaign","metadata":{"id":"summer-sale","name":"Summer Sale","scope":"mysite","enabled":true},"startDate":"2024-06-01T00:00:00.000Z","endDate":"2024-08-31T23:59:59.000Z","entryCondition":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}},"cost":5000.0,"currency":"USD","primaryGoal":"checkout-goal","timezone":"UTC"}
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
     * @api.status 204 empty Campaign deleted.
     * @api.example {"itemId":"summer-sale","itemType":"campaign","metadata":{"id":"summer-sale","name":"Summer Sale","scope":"mysite","enabled":true},"startDate":"2024-06-01T00:00:00.000Z","endDate":"2024-08-31T23:59:59.000Z","entryCondition":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}},"cost":5000.0,"currency":"USD","primaryGoal":"checkout-goal","timezone":"UTC"}
     */
    @DELETE
    @Path("/{campaignID}")
    public void removeCampaignDefinition(@PathParam("campaignID") String campaignID) {
        goalsService.removeCampaign(campaignID);
    }

    /**
     * Saves the specified campaign event in the context server.
     * Body is a full {@link CampaignEvent}: {@code metadata}, {@code eventDate}, {@code campaignId}, and optional cost fields.
     *
     * @param campaignEvent the CampaignEvent to be saved
     * @api.status 204 empty Campaign event created or updated.
     * @api.status 400 empty Invalid or unreadable campaign event body.
     * @api.example {"itemId":"summer-launch","itemType":"campaignevent","metadata":{"id":"summer-launch","name":"Campaign launch","scope":"mysite","enabled":true},"eventDate":"2024-06-01T09:00:00.000Z","campaignId":"summer-sale","cost":1000.0,"currency":"USD","timezone":"UTC"}
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
     * @api.status 204 empty Campaign event deleted.
     * @api.example {"itemId":"summer-launch","itemType":"campaignevent","metadata":{"id":"summer-launch","name":"Campaign launch","scope":"mysite","enabled":true},"eventDate":"2024-06-01T09:00:00.000Z","campaignId":"summer-sale","cost":1000.0,"currency":"USD","timezone":"UTC"}
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
     * @api.status 200 org.apache.unomi.api.PartialList Campaign events page (list items are CampaignEvent).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example {"list":[{"itemId":"summer-launch","itemType":"campaignevent","metadata":{"id":"summer-launch","name":"Campaign launch","scope":"mysite","enabled":true},"eventDate":"2024-06-01T09:00:00.000Z","campaignId":"summer-sale","cost":1000.0,"currency":"USD","timezone":"UTC"}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @POST
    @Path("/events/query")
    public PartialList<CampaignEvent> getCampaignEvents(Query query) {
        return goalsService.getEvents(query);
    }
}
