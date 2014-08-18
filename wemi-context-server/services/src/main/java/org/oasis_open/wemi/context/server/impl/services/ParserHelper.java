package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;
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

    public static void resolveConsequenceType(DefinitionsService definitionsService, Consequence consequence) {
        if (consequence.getConsequenceType() == null) {
            ConsequenceType consequenceType = definitionsService.getConsequenceType(consequence.getConsequenceTypeId());
            if (consequenceType != null) {
                consequence.setConsequenceType(consequenceType);
            }
        }
    }
}
