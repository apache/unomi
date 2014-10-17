package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.List;

/**
 * Evaluator for AND condition
 */
public class AndConditionEvaluator implements ConditionEvaluator {

    @Override
    public boolean eval(Condition condition, Item item, ConditionEvaluatorDispatcher dispatcher) {
        List<Condition> conditions = (List<Condition>) condition.getParameterValues().get("subConditions");
        for (Condition sub : conditions) {
            if (!dispatcher.eval(sub, item)) {
                return false;
            }
        }
        return true;
    }
}
