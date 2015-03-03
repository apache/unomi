package org.oasis_open.contextserver.api.rules;

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
