package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionType;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;

import java.util.Collection;

public class ParserHelper {

    public static void resolveConditionType(DefinitionsService definitionsService, Condition rootCondition) {
        if (rootCondition.getConditionType() == null) {
            ConditionType conditionType = definitionsService.getConditionType(rootCondition.getConditionTypeId());
            if (conditionType != null) {
                rootCondition.setConditionType(conditionType);
            }
        }
        // recursive call for sub-conditions as parameters
        for (Object parameterValue : rootCondition.getParameterValues().values()) {
            if (parameterValue instanceof Condition) {
                resolveConditionType(definitionsService, (Condition) parameterValue);
            } else if (parameterValue instanceof Collection) {
                Collection<Object> valueList = (Collection<Object>) parameterValue;
                for (Object value : valueList) {
                    if (value instanceof Condition) {
                        resolveConditionType(definitionsService, (Condition) value);
                    }
                }
            }
        }
    }

    public static void resolveActionType(DefinitionsService definitionsService, Action action) {
        if (action.getActionType() == null) {
            ActionType actionType = definitionsService.getActionType(action.getActionTypeId());
            if (actionType != null) {
                action.setActionType(actionType);
            }
        }
    }
}
