package org.oasis_open.contextserver.api.goals;

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.MetadataItem;
import org.oasis_open.contextserver.api.conditions.Condition;

/**
 * Created by toto on 08/08/14.
 */
public class Goal extends MetadataItem {
    public static final String ITEM_TYPE = "goal";

    private Condition startEvent;

    private Condition targetEvent;

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
}
