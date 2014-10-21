package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.apache.commons.beanutils.BeanUtils;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.unit.DistanceUnit;
import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Created by loom on 12.09.14.
 */
public class GeoLocationByPointSessionConditionEvaluator implements ConditionEvaluator {

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        Double latitude1 = Double.parseDouble ((String) condition.getParameterValues().get("latitude"));
        Double longitude1 = Double.parseDouble ((String) condition.getParameterValues().get("longitude"));
        Double latitude2 = null;
        Double longitude2 = null;
        try {
            latitude2 = Double.parseDouble((String) BeanUtils.getProperty(item, "properties.location.lat"));
            longitude2 = Double.parseDouble((String) BeanUtils.getProperty(item, "properties.location.lon"));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        DistanceUnit.Distance distance = DistanceUnit.Distance.parseDistance((String) condition.getParameterValues().get("distance"));

        double d = GeoDistance.DEFAULT.calculate(latitude1, longitude1, latitude2, longitude2, distance.unit);
        return d < distance.value;
    }


}
