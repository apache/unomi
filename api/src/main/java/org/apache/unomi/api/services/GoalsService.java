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

package org.apache.unomi.api.services;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.campaigns.CampaignDetail;
import org.apache.unomi.api.campaigns.events.CampaignEvent;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.goals.GoalReport;
import org.apache.unomi.api.query.AggregateQuery;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;

import java.util.Set;

/**
 * A service to interact with {@link Goal}s and {@link Campaign}s.
 */
public interface GoalsService {
    /**
     * Retrieves the set of Metadata associated with existing goals.
     *
     * @return the set of Metadata associated with existing goals
     */
    Set<Metadata> getGoalMetadatas();

    /**
     * Retrieves the set of Metadata associated with existing goals matching the specified {@link Query}
     *
     * @param query the Query used to filter the Goals which metadata we want to retrieve
     * @return the set of Metadata associated with existing goals matching the specified {@link Query}
     */
    Set<Metadata> getGoalMetadatas(Query query);

    /**
     * Retrieves the goal associated with the specified identifier.
     *
     * @param goalId the identifier of the goal to retrieve
     * @return the goal associated with the specified identifier or {@code null} if no such goal exists
     */
    Goal getGoal(String goalId);

    /**
     * Saves the specified goal in the context server and creates associated {@link Rule}s if the goal is enabled.
     *
     * TODO: rename to saveGoal
     *
     * @param goal the Goal to be saved
     */
    void setGoal(Goal goal);

    /**
     * Removes the goal associated with the specified identifier, also removing associated rules if needed.
     *
     * @param goalId the identifier of the goal to be removed
     */
    void removeGoal(String goalId);

    /**
     * Retrieves the report for the goal identified with the specified identifier.
     *
     * @param goalId the identifier of the goal which report we want to retrieve
     * @return the report for the specified goal
     */
    GoalReport getGoalReport(String goalId);

    /**
     * Retrieves the report for the goal identified with the specified identifier, considering only elements determined by the specified {@link AggregateQuery}.
     *
     * @param goalId the identifier of the goal which report we want to retrieve
     * @param query  an AggregateQuery to further specify which elements of the report we want
     * @return the report for the specified goal and query
     */
    GoalReport getGoalReport(String goalId, AggregateQuery query);

    /**
     * Retrieves the set of Metadata associated with existing campaigns.
     *
     * @return the set of Metadata associated with existing campaigns
     */
    Set<Metadata> getCampaignMetadatas();

    /**
     * Retrieves the set of Metadata associated with existing campaign matching the specified {@link Query}
     *
     * @param query the Query used to filter the campagins which metadata we want to retrieve
     * @return the set of Metadata associated with existing campaigns matching the specified {@link Query}
     */
    Set<Metadata> getCampaignMetadatas(Query query);

    /**
     * Retrieves campaign details for campaigns matching the specified query.
     *
     * @param query the query specifying which campaigns to retrieve
     * @return a {@link PartialList} of campaign details for the campaigns matching the specified query
     */
    PartialList<CampaignDetail> getCampaignDetails(Query query);

    /**
     * Retrieves the {@link CampaignDetail} associated with the campaign identified with the specified identifier
     *
     * @param id the identifier of the campaign for which we want to retrieve the details
     * @return the CampaignDetail for the campaign identified by the specified identifier or {@code null} if no such campaign exists
     */
    CampaignDetail getCampaignDetail(String id);

    /**
     * Retrieves the campaign identified by the specified identifier
     *
     * @param campaignId the identifier of the campaign we want to retrieve
     * @return the campaign associated with the specified identifier or {@code null} if no such campaign exists
     */
    Campaign getCampaign(String campaignId);

    /**
     * Saves the specified campaign in the context server and creates associated {@link Rule}s if the campaign is enabled.
     *
     * TODO: rename to saveCampaign
     *
     * @param campaign the Campaign to be saved
     */
    void setCampaign(Campaign campaign);

    /**
     * Removes the campaign associated with the specified identifier, also removing associated rules if needed.
     *
     * @param campaignId the identifier of the campaign to be removed
     */
    void removeCampaign(String campaignId);

    /**
     * Retrieves {@link CampaignEvent}s matching the specified query.
     *
     * @param query the Query specifying which CampaignEvents to retrieve
     * @return a {@link PartialList} of campaign events matching the specified query
     */
    PartialList<CampaignEvent> getEvents(Query query);

    /**
     * Saves the specified campaign event in the context server.
     *
     * TODO: rename to saveCampaignEvent
     *
     * @param event the CampaignEvent to be saved
     */
    void setCampaignEvent(CampaignEvent event);

    /**
     * Removes the campaign event associated with the specified identifier.
     *
     * @param campaignEventId the identifier of the campaign event to be removed
     */
    void removeCampaignEvent(String campaignEventId);
}
