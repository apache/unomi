package org.oasis_open.contextserver.api.services;

/*
 * #%L
 * context-server-api
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

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.campaigns.Campaign;
import org.oasis_open.contextserver.api.campaigns.events.CampaignEvent;
import org.oasis_open.contextserver.api.goals.Goal;
import org.oasis_open.contextserver.api.goals.GoalReport;
import org.oasis_open.contextserver.api.query.AggregateQuery;

import java.util.List;
import java.util.Set;

public interface GoalsService {
    Set<Metadata> getGoalMetadatas();

    Set<Metadata> getGoalMetadatas(String scope);

    Goal getGoal(String scope, String goalId);

    Set<Metadata> getCampaignGoalMetadatas(String campaignId);

    void setGoal(Goal goal);

    void removeGoal(String scope, String goalId);

    GoalReport getGoalReport(String scope, String goalId);

    GoalReport getGoalReport(String scope, String goalId, AggregateQuery query);

    Set<Metadata> getCampaignMetadatas();

    Set<Metadata> getCampaignMetadatas(String scope);

    Campaign getCampaign(String scope, String campaignId);

    void setCampaign(Campaign campaign);

    void removeCampaign(String scope, String campaignId);

    PartialList<CampaignEvent> getEvents(String scope, String campaignId, int offset, int size, String sortBy);

    void setCampaignEvent(CampaignEvent event);

    void removeCampaignEvent(String scope, String campaignEventId);


    PartialList<Profile> getMatchingIndividuals(String scope, String campaignId, int offset, int size, String sortBy);

    long getMatchingIndividualsCount(String scope, String campaignId);

    Set<Metadata> getSiteGoalsMetadatas(String scope);
}
