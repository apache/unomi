package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.TermsFilterBuilder;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.List;
import java.util.Map;

/**
 * Created by toto on 14/08/14.
 */
public class MultipleValuesPropertyMatchConditionESQueryBuilder implements ConditionESQueryBuilder {

    public MultipleValuesPropertyMatchConditionESQueryBuilder() {
    }

    @Override
    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        String name = (String) condition.getParameterValues().get("propertyName");
        String matchType = (String) condition.getParameterValues().get("matchType");
        final List<String> values = (List<String>) condition.getParameterValues().get("propertyValues");

        final TermsFilterBuilder builder = FilterBuilders.termsFilter(name, values.toArray(new String[values.size()]));

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
