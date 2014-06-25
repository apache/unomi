package org.oasis_open.wemi.context.server.impl.conditions;

import org.oasis_open.wemi.context.server.api.conditions.ConditionNode;
import org.oasis_open.wemi.context.server.api.conditions.ConditionParameter;
import org.oasis_open.wemi.context.server.api.conditions.ConditionParameterValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by loom on 25.06.14.
 */
public class UserPropertyConditionNode extends ConditionNode {

    public UserPropertyConditionNode(String name) {
        super(name);
    }

    @Override
    public List<ConditionParameter> getParameters() {
        List<ConditionParameter> parameters = new ArrayList<ConditionParameter>();
        parameters.add(new ConditionParameter("string", "propertyName", "org.oasis_open.wemi.context.server.impl.conditions.initializers.UserPropertyChoiceListInitializer"));
        parameters.add(new ConditionParameter("string", "comparisonOperation", "org.oasis_open.wemi.context.server.impl.conditions.initializers.ComparisonOperatorChoiceListInitializer"));
        parameters.add(new ConditionParameter("string", "propertyValue", ""));
        return null;
    }

    @Override
    public Object eval(Object context, List<ConditionParameterValue> conditionParameterValues) {
        return null;
    }
}
