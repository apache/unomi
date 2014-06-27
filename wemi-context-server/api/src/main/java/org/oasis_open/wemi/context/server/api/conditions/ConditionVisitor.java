package org.oasis_open.wemi.context.server.api.conditions;

/**
 * Created by loom on 26.06.14.
 */
public abstract class ConditionVisitor {

    public abstract void visit(Condition condition);
}
