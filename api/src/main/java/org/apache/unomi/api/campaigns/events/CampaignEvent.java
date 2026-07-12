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
 * A campaign milestone event used to mark dates and costs during a campaign for KPI analysis.
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
     * Default constructor.
     */
    public CampaignEvent() {
    }

    /**
     * Creates a campaign event with the given metadata.
     *
     * @param metadata the metadata
     */
    public CampaignEvent(Metadata metadata) {
        super(metadata);
    }

    /**
     * Cost associated with this campaign event.
     *
     * @return the event cost
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
     * Currency code for the event cost.
     *
     * @return the currency code
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
     * Date when this campaign event occurred.
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
     * Identifier of the associated {@link Campaign}.
     *
     * @return the campaign identifier
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
     * Timezone for interpreting the event date.
     *
     * @return the timezone identifier
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
