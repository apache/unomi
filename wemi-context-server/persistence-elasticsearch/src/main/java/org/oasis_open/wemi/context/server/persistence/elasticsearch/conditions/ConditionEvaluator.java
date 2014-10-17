package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.Map;

/**
 * Condition evaluator interface
 */
public interface ConditionEvaluator {

    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher);

}
