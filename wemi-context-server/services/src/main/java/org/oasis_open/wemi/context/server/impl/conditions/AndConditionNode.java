package org.oasis_open.wemi.context.server.impl.conditions;

import org.oasis_open.wemi.context.server.api.conditions.ConditionNode;
import org.oasis_open.wemi.context.server.api.conditions.ConditionParameter;
import org.oasis_open.wemi.context.server.api.conditions.ConditionParameterValue;

import java.util.List;

/**
 * Created by loom on 26.05.14.
 */
public class AndConditionNode extends ListOperatorConditionNode {
    protected AndConditionNode(List<ConditionNode> subConditionNodes) {
        super("AND", "AND", subConditionNodes);
    }

    @Override
    public List<ConditionParameter> getParameters() {
        return null;
    }

    @Override
    public Object eval(Object context, List<ConditionParameterValue> conditionParameterValues) {
        if (subConditionNodes == null || subConditionNodes.size() == 0) {
            return Boolean.FALSE;
        }
        for (ConditionNode subConditionNode : subConditionNodes) {
            Boolean subConditionNodeResult = (Boolean) subConditionNode.eval(context, conditionParameterValues);
            if (subConditionNodeResult == null || !subConditionNodeResult.booleanValue()) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }
}
