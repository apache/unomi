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

package org.apache.unomi.persistence.spi.conditions;

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.scripting.ScriptExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


import java.io.IOException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ConditionContextHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionContextHelper.class);

    private static final Map<Character, String> FOLD_MAPPING = new HashMap<>();

    static {
        try {
            loadMappingFile();
        } catch (IOException e) {
            LOGGER.error("Erreur lors du chargement du fichier de mapping", e);
        }
    }

    private static void loadMappingFile() throws IOException {
        try (InputStream is = ConditionContextHelper.class.getClassLoader().getResourceAsStream("mapping-FoldToASCII.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.contains("=>")) {
                    String[] parts = line.split("=>");
                    if (parts.length == 2) {
                        String unicodeStr = parts[0].trim();
                        String asciiStr = parts[1].trim();

                        if (unicodeStr.startsWith("\"\\u") && unicodeStr.endsWith("\"")) {
                            String hexCode = unicodeStr.substring(3, unicodeStr.length() - 1);
                            try {
                                char unicodeChar = (char) Integer.parseInt(hexCode, 16);

                                if (asciiStr.startsWith("\"") && asciiStr.endsWith("\"")) {
                                    String asciiValue = asciiStr.substring(1, asciiStr.length() - 1);
                                    FOLD_MAPPING.put(unicodeChar, asciiValue);
                                }
                            } catch (NumberFormatException e) {
                                LOGGER.warn("Format de code Unicode invalide: {}", hexCode);
                            }
                        }
                    }
                }
            }
        }
    }

    public static Condition getContextualCondition(Condition condition, Map<String, Object> context, ScriptExecutor scriptExecutor) {
        if (!hasContextualParameter(condition.getParameterValues())) {
            return condition;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) parseParameter(context, condition.getParameterValues(), scriptExecutor);
        if (values == null) {
            return null;
        }
        Condition n = new Condition(condition.getConditionType());
        n.setParameterValues(values);
        return n;
    }

    @SuppressWarnings("unchecked")
    private static Object parseParameter(Map<String, Object> context, Object value, ScriptExecutor scriptExecutor) {
        if (value instanceof String) {
            if (((String) value).startsWith("parameter::") || ((String) value).startsWith("script::")) {
                String s = (String) value;
                if (s.startsWith("parameter::")) {
                    return context.get(StringUtils.substringAfter(s, "parameter::"));
                } else if (s.startsWith("script::")) {
                    String script = StringUtils.substringAfter(s, "script::");
                    return scriptExecutor.execute(script, context);
                }
            }
        } else if (value instanceof Map) {
            Map<String, Object> values = new HashMap<String, Object>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                Object parameter = parseParameter(context, entry.getValue(), scriptExecutor);
                if (parameter == null) {
                    return null;
                }
                values.put(entry.getKey(), parameter);
            }
            return values;
        } else if (value instanceof List) {
            List<Object> values = new ArrayList<Object>();
            for (Object o : ((List<?>) value)) {
                Object parameter = parseParameter(context, o, scriptExecutor);
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

    public static String forceFoldToASCII(Object object) {
        if (object != null) {
            return foldToASCII(object.toString());
        }
        return null;
    }

    public static Collection<String> forceFoldToASCII(Collection<?> collection) {
        if (collection != null) {
            return collection.stream().map(ConditionContextHelper::forceFoldToASCII).collect(Collectors.toList());
        }
        return null;
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
        if (s == null) {
            return null;
        }

        s = s.toLowerCase();
        StringBuilder result = new StringBuilder(s.length());

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String mapped = FOLD_MAPPING.get(c);

            if (mapped != null) {
                result.append(mapped);
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    public static <T> Collection<T> foldToASCII(Collection<T> s) {
        if (s != null) {
            return s.stream().map(o -> {
                if (o instanceof String) {
                    return (T) ConditionContextHelper.foldToASCII((String) o);
                }
                return o;
            }).collect(Collectors.toCollection(ArrayList::new));
        }
        return null;
    }

}
