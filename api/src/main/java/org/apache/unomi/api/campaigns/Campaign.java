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
 * A goal-oriented, time-limited marketing operation that needs to be evaluated for return on investment performance by tracking the ratio of visits to conversions.
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
     * Instantiates a new Campaign.
     */
    public Campaign() {
    }

    /**
     * Instantiates a new Campaign with the specified metadata.
     *
     * @param metadata the metadata
     */
    public Campaign(Metadata metadata) {
        super(metadata);
    }

    /**
     * Retrieves the start date for this Campaign.
     *
     * @return the start date
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date for this Campaign.
     *
     * @param startDate the start date
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * Retrieves the end date for this Campaign.
     *
     * @return the end date
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the end date for this Campaign.
     *
     * @param endDate the end date
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Retrieves the entry condition that must be satisfied for users to be considered as taking part of this Campaign.
     *
     * @return the entry condition that must be satisfied for users to be considered as taking part of this Campaign
     */
    public Condition getEntryCondition() {
        return entryCondition;
    }

    /**
     * Sets the entry condition that must be satisfied for users to be considered as taking part of this Campaign..
     *
     * @param entryCondition the entry condition that must be satisfied for users to be considered as taking part of this Campaign
     */
    public void setEntryCondition(Condition entryCondition) {
        this.entryCondition = entryCondition;
    }

    /**
     * Retrieves the cost incurred by this Campaign.
     *
     * @return the cost incurred by this Campaign
     */
    public Double getCost() {
        return cost;
    }

    /**
     * Sets the cost incurred by this Campaign.
     *
     * @param cost the cost incurred by this Campaign
     */
    public void setCost(Double cost) {
        this.cost = cost;
    }

    /**
     * Retrieves the currency associated to the Campaign's cost.
     *
     * @return the currency associated to the Campaign's cost
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the currency associated to the Campaign's cost.
     *
     * @param currency the currency associated to the Campaign's cost
     */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /**
     * Retrieves the identifier for this Campaign's primary {@link Goal}.
     *
     * @return the identifier for this Campaign's primary {@link Goal}
     */
    public String getPrimaryGoal() {
        return primaryGoal;
    }

    /**
     * Sets the identifier for this Campaign's primary {@link Goal}.
     *
     * @param primaryGoal the identifier for this Campaign's primary {@link Goal}
     */
    public void setPrimaryGoal(String primaryGoal) {
        this.primaryGoal = primaryGoal;
    }

    /**
     * Retrieves the timezone associated with this Campaign's start and end dates.
     *
     * @return the timezone associated with this Campaign's start and end dates
     */
    public String getTimezone() {
        return timezone;
    }

    /**
     * Sets the timezone associated with this Campaign's start and end dates.
     *
     * @param timezone the timezone associated with this Campaign's start and end dates
     */
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
