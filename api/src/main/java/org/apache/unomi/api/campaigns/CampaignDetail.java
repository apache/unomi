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
 * Details about a {@link Campaign}.
 *
 * Created by kevan on 03/06/15.
 */
public class CampaignDetail {
    private long engagedProfiles = 0;
    private long campaignSessionViews = 0;
    private long campaignSessionSuccess = 0;
    private long numberOfGoals = 0;
    private double conversionRate;
    private Campaign campaign;

    /**
     * Instantiates a new Campaign detail.
     *
     * @param campaign the campaign
     */
    public CampaignDetail(Campaign campaign) {
        this.campaign = campaign;
    }

    /**
     * Retrieves the number of engaged profiles.
     *
     * @return the number of engaged profiles
     */
    public long getEngagedProfiles() {
        return engagedProfiles;
    }

    /**
     * Sets the number of engaged profiles.
     *
     * @param engagedProfiles the number of engaged profiles
     */
    public void setEngagedProfiles(long engagedProfiles) {
        this.engagedProfiles = engagedProfiles;
    }

    /**
     * Retrieves the number of goals.
     *
     * @return the number of goals
     */
    public long getNumberOfGoals() {
        return numberOfGoals;
    }

    /**
     * Sets the number of goals.
     *
     * @param numberOfGoals the number of goals
     */
    public void setNumberOfGoals(long numberOfGoals) {
        this.numberOfGoals = numberOfGoals;
    }

    /**
     * Retrieves the conversion rate.
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
     * Retrieves the associated campaign.
     *
     * @return the associated campaign
     */
    public Campaign getCampaign() {
        return campaign;
    }

    /**
     * Sets the associated campaign.
     *
     * @param campaign the campaign
     */
    public void setCampaign(Campaign campaign) {
        this.campaign = campaign;
    }

    /**
     * Retrieves the number of campaign session views.
     *
     * @return the number of campaign session views
     */
    public long getCampaignSessionViews() {
        return campaignSessionViews;
    }

    /**
     * Sets the number of campaign session views.
     *
     * @param campaignSessionViews the number of campaign session views
     */
    public void setCampaignSessionViews(long campaignSessionViews) {
        this.campaignSessionViews = campaignSessionViews;
    }

    /**
     * Retrieves the number of campaign session successes.
     *
     * @return the number of campaign session successes
     */
    public long getCampaignSessionSuccess() {
        return campaignSessionSuccess;
    }

    /**
     * Sets the number of campaign session successes.
     *
     * @param campaignSessionSuccess the number of campaign session successes
     */
    public void setCampaignSessionSuccess(long campaignSessionSuccess) {
        this.campaignSessionSuccess = campaignSessionSuccess;
    }
}
