package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;
import java.util.Map;

/**
 * Evaluator for NOT condition
 */
public class NotConditionEvaluator implements ConditionEvaluator {

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        Condition subCondition = (Condition) condition.getParameterValues().get("subCondition");
        return !dispatcher.eval(subCondition, item, context);
    }
}
