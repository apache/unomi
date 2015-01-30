package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.apache.commons.beanutils.BeanUtils;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Created by toto on 29/01/15.
 */
public class PageViewEventConditionEvaluator implements ConditionEvaluator {
    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        boolean result = ((Event)item).getEventType().equals("view");
        try {
            if (condition.getParameterValues().get("pagePath") != null && !"".equals(condition.getParameterValues().get("pagePath"))) {
                result &= BeanUtils.getProperty(item, "target.properties.pageInfo.pagePath").equals(condition.getParameterValues().get("pagePath"));
            }
        } catch (Exception e) {
            result = false;
        }
        try {
            if (condition.getParameterValues().get("language") != null && !"".equals(condition.getParameterValues().get("language"))) {
                result &= BeanUtils.getProperty(item, "target.properties.pageInfo.language").equals(condition.getParameterValues().get("language"));
            }
        } catch (Exception e) {
            result = false;
        }
        return result;
    }
}
