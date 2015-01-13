package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;
import java.util.Map;

/**
 * Evaluator for AND condition
 */
public class BooleanConditionEvaluator implements ConditionEvaluator {

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        boolean op = "and".equalsIgnoreCase((String) condition.getParameterValues().get("operator"));
        List<Condition> conditions = (List<Condition>) condition.getParameterValues().get("subConditions");
        for (Condition sub : conditions) {
            boolean eval = dispatcher.eval(sub, item, context);
            if (!eval && op) {
                // And
                return false;
            } else if (eval && !op) {
                // Or
                return true;
            }
        }
        return true;
    }
}
