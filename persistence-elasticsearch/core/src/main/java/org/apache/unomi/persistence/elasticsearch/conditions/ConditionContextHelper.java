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

package org.apache.unomi.persistence.elasticsearch.conditions;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.util.ArrayUtil;
import org.apache.unomi.api.conditions.Condition;
import org.mvel2.MVEL;
import org.mvel2.ParserConfiguration;
import org.mvel2.ParserContext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConditionContextHelper {
    private static Map<String,Serializable> mvelExpressions = new ConcurrentHashMap<>();

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
                    String script = StringUtils.substringAfter(s, "script::");
                    if (!mvelExpressions.containsKey(script)) {
                        ParserConfiguration parserConfiguration = new ParserConfiguration();
                        parserConfiguration.setClassLoader(ConditionContextHelper.class.getClassLoader());
                        mvelExpressions.put(script,MVEL.compileExpression(script, new ParserContext(parserConfiguration)));
                    }
                    return MVEL.executeExpression(mvelExpressions.get(script), context);
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

    public static String[] foldToASCII(String[] s) {
        if (s != null) {
            for (int i = 0; i < s.length; i++) {
                s[i] = foldToASCII(s[i]);
            }
        }
        return s;
    }

    public static String foldToASCII(String s) {
        if (s != null) {
            s = s.toLowerCase();
            int maxSizeNeeded = 4 * s.length();
            char[] output = new char[ArrayUtil.oversize(maxSizeNeeded, 2)];
            int length = ASCIIFoldingFilter.foldToASCII(s.toCharArray(), 0, output, 0, s.length());
            return new String(output, 0, length);
        }
        return null;
    }

    public static <T> List<T> foldToASCII(List<T> s) {
        if (s != null) {
            return Lists.transform(s, new Function<T, T>() {
                @Override
                public T apply(T o) {
                    if (o instanceof String) {
                        return (T) ConditionContextHelper.foldToASCII((String) o);
                    }
                    return o;
                }
            });
        }
        return null;
    }

}
