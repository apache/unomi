package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by loom on 12.09.14.
 */
public class GeoLocationSessionConditionESQueryBuilder implements ESQueryBuilder {
    @Override
    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        List<String> countryList = (List<String>) condition.getParameterValues().get("countries");
        List<FilterBuilder> subFilters = new ArrayList<FilterBuilder>();
        for (String country : countryList) {
            FilterBuilder countryFilterBuilder = FilterBuilders.termFilter("countryCode", country);
            subFilters.add(countryFilterBuilder);
        }
        OrFilterBuilder orFilterBuilder = FilterBuilders.orFilter(subFilters.toArray(new FilterBuilder[subFilters.size()]));
        return orFilterBuilder;
    }

}
