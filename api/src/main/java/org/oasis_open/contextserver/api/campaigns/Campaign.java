package org.oasis_open.contextserver.api.campaigns;

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
import org.oasis_open.contextserver.api.MetadataItem;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Date;

public class Campaign extends MetadataItem {
    private static final long serialVersionUID = -1829542196982959946L;

    public static final String ITEM_TYPE = "campaign";

    private Date startDate;

    private Date endDate;

    private Condition entryCondition;

    private Double cost;

    private String currency;

    private String primaryGoal;

    private String timezone;

    public Campaign() {
    }

    public Campaign(Metadata metadata) {
        super(metadata);
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Condition getEntryCondition() {
        return entryCondition;
    }

    public void setEntryCondition(Condition entryCondition) {
        this.entryCondition = entryCondition;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPrimaryGoal() {
        return primaryGoal;
    }

    public void setPrimaryGoal(String primaryGoal) {
        this.primaryGoal = primaryGoal;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
