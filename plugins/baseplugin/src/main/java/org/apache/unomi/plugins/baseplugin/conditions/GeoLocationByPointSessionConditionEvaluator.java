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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluator;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.persistence.spi.conditions.geo.DistanceUnit;
import org.apache.unomi.persistence.spi.conditions.geo.GeoDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class GeoLocationByPointSessionConditionEvaluator implements ConditionEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeoLocationByPointSessionConditionEvaluator.class.getName());

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        try {
            String type = (String) condition.getParameter("type");
            String name = condition.getParameter("name") == null ? "properties.location" : (String) condition.getParameter("name");

            double latitudeProperty = Double.parseDouble(BeanUtils.getProperty(item, name + ".lat"));
            double longitudeProperty = Double.parseDouble(BeanUtils.getProperty(item, name + ".lon"));


            if("circle".equals(type)) {
                Double circleLatitude = (Double) condition.getParameter("circleLatitude");
                Double circleLongitude = (Double) condition.getParameter("circleLongitude");
                DistanceUnit.Distance distance = DistanceUnit.Distance.parseDistance(condition.getParameter("distance").toString());

                double d = GeoDistance.PLANE.calculate(circleLatitude, circleLongitude, latitudeProperty, longitudeProperty, distance.unit);
                return d < distance.value;
            } else if("rectangle".equals(type)) {
                Double rectLatitudeNE = (Double) condition.getParameter("rectLatitudeNE");
                Double rectLongitudeNE = (Double) condition.getParameter("rectLongitudeNE");
                Double rectLatitudeSW = (Double) condition.getParameter("rectLatitudeSW");
                Double rectLongitudeSW = (Double) condition.getParameter("rectLongitudeSW");

                if(rectLatitudeNE != null && rectLongitudeNE != null && rectLatitudeSW != null && rectLongitudeSW != null) {
                    return latitudeProperty < Math.max(rectLatitudeNE, rectLatitudeSW)  &&
                            latitudeProperty > Math.min(rectLatitudeNE, rectLatitudeSW) &&
                            longitudeProperty < Math.max(rectLongitudeNE, rectLongitudeSW) &&
                            longitudeProperty > Math.min(rectLongitudeNE, rectLongitudeSW);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Cannot evaluate condition, properties 'properties.location.lat' or 'properties.location.lon' not found, enable debug log level to see full stacktrace");
            LOGGER.debug("Cannot evaluate condition", e);
        }
        return false;
    }


}
