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

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.MetadataItem;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;

public class Rule extends MetadataItem {

    public static final String ITEM_TYPE = "rule";

    private Condition condition;

    private List<Action> actions;

    private List<String> linkedItems;

    private boolean raiseEventOnlyOnceForProfile = false;

    private boolean raiseEventOnlyOnceForSession = false;

    private int priority;

    public Rule() {
    }

    public Rule(Metadata metadata) {
        super(metadata);
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    public List<String> getLinkedItems() {
        return linkedItems;
    }

    public void setLinkedItems(List<String> linkedItems) {
        this.linkedItems = linkedItems;
    }

    public boolean isRaiseEventOnlyOnceForProfile() {
        return raiseEventOnlyOnceForProfile;
    }

    public void setRaiseEventOnlyOnceForProfile(boolean raiseEventOnlyOnceForProfile) {
        this.raiseEventOnlyOnceForProfile = raiseEventOnlyOnceForProfile;
    }

    public boolean isRaiseEventOnlyOnceForSession() {
        return raiseEventOnlyOnceForSession;
    }

    public void setRaiseEventOnlyOnceForSession(boolean raiseEventOnlyOnceForSession) {
        this.raiseEventOnlyOnceForSession = raiseEventOnlyOnceForSession;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
