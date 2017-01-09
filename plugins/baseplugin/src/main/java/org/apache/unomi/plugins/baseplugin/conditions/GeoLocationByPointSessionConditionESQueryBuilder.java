/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.plugins.baseplugin.conditions;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Map;

public class GeoLocationByPointSessionConditionESQueryBuilder implements ConditionESQueryBuilder {
    @Override
    public QueryBuilder buildQuery(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        String type = (String) condition.getParameter("type");

        if("circle".equals(type)) {
            Double circleLatitude = (Double) condition.getParameter("circleLatitude");
            Double circleLongitude = (Double) condition.getParameter("circleLongitude");
            String distance = condition.getParameter("distance").toString();

            if(circleLatitude != null && circleLongitude != null && distance != null) {
                return QueryBuilders.geoDistanceQuery("location")
                        .point(circleLatitude, circleLongitude)
                        .distance(distance);
            }
        } else if("rectangle".equals(type)) {
            Double rectLatitudeNE = (Double) condition.getParameter("rectLatitudeNE");
            Double rectLongitudeNE = (Double) condition.getParameter("rectLongitudeNE");
            Double rectLatitudeSW = (Double) condition.getParameter("rectLatitudeSW");
            Double rectLongitudeSW = (Double) condition.getParameter("rectLongitudeSW");

            if(rectLatitudeNE != null && rectLongitudeNE != null && rectLatitudeSW != null && rectLongitudeSW != null) {
                return QueryBuilders.geoBoundingBoxQuery("location")
                        .setCorners(rectLatitudeNE, rectLongitudeNE,rectLatitudeSW, rectLongitudeSW);
            }
        }

        return null;
    }

}
