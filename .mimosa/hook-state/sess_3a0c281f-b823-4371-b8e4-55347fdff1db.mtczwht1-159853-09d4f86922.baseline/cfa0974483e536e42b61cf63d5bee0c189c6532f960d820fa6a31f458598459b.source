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
 * Utility methods for reading and writing nested item properties.
 */
public class PropertyHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyHelper.class.getName());
    private static DefaultResolver resolver = new DefaultResolver();

    /**
     * Sets a nested property on the target using the given strategy.
     *
     * @param target the object to update
     * @param propertyName the property path (dot-separated for nesting)
     * @param propertyValue the value to set
     * @param setPropertyStrategy the merge strategy ({@code alwaysSet}, {@code setIfMissing}, etc.)
     * @return {@code true} if the target was modified
     */
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

    /**
     * Converts a value to a list (wraps non-list values).
     *
     * @param value the value to convert
     * @return a list containing the value(s)
     */
    public static List<Object> convertToList(Object value) {
        List<Object> convertedList = new ArrayList<>();
        if (value != null && value instanceof List) {
            convertedList.addAll((List) value);
        } else if (value != null) {
            convertedList.add(value);
        }
        return convertedList;
    }

    /**
     * Coerces a value to an {@link Integer}, or returns {@code null}.
     *
     * @param value the value to coerce
     * @return the integer value, or {@code null}
     */
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

    /**
     * Coerces a value to a {@link Long}, or returns {@code null}.
     *
     * @param value the value to coerce
     * @return the long value, or {@code null}
     */
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

    /**
     * Coerces a value to a {@link Double}, or returns {@code null}.
     *
     * @param value the value to coerce
     * @return the double value, or {@code null}
     */
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

    /**
     * Coerces a value to a {@link Boolean}.
     *
     * @param setPropertyValueBoolean the value to coerce
     * @return the boolean value
     */
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

    /**
     * Coerces a property value according to a value type id.
     *
     * @param propertyValue the raw value
     * @param valueTypeId the target type id ({@code boolean}, {@code integer}, etc.)
     * @return the coerced value
     */
    public static Object getValueByTypeId(Object propertyValue, String valueTypeId) {
        if (("boolean".equals(valueTypeId))) {
            return getBooleanValue(propertyValue);
        } else if ("integer".equals(valueTypeId)) {
            return getInteger(propertyValue);
        } else {
            return propertyValue;
        }
    }

    /**
     * Compares two property values with type-aware coercion.
     *
     * @param propertyValue the expected value
     * @param beanPropertyValue the actual value on the bean
     * @return {@code true} if the values are equal
     */
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

    /**
     * Flattens a nested map into dot-separated keys.
     *
     * @param in the map to flatten
     * @return the flattened map
     */
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
