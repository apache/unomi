package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ConditionVisitor;

import java.util.Stack;

/**
 * Created by toto on 27/06/14.
 */
public abstract class AbstractESQueryGeneratorVisitor {

    public abstract String getConditionId();

    public abstract void visit(Condition condition, Stack<FilterBuilder> stack);

}
