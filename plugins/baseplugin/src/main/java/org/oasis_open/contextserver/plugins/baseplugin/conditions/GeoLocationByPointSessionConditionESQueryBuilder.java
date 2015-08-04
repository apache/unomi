package org.oasis_open.contextserver.plugins.baseplugin.conditions;

/*
 * #%L
 * context-server-persistence-elasticsearch-core
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;

import java.util.Map;

public class GeoLocationByPointSessionConditionESQueryBuilder implements ConditionESQueryBuilder {
    @Override
    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        Double lat = (Double) condition.getParameter("latitude");
        Double lon = (Double) condition.getParameter("longitude");

        Double lat2 = (Double) condition.getParameter("latitude2");
        Double lon2 = (Double) condition.getParameter("longitude2");

        if (lat2 != null && lon2 != null) {
            return FilterBuilders.geoBoundingBoxFilter("location")
                    .bottomLeft(Math.min(lat, lat2), Math.min(lon, lon2))
                    .topRight(Math.max(lat, lat2), Math.max(lon, lon2));
        }

        String distance = condition.getParameter("distance").toString();

        return FilterBuilders.geoDistanceFilter("location")
                .lat(lat)
                .lon(lon)
                .distance(distance);
    }

}
