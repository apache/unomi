package org.oasis_open.wemi.context.server.impl.conditions;

import org.oasis_open.wemi.context.server.api.conditions.ConditionNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by loom on 26.05.14.
 */
public abstract class ListOperatorConditionNode extends ConditionNode {

    private String operator;
    protected List<ConditionNode> subConditionNodes = new ArrayList<ConditionNode>();

    protected ListOperatorConditionNode(String name, String operator, List<ConditionNode> subConditionNodes) {
        super(name);
        this.operator = operator;
        this.subConditionNodes = subConditionNodes;
    }
}
