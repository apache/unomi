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

package org.apache.unomi.api.campaigns;

/**
 * Live campaign performance snapshot built from profiles, sessions, and goals.
 * Counts engaged profiles, session views and successes, linked goals, and an
 * overall conversion rate. {@link org.apache.unomi.api.services.GoalsService} and campaign REST endpoints
 * return this object for campaign dashboards.
 */
public class CampaignDetail {
    private long engagedProfiles = 0;
    private long campaignSessionViews = 0;
    private long campaignSessionSuccess = 0;
    private long numberOfGoals = 0;
    private double conversionRate;
    private Campaign campaign;

    /**
     * Creates campaign performance details for the given campaign.
     *
     * @param campaign the campaign being reported on
     */
    public CampaignDetail(Campaign campaign) {
        this.campaign = campaign;
    }

    /**
     * Number of profiles that engaged with the campaign.
     *
     * @return the engaged profile count
     */
    public long getEngagedProfiles() {
        return engagedProfiles;
    }

    /**
     * Sets the engaged profile count.
     *
     * @param engagedProfiles the engaged profile count
     */
    public void setEngagedProfiles(long engagedProfiles) {
        this.engagedProfiles = engagedProfiles;
    }

    /**
     * Number of goals linked to the campaign.
     *
     * @return the goal count
     */
    public long getNumberOfGoals() {
        return numberOfGoals;
    }

    /**
     * Sets the goal count.
     *
     * @param numberOfGoals the goal count
     */
    public void setNumberOfGoals(long numberOfGoals) {
        this.numberOfGoals = numberOfGoals;
    }

    /**
     * Overall conversion rate for the campaign.
     *
     * @return the conversion rate
     */
    public double getConversionRate() {
        return conversionRate;
    }

    /**
     * Sets the conversion rate.
     *
     * @param conversionRate the conversion rate
     */
    public void setConversionRate(double conversionRate) {
        this.conversionRate = conversionRate;
    }

    /**
     * Campaign these metrics describe.
     *
     * @return the campaign
     */
    public Campaign getCampaign() {
        return campaign;
    }

    /**
     * Sets the campaign reference.
     *
     * @param campaign the campaign
     */
    public void setCampaign(Campaign campaign) {
        this.campaign = campaign;
    }

    /**
     * Number of campaign sessions that recorded a view.
     *
     * @return the session view count
     */
    public long getCampaignSessionViews() {
        return campaignSessionViews;
    }

    /**
     * Sets the campaign session view count.
     *
     * @param campaignSessionViews the session view count
     */
    public void setCampaignSessionViews(long campaignSessionViews) {
        this.campaignSessionViews = campaignSessionViews;
    }

    /**
     * Number of campaign sessions that reached a success state.
     *
     * @return the session success count
     */
    public long getCampaignSessionSuccess() {
        return campaignSessionSuccess;
    }

    /**
     * Sets the campaign session success count.
     *
     * @param campaignSessionSuccess the session success count
     */
    public void setCampaignSessionSuccess(long campaignSessionSuccess) {
        this.campaignSessionSuccess = campaignSessionSuccess;
    }
}
