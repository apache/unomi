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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluator;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcherImpl;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test condition evaluators for use in unit tests.
 */
public class TestConditionEvaluators {

    private static Map<String, ConditionType> conditionTypes = new ConcurrentHashMap<>();

    public static ConditionEvaluatorDispatcher createDispatcher() {
        ConditionEvaluatorDispatcherImpl dispatcher = new ConditionEvaluatorDispatcherImpl();
        dispatcher.addEvaluator("booleanCondition", createBooleanConditionEvaluator());
        dispatcher.addEvaluator("propertyCondition", createPropertyConditionEvaluator());
        initializeConditionTypes();
        return dispatcher;
    }

    public static ConditionEvaluator createBooleanConditionEvaluator() {
        return new ConditionEvaluator() {
            @Override
            public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
                String operator = (String) condition.getParameter("operator");
                List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");

                if (subConditions == null || subConditions.isEmpty()) {
                    return true;
                }

                boolean isAnd = "and".equalsIgnoreCase(operator);

                for (Condition subCondition : subConditions) {
                    boolean result = dispatcher.eval(subCondition, item, context);
                    if (isAnd && !result) {
                        return false;
                    } else if (!isAnd && result) {
                        return true;
                    }
                }

                return isAnd;
            }
        };
    }

    public static ConditionEvaluator createPropertyConditionEvaluator() {
        return new ConditionEvaluator() {
            @Override
            public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
                String propertyName = (String) condition.getParameter("propertyName");
                String comparisonOperator = (String) condition.getParameter("comparisonOperator");
                Object expectedValue = condition.getParameter("propertyValue");

                if (propertyName == null || comparisonOperator == null) {
                    return false;
                }

                Object actualValue = getPropertyValue(item, propertyName);

                switch (comparisonOperator) {
                    case "equals":
                        return Objects.equals(expectedValue, actualValue);
                    case "notEquals":
                        return !Objects.equals(expectedValue, actualValue);
                    case "exists":
                        return actualValue != null;
                    case "missing":
                        return actualValue == null;
                    case "contains":
                        return actualValue != null && actualValue.toString().contains(expectedValue.toString());
                    case "startsWith":
                        return actualValue != null && actualValue.toString().startsWith(expectedValue.toString());
                    case "endsWith":
                        return actualValue != null && actualValue.toString().endsWith(expectedValue.toString());
                    default:
                        return false;
                }
            }
        };
    }

    private static Object getPropertyValue(Item item, String propertyName) {
        if (item == null || propertyName == null) {
            return null;
        }

        // Handle metadata properties first
        if (propertyName.startsWith("metadata.")) {
            String metadataField = propertyName.substring("metadata.".length());
            if (item instanceof MetadataItem) {
                Metadata metadata = ((MetadataItem) item).getMetadata();
                if (metadata != null) {
                    if ("tags".equals(metadataField)) {
                        return metadata.getTags();
                    } else if ("systemTags".equals(metadataField)) {
                        return metadata.getSystemTags();
                    }
                    // Try to get other metadata fields using reflection
                    return getFieldValueByReflection(metadata, metadataField);
                }
            }
            return null;
        }

        // Handle nested properties
        String[] propertyPath = propertyName.split("\\.");
        Object currentObject = item;

        for (String property : propertyPath) {
            if (currentObject == null) {
                return null;
            }

            // Try to get value using reflection
            currentObject = getFieldValueByReflection(currentObject, property);
        }

        return currentObject;
    }

    private static Object getFieldValueByReflection(Object object, String fieldName) {
        if (object == null || fieldName == null) {
            return null;
        }

        // Handle Map lookup first
        if (object instanceof Map) {
            return ((Map<?, ?>) object).get(fieldName);
        }

        Class<?> clazz = object.getClass();
        while (clazz != null) {
            try {
                // First try to find a getter method
                String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                try {
                    Method getter = clazz.getDeclaredMethod(getterName);
                    getter.setAccessible(true);
                    return getter.invoke(object);
                } catch (NoSuchMethodException e) {
                    // No getter found, try direct field access
                    try {
                        Field field = clazz.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        return field.get(object);
                    } catch (NoSuchFieldException nsfe) {
                        // Try superclass
                        clazz = clazz.getSuperclass();
                        continue;
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                // If we can't access the field/method, return null
                return null;
            }
        }
        return null;
    }

    private static void initializeConditionTypes() {
        // Create property condition type
        ConditionType propertyConditionType = new ConditionType();
        propertyConditionType.setItemId("propertyCondition");
        Metadata propertyMetadata = new Metadata();
        propertyMetadata.setId("propertyCondition");
        propertyConditionType.setMetadata(propertyMetadata);
        propertyConditionType.setConditionEvaluator("propertyCondition");
        conditionTypes.put("propertyCondition", propertyConditionType);

        // Create boolean condition type
        ConditionType booleanConditionType = new ConditionType();
        booleanConditionType.setItemId("booleanCondition");
        Metadata booleanMetadata = new Metadata();
        booleanMetadata.setId("booleanCondition");
        booleanConditionType.setMetadata(booleanMetadata);
        booleanConditionType.setConditionEvaluator("booleanCondition");
        conditionTypes.put("booleanCondition", booleanConditionType);
    }

    public static ConditionType getConditionType(String conditionTypeId) {
        return conditionTypes.get(conditionTypeId);
    }
}
