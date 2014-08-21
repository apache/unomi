package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
 * Created by loom on 21.08.14.
 */
public class LoginEventConditionESQueryBuilder implements ESQueryBuilder {
    @Override
    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        return FilterBuilders.termFilter("eventType", "login");
    }
}
