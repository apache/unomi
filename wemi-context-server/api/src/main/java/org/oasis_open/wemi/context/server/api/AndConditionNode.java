package org.oasis_open.wemi.context.server.api;

import java.util.List;

/**
 * Created by loom on 26.05.14.
 */
public class AndConditionNode extends ListOperatorConditionNode {
    protected AndConditionNode(List<ConditionNode> subConditionNodes) {
        super("AND", "AND", subConditionNodes);
    }

    @Override
    public Object eval(Object context) {
        if (subConditionNodes == null || subConditionNodes.size() == 0) {
            return Boolean.FALSE;
        }
        for (ConditionNode subConditionNode : subConditionNodes) {
            Boolean subConditionNodeResult = (Boolean) subConditionNode.eval(context);
            if (subConditionNodeResult == null || !subConditionNodeResult.booleanValue()) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }
}
