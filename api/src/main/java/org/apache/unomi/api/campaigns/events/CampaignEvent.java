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

package org.apache.unomi.api.campaigns.events;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.campaigns.Campaign;

import java.util.Date;

/**
 * A specific campaign event to help analyzing your key performance indicators by marking specific dates during your campaign.
 *
 * @author : rincevent Created : 17/03/15
 */
public class CampaignEvent extends MetadataItem {
    /**
     * The CampaignEvent ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "campaignevent";
    private static final long serialVersionUID = -20151703L;
    private Date eventDate;
    private String campaignId;
    private Double cost;
    private String currency;
    private String timezone;

    /**
     * Instantiates a new Campaign event.
     */
    public CampaignEvent() {
    }

    /**
     * Instantiates a new Campaign event with the specified metadata.
     *
     * @param metadata the metadata
     */
    public CampaignEvent(Metadata metadata) {
        super(metadata);
    }

    /**
     * Retrieves the cost associated with this campaign event.
     *
     * @return the cost associated with this campaign event
     */
    public Double getCost() {
        return cost;
    }

    /**
     * Sets the cost.
     *
     * @param cost the cost
     */
    public void setCost(Double cost) {
        this.cost = cost;
    }

    /**
     * Retrieves the currency.
     *
     * @return the currency
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the currency.
     *
     * @param currency the currency
     */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /**
     * Retrieves the event date.
     *
     * @return the event date
     */
    public Date getEventDate() {
        return eventDate;
    }

    /**
     * Sets the event date.
     *
     * @param eventDate the event date
     */
    public void setEventDate(Date eventDate) {
        this.eventDate = eventDate;
    }

    /**
     * Retrieves the identifier of the associated {@link Campaign}.
     *
     * @return the identifier of the associated {@link Campaign}
     */
    public String getCampaignId() {
        return campaignId;
    }

    /**
     * Sets the campaign id.
     *
     * @param campaignId the campaign id
     */
    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    /**
     * Retrieves the timezone.
     *
     * @return the timezone
     */
    public String getTimezone() {
        return timezone;
    }

    /**
     * Sets the timezone.
     *
     * @param timezone the timezone
     */
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
