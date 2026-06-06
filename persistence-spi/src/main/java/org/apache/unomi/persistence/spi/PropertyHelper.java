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

package org.apache.unomi.persistence.spi;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.NestedNullException;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.beanutils.expression.DefaultResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper method for properties
 */
public class PropertyHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyHelper.class.getName());
    private static DefaultResolver resolver = new DefaultResolver();

    public static boolean setProperty(Object target, String propertyName, Object propertyValue, String setPropertyStrategy) {
        try {
            // Handle remove
            String parentPropertyName;
            if (setPropertyStrategy != null && setPropertyStrategy.equals("remove")) {
                if (resolver.hasNested(propertyName)) {
                    parentPropertyName = propertyName.substring(0, propertyName.lastIndexOf('.'));
                    try {
                        Object parentPropertyValue = PropertyUtils.getNestedProperty(target, parentPropertyName);
                        if (parentPropertyValue instanceof HashMap) {
                            if (((HashMap) parentPropertyValue).keySet().contains(propertyName.substring(propertyName.lastIndexOf('.') + 1))) {
                                ((HashMap) parentPropertyValue).remove(propertyName.substring(propertyName.lastIndexOf('.') + 1));
                                PropertyUtils.setNestedProperty(target, parentPropertyName, parentPropertyValue);
                                return true;
                            } else {
                                return false;
                            }
                        }
                    } catch (NestedNullException ex) {
                        return false;
                    }

                }
                return false;
            }

            // Leave now, next strategies require a propertyValue, if no propertyValue, nothing to update.
            if (propertyValue == null) {
                return false;
            }

            // Resolve propertyName
            while (resolver.hasNested(propertyName)) {
                Object v = PropertyUtils.getProperty(target, resolver.next(propertyName));
                if (v == null) {
                    v = new LinkedHashMap<>();
                    PropertyUtils.setProperty(target, resolver.next(propertyName), v);
                }
                propertyName = resolver.remove(propertyName);
                target = v;
            }

            // Get previous value
            Object previousValue = PropertyUtils.getProperty(target, propertyName);

            // Handle strategies
            if (setPropertyStrategy == null ||
                    setPropertyStrategy.equals("alwaysSet") ||
                    (setPropertyStrategy.equals("setIfMissing") && previousValue == null)) {
                if (!compareValues(propertyValue, previousValue)) {
                    BeanUtils.setProperty(target, propertyName, propertyValue);
                    return true;
                }
            } else if (setPropertyStrategy.equals("addValue") || setPropertyStrategy.equals("addValues")) {
                List<Object> newValuesList = convertToList(propertyValue);
                List<Object> previousValueList = convertToList(previousValue);

                newValuesList.addAll(previousValueList);
                Set<Object> newValuesSet = new HashSet<>(newValuesList);
                if (newValuesSet.size() != previousValueList.size()) {
                    BeanUtils.setProperty(target, propertyName, Arrays.asList(newValuesSet.toArray()));
                    return true;
                }
            } else if (setPropertyStrategy.equals("removeValue") || setPropertyStrategy.equals("removeValues")) {
                List<Object> previousValueList = convertToList(previousValue);

                if (previousValueList.removeAll(convertToList(propertyValue))) {
                    BeanUtils.setProperty(target, propertyName, previousValueList);
                    return true;
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            LOGGER.error("Cannot set property", e);
        }
        return false;
    }

    public static List<Object> convertToList(Object value) {
        List<Object> convertedList = new ArrayList<>();
        if (value != null && value instanceof List) {
            convertedList.addAll((List) value);
        } else if (value != null) {
            convertedList.add(value);
        }
        return convertedList;
    }

    public static Integer getInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            // Not a number
        }
        return null;
    }

    public static Long getLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                // Not a number
            }
        }
        return null;
    }

    public static Double getDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                // Not a number
            }
        }
        return null;
    }

    public static Boolean getBooleanValue(Object setPropertyValueBoolean) {

        if (setPropertyValueBoolean instanceof Boolean) {
            return ((Boolean) setPropertyValueBoolean);
        } else if (setPropertyValueBoolean instanceof Number) {
            if (((Number) setPropertyValueBoolean).intValue() >= 1) {
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
        } else {
            if (((String) setPropertyValueBoolean).equalsIgnoreCase("true") || ((String) setPropertyValueBoolean).equalsIgnoreCase("on") ||
                    ((String) setPropertyValueBoolean).equalsIgnoreCase("yes") || ((String) setPropertyValueBoolean).equalsIgnoreCase("1")) {
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
        }

    }

    public static Object getValueByTypeId(Object propertyValue, String valueTypeId) {
        if (("boolean".equals(valueTypeId))) {
            return getBooleanValue(propertyValue);
        } else if ("integer".equals(valueTypeId)) {
            return getInteger(propertyValue);
        } else {
            return propertyValue;
        }
    }

    public static boolean compareValues(Object propertyValue, Object beanPropertyValue) {
        if (propertyValue == null) {
            return true;
        } else if (beanPropertyValue == null) {
            return false;
        }
        if (propertyValue instanceof Integer) {
            return propertyValue.equals(getInteger(beanPropertyValue));
        } if (propertyValue instanceof Long) {
            return propertyValue.equals(getLong(beanPropertyValue));
        } else if (propertyValue instanceof Boolean) {
            return propertyValue.equals(getBooleanValue(beanPropertyValue));
        } else {
            return propertyValue.equals(beanPropertyValue);
        }
    }

    public static Map<String, Object> flatten(Map<String, Object> in) {
        return in.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .flatMap(entry -> flatten(entry).entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
    }

    private static Map<String, Object> flatten(Map.Entry<String, Object> in) {
        // for other then Map objects return them
        if (!Map.class.isInstance(in.getValue())) {
            return Collections.singletonMap(in.getKey(), in.getValue());
        }
        // extract the key prefix for nested objects
        String prefix = in.getKey();
        Map<String, Object> values = (Map<String, Object>) in.getValue();
        // create a new Map, with prefix added to each key
        Map<String, Object> flattenMap = new HashMap<>();
        values.keySet().forEach(key -> {
            // use a dot as a joining char
            flattenMap.put(prefix + "." + key, values.get(key));
        });
        // use recursion to flatten the structure deeper
        return flatten(flattenMap);
    }

}
