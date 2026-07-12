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

package org.apache.unomi.api.goals;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.utils.YamlUtils;
import org.apache.unomi.api.utils.YamlUtils.YamlConvertible;
import org.apache.unomi.api.utils.YamlUtils.YamlMapBuilder;

import java.util.Map;
import java.util.Set;

import static org.apache.unomi.api.utils.YamlUtils.circularRef;
import static org.apache.unomi.api.utils.YamlUtils.toYamlValue;

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
public class Goal extends MetadataItem implements YamlConvertible {
    /** The constant string used to identify this object as a goal item type. */
    public static final String ITEM_TYPE = "goal";
    private static final long serialVersionUID = 6131648013470949983L;
    private Condition startEvent;

    private Condition targetEvent;

    private String campaignId;

    /**
     * Constructs an empty {@link Goal} instance.
     * This constructor is typically used when initializing a goal object
     * programmatically before setting its specific metadata and events.
     */
    public Goal() {
    }

    /**
     * Constructs a {@link Goal} instance initialized with
     * the provided metadata.
     * to be identified or configured within a system context.
     * @param metadata The metadata to associate with this goal, allowing it
     */
    public Goal(Metadata metadata) {
        super(metadata);
    }

    /**
     * Retrieves the {@link Condition} determining the goal's start event if any, used for more complex goals where an action has to be accomplished first before evaluating the
     * success of the final goal (funnel goal for example).
     * @return the condition associated with the start event for this goal or {@code null} if no such event exists
     */
    public Condition getStartEvent() {
        return startEvent;
    }

    /**
     * Sets the condition that must occur for the goal to begin tracking.
     * This is used when the goal's success depends on an initial event
     * occurring, such as in a funnel scenario.
     * @param startEvent The {@link Condition} defining the starting event, or
     * null if no specific start event is required.
     */
    public void setStartEvent(Condition startEvent) {
        this.startEvent = startEvent;
    }

    /**
     * Retrieves the {@link Condition} determining the target event which needs to occur to consider the goal accomplished.
     * @return the condition associated with the event determining if the goal is reached or not
     */
    public Condition getTargetEvent() {
        return targetEvent;
    }

    /**
     * Sets the condition that must occur for the goal to be
     * considered accomplished.
     * This defines the target event that determines whether the
     * goal has been reached.
     * @param targetEvent The {@link Condition} defining the target event, or
     * null if no specific target event is required.
     */
    public void setTargetEvent(Condition targetEvent) {
        this.targetEvent = targetEvent;
    }

    /**
     * Retrieves the identifier of the campaign this goal is part of, if any.
     * @return the identifier of the campaign this goal is part of, or {@code null} if this goal is not part of any campaign
     */
    public String getCampaignId() {
        return campaignId;
    }

    /**
     * Sets the identifier of the campaign to which this goal belongs.
     * This allows goals to be scoped and managed within a specific
     * marketing campaign context.
     * @param campaignId The unique ID of the associated campaign, or
     * {@code null} if the goal is not tied to a campaign.
     */
    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    /**
     * Converts this goal to a Map structure for YAML output.
     * Implements YamlConvertible interface with circular reference detection.
     * @param visited set of already visited objects to prevent infinite recursion (may be null)
     * @return a Map representation of this goal
     */
    @Override
    public Map<String, Object> toYaml(Set<Object> visited, int maxDepth) {
        if (maxDepth <= 0) {
            return YamlMapBuilder.create()
                .put("startEvent", "<max depth exceeded>")
                .put("targetEvent", "<max depth exceeded>")
                .put("campaignId", campaignId)
                .build();
        }
        if (visited != null && visited.contains(this)) {
            return circularRef();
        }
        final Set<Object> visitedSet = visited != null ? visited : YamlUtils.newIdentityVisitedSet();
        visitedSet.add(this);
        try {
            return YamlMapBuilder.create()
                .mergeObject(super.toYaml(visitedSet, maxDepth))
                .putIfNotNull("startEvent", startEvent != null ? toYamlValue(startEvent, visitedSet, maxDepth - 1) : null)
                .putIfNotNull("targetEvent", targetEvent != null ? toYamlValue(targetEvent, visitedSet, maxDepth - 1) : null)
                .putIfNotNull("campaignId", campaignId)
                .build();
        } finally {
            visitedSet.remove(this);
        }
    }
}
