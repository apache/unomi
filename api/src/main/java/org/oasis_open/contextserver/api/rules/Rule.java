package org.oasis_open.contextserver.api.rules;

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

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.MetadataItem;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;

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
public class Rule extends MetadataItem {

    private static final long serialVersionUID = 3058739939263056507L;

    /**
     * The Rule ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "rule";

    private Condition condition;

    private List<Action> actions;

    private List<String> linkedItems;

    private boolean raiseEventOnlyOnceForProfile = false;

    private boolean raiseEventOnlyOnceForSession = false;

    private int priority;

    /**
     * Instantiates a new Rule.
     */
    public Rule() {
    }

    /**
     * Instantiates a new Rule with the specified {@link Metadata}.
     *
     * @param metadata the metadata
     */
    public Rule(Metadata metadata) {
        super(metadata);
    }

    /**
     * Retrieves the condition that, when satisfied, triggers the rule.
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
     * Retrieves the actions to be performed when this rule triggers.
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
     * Retrieves the linked items.
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
        this.linkedItems = linkedItems;
    }

    /**
     * Determines whether the event raised when the rule is triggered should only be raised once per {@link org.oasis_open.contextserver.api.Profile}.
     *
     * @return {@code true} if the rule-triggered event should only be raised once per profile, {@code false} otherwise
     */
    public boolean isRaiseEventOnlyOnceForProfile() {
        return raiseEventOnlyOnceForProfile;
    }

    /**
     * Specifies whether the event raised when the rule is triggered should only be raised once per {@link org.oasis_open.contextserver.api.Profile}.
     *
     * @param raiseEventOnlyOnceForProfile {@code true} if the rule-triggered event should only be raised once per profile, {@code false} otherwise
     */
    public void setRaiseEventOnlyOnceForProfile(boolean raiseEventOnlyOnceForProfile) {
        this.raiseEventOnlyOnceForProfile = raiseEventOnlyOnceForProfile;
    }

    /**
     * Determines whether the event raised when the rule is triggered should only be raised once per {@link org.oasis_open.contextserver.api.Session}.
     *
     * @return {@code true} if the rule-triggered event should only be raised once per session, {@code false} otherwise
     */
    public boolean isRaiseEventOnlyOnceForSession() {
        return raiseEventOnlyOnceForSession;
    }

    /**
     * Specifies whether the event raised when the rule is triggered should only be raised once per {@link org.oasis_open.contextserver.api.Session}.
     *
     * @param raiseEventOnlyOnceForSession {@code true} if the rule-triggered event should only be raised once per session, {@code false} otherwise
     */
    public void setRaiseEventOnlyOnceForSession(boolean raiseEventOnlyOnceForSession) {
        this.raiseEventOnlyOnceForSession = raiseEventOnlyOnceForSession;
    }

    /**
     * Retrieves the priority in case this Rule needs to be executed before other ones when similar conditions match.
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
}
