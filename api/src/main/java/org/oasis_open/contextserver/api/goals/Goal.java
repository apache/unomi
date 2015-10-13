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
import org.oasis_open.contextserver.api.campaigns.Campaign;
import org.oasis_open.contextserver.api.conditions.Condition;

/**
 * A tracked activity / action that can be accomplished by site (scope) visitors. These are tracked in general because they relate to specific business objectives or are
 * relevant to measure site/scope performance.
 * Goals can be defined at the scope level or in the context of a particular {@link Campaign}. Either types of goals behave exactly the same way with the exception of two
 * notable differences:
 * <ul>
 * <li>duration: scope-level goals are considered until removed while campaign-level goals are only considered for the campaign duration
 * <li>audience filtering: any visitor is considered for scope-level goals while campaign-level goals only consider visitors who match the campaign's conditions
 * </ul>
 */
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

    /**
     * Retrieves the {@link Condition} determining the goal's start event if any, used for more complex goals where an action has to be accomplished first before evaluating the
     * success of the final goal (funnel goal for example).
     *
     * @return the condition associated with the start event for this goal or {@code null} if no such event exists
     */
    public Condition getStartEvent() {
        return startEvent;
    }

    public void setStartEvent(Condition startEvent) {
        this.startEvent = startEvent;
    }

    /**
     * Retrieves the {@link Condition} determining the target event which needs to occur to consider the goal accomplished.
     *
     * @return the condition associated with the event determining if the goal is reached or not
     */
    public Condition getTargetEvent() {
        return targetEvent;
    }

    public void setTargetEvent(Condition targetEvent) {
        this.targetEvent = targetEvent;
    }

    /**
     * Retrieves the identifier of the campaign this goal is part of, if any.
     *
     * @return the identifier of the campaign this goal is part of, or {@code null} if this goal is not part of any campaign
     */
    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }
}
