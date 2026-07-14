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

package org.apache.unomi.api.rules;

import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.utils.YamlUtils;
import org.apache.unomi.api.utils.YamlUtils.YamlConvertible;
import org.apache.unomi.api.utils.YamlUtils.YamlMapBuilder;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.unomi.api.utils.YamlUtils.circularRef;
import static org.apache.unomi.api.utils.YamlUtils.toYamlValue;

/**
 * A conditional set of actions to be executed in response to incoming events. Triggering of rules is guarded by a condition: the rule is only triggered if the associated
 * condition ({@link #getCondition()}) is satisfied. Once a rule triggers, a list of actions ({@link #getActions()} can be performed as consequences.
 *
 * When rules trigger, a specific event is raised so that other parts of unomi can react to it accordingly. We can control how that event should be raised using
 * {@link #isRaiseEventOnlyOnceForProfile()} and {@link #isRaiseEventOnlyOnceForSession()}.
 *
 * We could also specify a priority for our rule in case it needs to be executed before other ones when similar conditions match. This is accomplished using the
 * {@link #getPriority()} property.
 */
public class Rule extends MetadataItem implements YamlConvertible {

    /**
     * The Rule ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "rule";
    private static final long serialVersionUID = 3058739939263056507L;
    /**
     * Condition that must match before this rule's actions run.
     * Typical trigger: {@code eventTypeCondition} with {@code eventTypeId}.
     */
    private Condition condition;

    /**
     * Actions performed when the condition matches (ordered).
     * @api.example [{"type":"setPropertyAction","parameterValues":{"setPropertyName":"properties.isPremium","setPropertyValueBoolean":true,"storeInSession":false}}]
     */
    private List<Action> actions;

    /**
     * Identifiers of items linked to this rule (for example segments or goals that own it).
     * @api.example ["goal-welcome"]
     */
    private List<String> linkedItems;

    /**
     * When {@code true}, raise the rule-triggered event at most once per profile.
     * @api.example false
     */
    private boolean raiseEventOnlyOnceForProfile = false;

    /**
     * When {@code true}, raise the rule-triggered event at most once per session.
     * @api.example false
     */
    private boolean raiseEventOnlyOnceForSession = false;

    /**
     * When {@code true}, raise the rule-triggered event at most once overall.
     * @api.example false
     */
    private boolean raiseEventOnlyOnce = false;

    /**
     * Execution priority among matching rules (higher runs first when comparable).
     * @api.example 0
     */
    private int priority;

    /**
     * Default constructor.
     */
    public Rule() {
    }

    /**
     * Creates a rule with the given metadata.
     *
     * @param metadata the metadata
     */
    public Rule(Metadata metadata) {
        super(metadata);
    }

    /**
     * Condition that triggers the rule when satisfied.
     *
     * @return the condition that, when satisfied, triggers the rule.
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * Sets the condition that, when satisfied, triggers the rule..
     *
     * @param condition the condition that, when satisfied, triggers the rule.
     */
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    /**
     * Actions performed when this rule triggers.
     *
     * @return the actions to be performed when this rule triggers
     */
    public List<Action> getActions() {
        return actions;
    }

    /**
     * Sets the actions to be performed when this rule triggers.
     *
     * @param actions the actions to be performed when this rule triggers
     */
    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    /**
     * Identifiers of items linked to this rule.
     *
     * @return the linked items
     */
    public List<String> getLinkedItems() {
        return linkedItems;
    }

    /**
     * Sets the linked items.
     *
     * @param linkedItems the linked items
     */
    public void setLinkedItems(List<String> linkedItems) {
        this.linkedItems = linkedItems == null ? null : linkedItems.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Determines whether the event raised when the rule is triggered should only be raised once per {@link Profile}.
     *
     * @return {@code true} if the rule-triggered event should only be raised once per profile, {@code false} otherwise
     */
    public boolean isRaiseEventOnlyOnceForProfile() {
        return raiseEventOnlyOnceForProfile;
    }

    /**
     * Whether the rule-triggered event should be raised only once per incoming event.
     *
     * @return {@code true} if the rule-triggered event should only be raised once per event
     */
    public boolean isRaiseEventOnlyOnce() {
        return raiseEventOnlyOnce;
    }

    /**
     * Specifies whether the event raised when the rule is triggered should only be raised once per {@link Profile}.
     *
     * @param raiseEventOnlyOnceForProfile {@code true} if the rule-triggered event should only be raised once per profile, {@code false} otherwise
     */
    public void setRaiseEventOnlyOnceForProfile(boolean raiseEventOnlyOnceForProfile) {
        this.raiseEventOnlyOnceForProfile = raiseEventOnlyOnceForProfile;
    }

    /**
     * Determines whether the event raised when the rule is triggered should only be raised once per {@link Session}.
     *
     * @return {@code true} if the rule-triggered event should only be raised once per session, {@code false} otherwise
     */
    public boolean isRaiseEventOnlyOnceForSession() {
        return raiseEventOnlyOnceForSession;
    }

    /**
     * Specifies whether the event raised when the rule is triggered should only be raised once per {@link Session}.
     *
     * @param raiseEventOnlyOnceForSession {@code true} if the rule-triggered event should only be raised once per session, {@code false} otherwise
     */
    public void setRaiseEventOnlyOnceForSession(boolean raiseEventOnlyOnceForSession) {
        this.raiseEventOnlyOnceForSession = raiseEventOnlyOnceForSession;
    }

    /**
     * Specifies whether the event raised when the rule is triggered should only be raised once per {@link Event}.
     *
     * @param raiseEventOnlyOnce {@code true} if the rule-triggered event should only be raised once per event, {@code false} otherwise
     */
    public void setRaiseEventOnlyOnce(boolean raiseEventOnlyOnce) {
        this.raiseEventOnlyOnce = raiseEventOnlyOnce;
    }

    /**
     * Priority when this rule must run before others with similar conditions before other ones when similar conditions match.
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets the priority in case this Rule needs to be executed before other ones when similar conditions match.
     *
     * @param priority the priority
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Converts this rule to a Map structure for YAML output.
     * Implements YamlConvertible interface with circular reference detection.
     *
     * @param visited set of already visited objects to prevent infinite recursion (may be null)
     * @param maxDepth maximum recursion depth to prevent stack overflow
     * @return a Map representation of this rule
     */
    @Override
    public Map<String, Object> toYaml(Set<Object> visited, int maxDepth) {
        if (maxDepth <= 0) {
            return YamlMapBuilder.create()
                .put("condition", "<max depth exceeded>")
                .put("actions", "<max depth exceeded>")
                .put("priority", priority)
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
                .putIfNotNull("condition", condition != null ? toYamlValue(condition, visitedSet, maxDepth - 1) : null)
                .putIfNotEmpty("actions", actions != null ? (Collection<?>) toYamlValue(actions, visitedSet, maxDepth - 1) : null)
                .putIfNotEmpty("linkedItems", linkedItems)
                .putIf("raiseEventOnlyOnceForProfile", true, raiseEventOnlyOnceForProfile)
                .putIf("raiseEventOnlyOnceForSession", true, raiseEventOnlyOnceForSession)
                .putIf("raiseEventOnlyOnce", true, raiseEventOnlyOnce)
                .putIf("priority", priority, priority != 0)
                .build();
        } finally {
            visitedSet.remove(this);
        }
    }
}
