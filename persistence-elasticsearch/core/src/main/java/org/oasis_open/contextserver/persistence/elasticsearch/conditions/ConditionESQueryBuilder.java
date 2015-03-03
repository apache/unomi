package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Map;

public interface ConditionESQueryBuilder {

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher);

}
