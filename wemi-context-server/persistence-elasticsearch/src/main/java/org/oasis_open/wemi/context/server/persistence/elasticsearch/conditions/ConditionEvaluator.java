package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
 * Condition evaluator interface
 */
public interface ConditionEvaluator {

    public boolean eval(Condition condition, Item item, ConditionEvaluatorDispatcher dispatcher);

}
