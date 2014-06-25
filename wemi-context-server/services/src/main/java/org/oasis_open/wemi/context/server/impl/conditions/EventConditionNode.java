package org.oasis_open.wemi.context.server.impl.conditions;

import org.oasis_open.wemi.context.server.api.conditions.ConditionNode;
import org.oasis_open.wemi.context.server.api.conditions.ConditionParameter;
import org.oasis_open.wemi.context.server.api.conditions.ConditionParameterValue;

import java.util.List;

/**
 * Condition node that checks if an event was received, to be able to either build segments or
 * rules for listening to events and performing actions when an event is received
 */
public class EventConditionNode extends ConditionNode {
    public EventConditionNode(String name) {
        super(name);
    }

    @Override
    public List<ConditionParameter> getParameters() {
        return null;
    }

    @Override
    public Object eval(Object context, List<ConditionParameterValue> conditionParameterValues) {
        return null;
    }
}
