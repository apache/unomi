package org.oasis_open.contextserver.plugins.pastevent.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.Session;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by toto on 11/08/14.
 */
public class PastEventConditionESQueryBuilder implements ConditionESQueryBuilder {
    public PastEventConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        Map<String, Object> parameters = condition.getParameterValues();

        Integer minimumEventCount = !parameters.containsKey("minimumEventCount") ? 0 : (Integer) parameters.get("minimumEventCount");
        Integer maximumEventCount = !parameters.containsKey("maximumEventCount") ? Integer.MAX_VALUE : (Integer) parameters.get("maximumEventCount");

        if (minimumEventCount  > 0 && maximumEventCount < Integer.MAX_VALUE) {
            return FilterBuilders.rangeFilter("properties." + parameters.get("generatedPropertyKey"))
                    .gte(minimumEventCount).lte(maximumEventCount);
        } else {
            return FilterBuilders.existsFilter("properties." + parameters.get("generatedPropertyKey"));
        }
    }
}
