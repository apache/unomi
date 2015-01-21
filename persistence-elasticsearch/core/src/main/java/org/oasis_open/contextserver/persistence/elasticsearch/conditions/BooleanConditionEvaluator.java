package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;
import java.util.Map;

/**
 * Evaluator for AND and OR conditions.
 */
public class BooleanConditionEvaluator implements ConditionEvaluator {

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context,
            ConditionEvaluatorDispatcher dispatcher) {
        boolean isAnd = "and".equalsIgnoreCase((String) condition.getParameterValues().get("operator"));
        @SuppressWarnings("unchecked")
        List<Condition> conditions = (List<Condition>) condition.getParameterValues().get("subConditions");
        for (Condition sub : conditions) {
            boolean eval = dispatcher.eval(sub, item, context);
            if (!eval && isAnd) {
                // And
                return false;
            } else if (eval && !isAnd) {
                // Or
                return true;
            }
        }
        return isAnd;
    }
}
