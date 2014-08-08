package org.oasis_open.wemi.context.server.api.goals;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
 * Created by toto on 08/08/14.
 */
public class Goal {
    private Metadata metadata;

    private Condition startEvent;

    private Condition targetEvent;

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
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
