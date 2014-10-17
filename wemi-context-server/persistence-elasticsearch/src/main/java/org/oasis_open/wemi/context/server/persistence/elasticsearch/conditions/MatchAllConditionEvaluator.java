package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.Map;

/**
* Created by toto on 27/06/14.
*/
public class MatchAllConditionEvaluator implements ConditionEvaluator {

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        return true;
    }
}
