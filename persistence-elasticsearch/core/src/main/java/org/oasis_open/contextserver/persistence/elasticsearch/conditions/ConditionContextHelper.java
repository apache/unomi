package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

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
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) parseParameter(context, condition.getParameterValues());
        if (values == null) {
            return null;
        }
        Condition n = new Condition(condition.getConditionType());
        n.setParameterValues(values);
        return n;
    }

    @SuppressWarnings("unchecked")
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
                Object parameter = parseParameter(context, entry.getValue());
                if (parameter == null) {
                    return null;
                }
                values.put(entry.getKey(), parameter);
            }
            return values;
        } else if (value instanceof List) {
            List<Object> values = new ArrayList<Object>();
            for (Object o : ((List<?>) value)) {
                Object parameter = parseParameter(context, o);
                if (parameter != null) {
                    values.add(parameter);
                }
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
            for (Object o : ((Map<?, ?>) value).values()) {
                if (hasContextualParameter(o)) {
                    return true;
                }
            }
        } else if (value instanceof List) {
            for (Object o : ((List<?>) value)) {
                if (hasContextualParameter(o)) {
                    return true;
                }
            }
        }
        return false;
    }


}
