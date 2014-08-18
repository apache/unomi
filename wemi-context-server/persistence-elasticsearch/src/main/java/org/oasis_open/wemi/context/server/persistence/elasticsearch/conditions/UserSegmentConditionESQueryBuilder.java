package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.TermsFilterBuilder;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.List;

/**
 * Created by toto on 14/08/14.
 */
public class UserSegmentConditionESQueryBuilder implements ESQueryBuilder {

    public UserSegmentConditionESQueryBuilder() {
    }

    @Override
    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        final List<String> segment = (List<String>) condition.getParameterValues().get("segments");
        String matchType = (String) condition.getParameterValues().get("matchType");

        final TermsFilterBuilder builder = FilterBuilders.termsFilter("segments", segment.toArray(new String[segment.size()]));

        if (matchType != null) {
            if (matchType.equals("some")) {
                return builder.execution("bool");
            } else if (matchType.equals("none")) {
                return FilterBuilders.notFilter(builder);
            }
        }

        return builder.execution("and");
    }
}
