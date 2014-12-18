package org.oasis_open.contextserver.api.goals;

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;

/**
 * Created by toto on 08/08/14.
 */
public class Goal extends Item {
    public static final String ITEM_TYPE = "goal";

    private Metadata metadata;

    private Condition startEvent;

    private Condition targetEvent;

    public Goal() {
    }

    public Goal(Metadata metadata) {
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
}
