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
public class GeoLocationByPointSessionConditionESQueryBuilder implements ESQueryBuilder {
    @Override
    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        String latitude = (String) condition.getParameterValues().get("latitude");
        String longitude = (String) condition.getParameterValues().get("longitude");
        String distance = (String) condition.getParameterValues().get("distance");
        return FilterBuilders.geoDistanceFilter("location")
                .lat(Double.parseDouble(latitude))
                .lon(Double.parseDouble(longitude))
                .distance(distance);
    }

}
