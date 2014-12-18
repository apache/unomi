package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.mvel2.MVEL;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.HashMap;
import java.util.Map;

public class ConditionContextHelper {
    public static Condition getContextualCondition(Condition condition, Map<String, Object> context) {
        if (context.isEmpty() || !hasContextualParameter(condition.getParameterValues())) {
            return condition;
        }
        Map<String, Object> values = parseMap(context, condition.getParameterValues());
        Condition n = new Condition(condition.getConditionType());
        n.setParameterValues(values);
        return n;
    }

    private static Map<String, Object> parseMap(Map<String, Object> context, Map<String, Object> parameters) {
        Map<String, Object> values = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                if (s.startsWith("parameter::")) {
                    value = context.get(StringUtils.substringAfter(s, "parameter::"));
                } else if (s.startsWith("script::")) {
                    value = MVEL.eval(StringUtils.substringAfter(s, "script::"), context);
                }
            } else if (value instanceof Map) {
                value = parseMap(context, (Map<String, Object>) value);
            }
            values.put(entry.getKey(), value);
        }
        return values;
    }

    private static boolean hasContextualParameter(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                if (((String) value).startsWith("parameter::") || ((String) value).startsWith("script::")) {
                    return true;
                }
            } else if (value instanceof Map) {
                if (hasContextualParameter((Map<String, Object>) value)) {
                    return true;
                }
            }
        }
        return false;
    }


}
