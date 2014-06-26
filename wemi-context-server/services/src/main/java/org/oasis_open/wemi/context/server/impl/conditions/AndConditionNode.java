package org.oasis_open.wemi.context.server.impl.conditions;

import org.oasis_open.wemi.context.server.api.conditions.ConditionNode;
import org.oasis_open.wemi.context.server.api.conditions.ConditionNodeVisitor;
import org.oasis_open.wemi.context.server.api.conditions.ConditionParameter;
import org.oasis_open.wemi.context.server.api.conditions.ConditionParameterValue;

import java.util.List;

/**
 * Created by loom on 26.05.14.
 */
public class AndConditionNode extends ListOperatorConditionNode {
    protected AndConditionNode(List<ConditionNode> subConditionNodes) {
        super("andOperator", "AND", "AND", subConditionNodes);
    }

}
