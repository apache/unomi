package org.oasis_open.wemi.context.server.persistence.elasticsearch;

import org.elasticsearch.index.query.QueryBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ConditionVisitor;

/**
 * Created by loom on 26.06.14.
 */
public class ConditionESQueryGeneratorVisitor extends ConditionVisitor {

    @Override
    public void visit(Condition condition) {
        condition.getConditionType().getName();
    }
}
