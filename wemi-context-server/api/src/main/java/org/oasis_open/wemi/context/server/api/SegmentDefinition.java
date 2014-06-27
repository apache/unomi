package org.oasis_open.wemi.context.server.api;

import org.oasis_open.wemi.context.server.api.conditions.Condition;
/**
 * Created by loom on 24.04.14.
 */
public class SegmentDefinition {

    Condition rootCondition;

    public SegmentDefinition() {
    }

    public Condition getRootCondition() {
        return rootCondition;
    }

    public void setRootCondition(Condition rootCondition) {
        this.rootCondition = rootCondition;
    }

}
