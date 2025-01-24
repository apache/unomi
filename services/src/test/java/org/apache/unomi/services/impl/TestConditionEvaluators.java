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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluator;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcherImpl;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test condition evaluators for use in unit tests.
 */
public class TestConditionEvaluators {

    private static Map<String, ConditionType> conditionTypes = new ConcurrentHashMap<>();

    public static ConditionEvaluatorDispatcher createDispatcher() {
        ConditionEvaluatorDispatcherImpl dispatcher = new ConditionEvaluatorDispatcherImpl();
        dispatcher.addEvaluator("booleanConditionEvaluator", createBooleanConditionEvaluator());
        dispatcher.addEvaluator("propertyConditionEvaluator", createPropertyConditionEvaluator());
        dispatcher.addEvaluator("matchAllConditionEvaluator", createMatchAllConditionEvaluator());
        dispatcher.addEvaluator("eventTypeConditionEvaluator", createEventTypeConditionEvaluator());
        initializeConditionTypes();
        return dispatcher;
    }

    private static ConditionEvaluator createBooleanConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
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
        };
    }

    private static boolean compareValues(Object expectedValue, Object actualValue, String comparisonOperator) {
        if (comparisonOperator == null) {
            return false;
        }

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
                if (actualValue instanceof Collection) {
                    return ((Collection<?>) actualValue).contains(expectedValue);
                }
                return actualValue != null && actualValue.toString().contains(expectedValue.toString());
            case "startsWith":
                return actualValue != null && actualValue.toString().startsWith(expectedValue.toString());
            case "endsWith":
                return actualValue != null && actualValue.toString().endsWith(expectedValue.toString());
            default:
                return false;
        }
    }

    private static Object getPropertyValue(Item item, String propertyName) {
        if (item == null || propertyName == null) {
            return null;
        }

        try {
            String[] path = propertyName.split("\\.");
            Object current = item;

            for (String field : path) {
                if (current == null) {
                    return null;
                }

                // Handle Map-based access
                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(field);
                    continue;
                }

                // Handle special cases for known types
                if (current instanceof Event && "profile".equals(field)) {
                    current = ((Event) current).getProfile();
                    continue;
                } else if (current instanceof Event && "session".equals(field)) {
                    current = ((Event) current).getSession();
                    continue;
                }

                // Try getter method
                try {
                    Method getter = current.getClass().getMethod("get" + field.substring(0, 1).toUpperCase() + field.substring(1));
                    current = getter.invoke(current);
                } catch (Exception e) {
                    // If getter fails, try direct field access
                    try {
                        current = current.getClass().getField(field).get(current);
                    } catch (Exception ex) {
                        return null;
                    }
                }
            }
            return current;
        } catch (Exception e) {
            return null;
        }
    }

    private static ConditionEvaluator createPropertyConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
            String propertyName = (String) condition.getParameter("propertyName");
            String comparisonOperator = (String) condition.getParameter("comparisonOperator");
            Object expectedValue = condition.getParameter("propertyValue");

            if (propertyName == null || comparisonOperator == null) {
                return false;
            }

            Object actualValue = getPropertyValue(item, propertyName);
            return compareValues(expectedValue, actualValue, comparisonOperator);
        };
    }

    private static ConditionEvaluator createMatchAllConditionEvaluator() {
        return (condition, item, context, dispatcher) -> true;
    }

    private static ConditionEvaluator createEventTypeConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
            if (!(item instanceof Event)) {
                return false;
            }
            Event event = (Event) item;
            String expectedEventType = (String) condition.getParameter("eventTypeId");
            return expectedEventType != null && expectedEventType.equals(event.getEventType());
        };
    }

    private static void initializeConditionTypes() {

        ConditionType propertyConditionType = createConditionType("propertyCondition", "propertyConditionEvaluator", "propertyConditionESQueryBuilder", null);
        conditionTypes.put("propertyCondition", propertyConditionType);

        // Create boolean condition type
        ConditionType booleanConditionType = createConditionType("booleanCondition",
                "booleanConditionEvaluator",
                "booleanConditionESQueryBuilder", Set.of("profileTags",
                        "logical",
                        "condition",
                        "profileCondition",
                        "eventCondition",
                        "sessionCondition",
                        "sourceEventCondition"));
        conditionTypes.put("booleanCondition", booleanConditionType);

        // Create matchAll condition type
        ConditionType matchAllConditionType = createConditionType("matchAllCondition", "matchAllConditionEvaluator",
                "matchAllConditionESQueryBuilder", Set.of("profileTags", "logical", "condition", "profileCondition",
                        "eventCondition", "sessionCondition", "sourceEventCondition"));
        conditionTypes.put("matchAllCondition", matchAllConditionType);

        // Create eventType condition type
        ConditionType eventTypeConditionType = createConditionType("eventTypeCondition", "eventTypeConditionEvaluator",
                "eventTypeConditionESQueryBuilder", Set.of("profileTags", "event", "condition", "eventCondition"));
        conditionTypes.put("eventTypeCondition", eventTypeConditionType);

        // Create eventProperty condition type
        ConditionType eventPropertyConditionType = createConditionType("eventPropertyCondition", "propertyConditionEvaluator",
                "propertyConditionESQueryBuilder", Set.of("profileTags", "demographic", "condition", "eventCondition"));
        conditionTypes.put("eventPropertyCondition", eventPropertyConditionType);

        // Create sessionProperty condition type
        ConditionType sessionPropertyConditionType = createConditionType("sessionPropertyCondition", "propertyConditionEvaluator",
                "propertyConditionESQueryBuilder", Set.of("availableToEndUser", "sessionBased", "profileTags", "event", "condition", "sessionCondition"));
        conditionTypes.put("sessionPropertyCondition", sessionPropertyConditionType);

        // Create profileProperty condition type
        ConditionType profilePropertyConditionType = createConditionType("profilePropertyCondition", "propertyConditionEvaluator",
                "propertyConditionESQueryBuilder", Set.of("availableToEndUser", "profileTags", "demographic", "condition", "profileCondition"));
        conditionTypes.put("profilePropertyCondition", profilePropertyConditionType);
    }

    private static ConditionType createConditionType(String typeId, String conditionEvaluatorId, String queryBuilderId, Set<String> systemTags) {
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId(typeId);

        Metadata metadata = new Metadata();
        metadata.setId(typeId);
        metadata.setEnabled(true);
        if (systemTags != null) {
            metadata.setSystemTags(new HashSet<>(systemTags));
        }

        conditionType.setMetadata(metadata);
        conditionType.setConditionEvaluator(conditionEvaluatorId);
        conditionType.setQueryBuilder(queryBuilderId);

        return conditionType;
    }

    public static Map<String, ConditionType> getConditionTypes() {
        return conditionTypes;
    }

    public static ConditionType getConditionType(String conditionTypeId) {
        return conditionTypes.get(conditionTypeId);
    }
}
