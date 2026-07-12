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

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.goals.Goal;

import java.util.Date;

/**
 * Time-bounded marketing program built around {@link org.apache.unomi.api.goals.Goal}s.
 * Campaigns track entry conditions, duration, and conversion metrics so teams
 * can measure ROI for a specific promotion or experiment.
 */
public class Campaign extends MetadataItem {
    /**
     * The Campaign ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "campaign";
    private static final long serialVersionUID = -1829542196982959946L;
    private Date startDate;

    private Date endDate;

    private Condition entryCondition;

    private Double cost;

    private String currency;

    private String primaryGoal;

    private String timezone;

    /**
     * Default constructor.
     */
    public Campaign() {
    }

    /**
     * Creates a campaign with the given metadata.
     *
     * @param metadata the campaign metadata
     */
    public Campaign(Metadata metadata) {
        super(metadata);
    }

    /**
     * When the campaign becomes active.
     *
     * @return the start date
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the campaign start date.
     *
     * @param startDate the start date
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * When the campaign stops being active.
     *
     * @return the end date
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the campaign end date.
     *
     * @param endDate the end date
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Condition a profile must satisfy to enter the campaign.
     *
     * @return the entry condition
     */
    public Condition getEntryCondition() {
        return entryCondition;
    }

    /**
     * Sets the campaign entry condition.
     *
     * @param entryCondition the entry condition
     */
    public void setEntryCondition(Condition entryCondition) {
        this.entryCondition = entryCondition;
    }

    /**
     * Reported cost of running this campaign.
     *
     * @return the campaign cost
     */
    public Double getCost() {
        return cost;
    }

    /**
     * Sets the campaign cost.
     *
     * @param cost the campaign cost
     */
    public void setCost(Double cost) {
        this.cost = cost;
    }

    /**
     * Currency code for {@link #getCost()}.
     *
     * @return the currency code
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the currency code for the campaign cost.
     *
     * @param currency the currency code
     */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /**
     * Identifier of the primary goal tracked for this campaign.
     *
     * @return the primary goal id
     */
    public String getPrimaryGoal() {
        return primaryGoal;
    }

    /**
     * Sets the primary goal id for this campaign.
     *
     * @param primaryGoal the primary goal id
     */
    public void setPrimaryGoal(String primaryGoal) {
        this.primaryGoal = primaryGoal;
    }

    /**
     * Time zone used to interpret {@link #getStartDate()} and {@link #getEndDate()}.
     *
     * @return the time zone id
     */
    public String getTimezone() {
        return timezone;
    }

    /**
     * Sets the time zone for campaign start and end dates.
     *
     * @param timezone the time zone id
     */
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
