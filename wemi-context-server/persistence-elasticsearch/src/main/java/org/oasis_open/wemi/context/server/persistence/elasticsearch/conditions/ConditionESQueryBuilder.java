package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
 * Created by toto on 27/06/14.
 */
public interface ConditionESQueryBuilder {

    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher);

}
