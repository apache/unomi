package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.Map;

/**
 * Created by toto on 21/08/14.
 */
public class GoalMatchConditionESQueryBuilder implements ConditionESQueryBuilder {
    @Override
    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        final String goalId = (String) condition.getParameterValues().get("goalId");
        final Boolean reached = (Boolean) condition.getParameterValues().get("goalReached");

        if (reached) {
            return FilterBuilders.andFilter(
                    FilterBuilders.existsFilter("session.properties." + goalId + ".start.reached"),
                    FilterBuilders.scriptFilter("doc['session.properties." + goalId + ".target.reached'].value > doc['session.properties." + goalId + ".start.reached'].value").lang("groovy")
            );
        } else {
            return FilterBuilders.existsFilter("session.properties." + goalId + ".start.reached");
        }
    }
}
