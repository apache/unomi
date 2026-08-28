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
 * CRUD and reporting API for {@link org.apache.unomi.api.goals.Goal}s and
 * {@link Campaign}s. Manages goal definitions, campaign lifecycle, and
 * related statistics.
 */
public interface GoalsService {
    /**
     * Returns metadata for all goals.
     *
     * @return goal metadata entries
     */
    Set<Metadata> getGoalMetadatas();

    /**
     * Returns metadata for goals matching the given query.
     *
     * @param query filter for goals whose metadata should be returned
     * @return matching goal metadata entries
     */
    Set<Metadata> getGoalMetadatas(Query query);

    /**
     * Loads a goal by id.
     *
     * @param goalId goal identifier
     * @return matching goal, or {@code null} if none exists
     */
    Goal getGoal(String goalId);

    /**
     * Saves a goal and creates associated rules when the goal is enabled.
     *
     * The {@code setGoal} name is historical; a {@code saveGoal} alias may be added later.
     *
     * @param goal goal to save
     */
    void setGoal(Goal goal);

    /**
     * Deletes a goal and its associated rules when present.
     *
     * @param goalId goal identifier
     */
    void removeGoal(String goalId);

    /**
     * Builds a performance report for the given goal.
     *
     * @param goalId goal identifier
     * @return goal report
     */
    GoalReport getGoalReport(String goalId);

    /**
     * Builds a performance report for the given goal, filtered by an aggregate query.
     *
     * @param goalId goal identifier
     * @param query aggregate query limiting report elements
     * @return goal report for the query scope
     */
    GoalReport getGoalReport(String goalId, AggregateQuery query);

    /**
     * Returns metadata for all campaigns.
     *
     * @return campaign metadata entries
     */
    Set<Metadata> getCampaignMetadatas();

    /**
     * Returns metadata for campaigns matching the given query.
     *
     * @param query filter for campaigns whose metadata should be returned
     * @return matching campaign metadata entries
     */
    Set<Metadata> getCampaignMetadatas(Query query);

    /**
     * Returns detailed campaign records matching the given query.
     *
     * @param query filter for campaigns to return
     * @return matching campaign details
     */
    PartialList<CampaignDetail> getCampaignDetails(Query query);

    /**
     * Loads detailed campaign information by id.
     *
     * @param id campaign identifier
     * @return campaign details, or {@code null} if none exists
     */
    CampaignDetail getCampaignDetail(String id);

    /**
     * Loads a campaign by id.
     *
     * @param campaignId campaign identifier
     * @return matching campaign, or {@code null} if none exists
     */
    Campaign getCampaign(String campaignId);

    /**
     * Saves a campaign and creates associated rules when the campaign is enabled.
     *
     * The {@code setCampaign} name is historical; a {@code saveCampaign} alias may be added later.
     *
     * @param campaign campaign to save
     */
    void setCampaign(Campaign campaign);

    /**
     * Deletes a campaign and its associated rules when present.
     *
     * @param campaignId campaign identifier
     */
    void removeCampaign(String campaignId);

    /**
     * Searches campaign events matching the given query.
     *
     * @param query filter for campaign events to return
     * @return matching campaign events
     */
    PartialList<CampaignEvent> getEvents(Query query);

    /**
     * Saves a campaign event.
     *
     * The {@code setEvent} name is historical; a {@code saveCampaignEvent} alias may be added later.
     *
     * @param event campaign event to save
     */
    void setCampaignEvent(CampaignEvent event);

    /**
     * Deletes a campaign event by id.
     *
     * @param campaignEventId campaign event identifier
     */
    void removeCampaignEvent(String campaignEventId);
}
