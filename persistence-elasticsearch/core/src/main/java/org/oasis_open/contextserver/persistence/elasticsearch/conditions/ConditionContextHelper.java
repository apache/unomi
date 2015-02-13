package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.apache.commons.lang3.StringUtils;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.mvel2.MVEL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConditionContextHelper {
    public static Condition getContextualCondition(Condition condition, Map<String, Object> context) {
        if (context.isEmpty() || !hasContextualParameter(condition.getParameterValues())) {
            return condition;
        }
        Map<String, Object> values = (Map<String, Object>) parseParameter(context, condition.getParameterValues());
        Condition n = new Condition(condition.getConditionType());
        n.setParameterValues(values);
        return n;
    }

    private static Object parseParameter(Map<String, Object> context, Object value) {
        if (value instanceof String) {
            if (((String) value).startsWith("parameter::") || ((String) value).startsWith("script::")) {
                String s = (String) value;
                if (s.startsWith("parameter::")) {
                    return context.get(StringUtils.substringAfter(s, "parameter::"));
                } else if (s.startsWith("script::")) {
                    return MVEL.eval(StringUtils.substringAfter(s, "script::"), context);
                }
            }
        } else if (value instanceof Map) {
            Map<String, Object> values = new HashMap<String, Object>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                values.put(entry.getKey(), parseParameter(context, entry.getValue()));
            }
            return values;
        } else if (value instanceof List) {
            List values = new ArrayList();
            for (Object o : ((List) value)) {
                values.add(parseParameter(context, o));
            }
            return values;
        }
        return value;
    }

    private static boolean hasContextualParameter(Object value) {
        if (value instanceof String) {
            if (((String) value).startsWith("parameter::") || ((String) value).startsWith("script::")) {
                return true;
            }
        } else if (value instanceof Map) {
            for (Object o : ((Map) value).values()) {
                if (hasContextualParameter(o)) {
                    return true;
                }
            }
        } else if (value instanceof List) {
            for (Object o : ((List) value)) {
                if (hasContextualParameter(o)) {
                    return true;
                }
            }
        }
        return false;
    }


}
