package org.oasis_open.contextserver.api.rules;

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;

/**
* Created by toto on 26/06/14.
*/
public class Rule extends Item {

    public static final String ITEM_TYPE = "rule";

    private Metadata metadata;

    private Condition condition;

    private List<Action> actions;

    private boolean raiseEventOnlyOnceForUser = false;

    private boolean raiseEventOnlyOnceForSession = false;

    public Rule() {
    }

    public Rule(Metadata metadata) {
        super(metadata.getIdWithScope());
        this.metadata = metadata;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.itemId = metadata.getIdWithScope();
        this.metadata = metadata;
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

    public boolean isRaiseEventOnlyOnceForUser() {
        return raiseEventOnlyOnceForUser;
    }

    public void setRaiseEventOnlyOnceForUser(boolean raiseEventOnlyOnceForUser) {
        this.raiseEventOnlyOnceForUser = raiseEventOnlyOnceForUser;
    }

    public boolean isRaiseEventOnlyOnceForSession() {
        return raiseEventOnlyOnceForSession;
    }

    public void setRaiseEventOnlyOnceForSession(boolean raiseEventOnlyOnceForSession) {
        this.raiseEventOnlyOnceForSession = raiseEventOnlyOnceForSession;
    }
}
