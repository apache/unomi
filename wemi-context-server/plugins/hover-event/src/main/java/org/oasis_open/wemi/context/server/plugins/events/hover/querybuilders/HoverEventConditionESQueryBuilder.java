package org.oasis_open.wemi.context.server.plugins.events.hover.querybuilders;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
* Created by toto on 27/06/14.
*/
public class HoverEventConditionESQueryBuilder implements ConditionESQueryBuilder {

    public HoverEventConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        List<FilterBuilder> filters = new ArrayList<FilterBuilder>();
        filters.add(FilterBuilders.termFilter("eventType", "hover"));
        String targetId = (String) condition.getParameterValues().get("targetId");
        String targetPath = (String) condition.getParameterValues().get("targetPath");

        if (targetId != null && targetId.trim().length() > 0) {
            filters.add(FilterBuilders.termFilter("target.id", targetId));
        } else if (targetPath != null && targetPath.trim().length() > 0) {
            filters.add(FilterBuilders.termFilter("target.properties.path", targetPath));
        } else {
            filters.add(FilterBuilders.termFilter("target.id", ""));
        }
        return FilterBuilders.andFilter(filters.toArray(new FilterBuilder[filters.size()]));
    }
}
