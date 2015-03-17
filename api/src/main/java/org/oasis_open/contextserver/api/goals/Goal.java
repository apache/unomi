package org.oasis_open.contextserver.api.goals;

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

public class Goal extends MetadataItem {
    private static final long serialVersionUID = 6131648013470949983L;

    public static final String ITEM_TYPE = "goal";

    private Condition startEvent;

    private Condition targetEvent;

    private String campaignId;

    public Goal() {
    }

    public Goal(Metadata metadata) {
        super(metadata);
    }


    public Condition getStartEvent() {
        return startEvent;
    }

    public void setStartEvent(Condition startEvent) {
        this.startEvent = startEvent;
    }

    public Condition getTargetEvent() {
        return targetEvent;
    }

    public void setTargetEvent(Condition targetEvent) {
        this.targetEvent = targetEvent;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }
}
