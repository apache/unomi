package org.oasis_open.wemi.context.server.api.consequences;

import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
 * Created by toto on 27/06/14.
 */
public abstract class ConsequenceVisitor {

    public abstract void visit(Consequence consequence);

}
