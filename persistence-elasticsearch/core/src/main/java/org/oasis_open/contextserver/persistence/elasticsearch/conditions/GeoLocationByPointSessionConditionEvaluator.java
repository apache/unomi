package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.apache.commons.beanutils.BeanUtils;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.unit.DistanceUnit;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by loom on 12.09.14.
 */
public class GeoLocationByPointSessionConditionEvaluator implements ConditionEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(GeoLocationByPointSessionConditionEvaluator.class.getName());

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        try {
            Double latitude1 = Double.parseDouble((String) condition.getParameterValues().get("latitude"));
            Double longitude1 = Double.parseDouble((String) condition.getParameterValues().get("longitude"));
            Double latitude2 = Double.parseDouble(BeanUtils.getProperty(item, "properties.location.lat"));
            Double longitude2 = Double.parseDouble(BeanUtils.getProperty(item, "properties.location.lon"));
            DistanceUnit.Distance distance = DistanceUnit.Distance.parseDistance((String) condition.getParameterValues().get("distance"));

            double d = GeoDistance.DEFAULT.calculate(latitude1, longitude1, latitude2, longitude2, distance.unit);
            return d < distance.value;
        } catch (Exception e) {
            logger.debug("Cannot get properties", e);
        }
        return false;
    }


}
