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
    /**
     * Optional start condition for funnel-style goals (JSON wire field {@code type} + {@code parameterValues}).
     * @api.example {"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}}
     */
    private Condition startEvent;

    /**
     * Condition that marks the goal as accomplished (JSON wire field {@code type} + {@code parameterValues}).
     * @api.example {"type":"eventTypeCondition","parameterValues":{"eventTypeId":"purchase"}}
     */
    private Condition targetEvent;

    private String campaignId;

    /**
     * Default constructor.
     */
    public Goal() {
    }

    /**
     * Creates a goal with the given metadata.
     *
     * @param metadata the goal metadata
     */
    public Goal(Metadata metadata) {
        super(metadata);
    }

    /**
     * Optional start condition for funnel-style goals.
     * When set, goal tracking begins only after this condition is met.
     * In JSON the condition type id is usually the field {@code type} (not {@code conditionTypeId}).
     *
     * @return the start condition, or {@code null} if none is configured
     * @api.example {"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}}
     */
    public Condition getStartEvent() {
        return startEvent;
    }

    /**
     * Sets the optional start condition for funnel-style goals.
     *
     * @param startEvent the start condition, or {@code null} to clear it
     */
    public void setStartEvent(Condition startEvent) {
        this.startEvent = startEvent;
    }

    /**
     * Condition that marks the goal as accomplished when it matches.
     * In JSON the condition type id is usually the field {@code type} (not {@code conditionTypeId}).
     *
     * @return the target condition
     * @api.example {"type":"eventTypeCondition","parameterValues":{"eventTypeId":"purchase"}}
     */
    public Condition getTargetEvent() {
        return targetEvent;
    }

    /**
     * Sets the condition that marks the goal as accomplished.
     *
     * @param targetEvent the target condition, or {@code null} to clear it
     */
    public void setTargetEvent(Condition targetEvent) {
        this.targetEvent = targetEvent;
    }

    /**
     * Campaign id when this goal is scoped to a campaign.
     *
     * @return the campaign id, or {@code null} for scope-level goals
     */
    public String getCampaignId() {
        return campaignId;
    }

    /**
     * Sets the campaign id for campaign-scoped goals.
     *
     * @param campaignId the campaign id, or {@code null} for scope-level goals
     */
    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    /**
     * Converts this goal to a Map structure for YAML output.
     * Implements YamlConvertible interface with circular reference detection.
     *
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
