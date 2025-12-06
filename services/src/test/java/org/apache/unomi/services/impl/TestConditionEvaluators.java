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

import org.apache.commons.lang3.ObjectUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.conditions.ConditionValidation;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.apache.unomi.persistence.spi.conditions.*;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluator;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.persistence.spi.conditions.evaluator.impl.ConditionEvaluatorDispatcherImpl;
import org.apache.unomi.persistence.spi.conditions.geo.DistanceUnit;
import org.apache.unomi.tracing.api.RequestTracer;
import org.osgi.framework.BundleContext;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Test condition evaluators for use in unit tests.
 */
public class TestConditionEvaluators {

    private static Map<String, ConditionType> conditionTypes = new ConcurrentHashMap<>();
    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final SimpleDateFormat yearMonthDayDateFormat = new SimpleDateFormat("yyyyMMdd");
    private static EventService eventService;
    private static BundleContext bundleContext;
    private static TestRequestTracer tracer = new TestRequestTracer(false);
    private static Map<String, ConditionEvaluator> evaluators = new HashMap<>();

    static {
        ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        yearMonthDayDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static void setEventService(EventService service) {
        eventService = service;
    }

    public static void setBundleContext(BundleContext bundleContext) {
        TestConditionEvaluators.bundleContext = bundleContext;
    }

    public static RequestTracer getTracer() {
        return tracer;
    }

    public static ConditionEvaluatorDispatcher createDispatcher() {
        ConditionEvaluatorDispatcherImpl dispatcher = new ConditionEvaluatorDispatcherImpl();
        dispatcher.addEvaluator("booleanConditionEvaluator", createBooleanConditionEvaluator());
        dispatcher.addEvaluator("propertyConditionEvaluator", createPropertyConditionEvaluator());
        dispatcher.addEvaluator("matchAllConditionEvaluator", createMatchAllConditionEvaluator());
        dispatcher.addEvaluator("eventTypeConditionEvaluator", createEventTypeConditionEvaluator());
        dispatcher.addEvaluator("pastEventConditionEvaluator", createPastEventConditionEvaluator());
        dispatcher.addEvaluator("notConditionEvaluator", createNotConditionEvaluator());
        dispatcher.addEvaluator("nestedConditionEvaluator", createNestedConditionEvaluator());
        dispatcher.addEvaluator("profileUpdatedEventConditionEvaluator", createProfileUpdatedEventConditionEvaluator());
        dispatcher.addEvaluator("idsConditionEvaluator", createIdsConditionEvaluator());
        initializeConditionTypes();
        return dispatcher;
    }

    private static ConditionEvaluator createBooleanConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
            tracer.startOperation("boolean", "Evaluating boolean condition with operator: " + condition.getParameter("operator"), condition);
            String operator = (String) condition.getParameter("operator");
            List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");

            if (subConditions == null || subConditions.isEmpty()) {
                tracer.endOperation(true, "No subconditions found, returning true");
                return true;
            }

            boolean isAnd = "and".equalsIgnoreCase(operator);
            tracer.trace("Using " + (isAnd ? "AND" : "OR") + " operator for " + subConditions.size() + " subconditions", condition);

            for (Condition subCondition : subConditions) {
                boolean result = dispatcher.eval(subCondition, item, context);
                if (isAnd && !result) {
                    tracer.endOperation(false, "AND condition failed on subcondition");
                    return false;
                } else if (!isAnd && result) {
                    tracer.endOperation(true, "OR condition succeeded on subcondition");
                    return true;
                }
            }

            boolean finalResult = isAnd;
            tracer.endOperation(finalResult, "All subconditions processed, returning " + finalResult);
            return finalResult;
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
                    try {
                        Method getter = current.getClass().getMethod("is" + field.substring(0, 1).toUpperCase() + field.substring(1));
                        current = getter.invoke(current);
                    } catch (Exception e2) {
                        // If getter fails, try direct field access
                        try {
                            current = current.getClass().getField(field).get(current);
                        } catch (Exception ex) {
                            return null;
                        }
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
            tracer.startOperation("property", "Evaluating property condition", condition);
            String propertyName = (String) condition.getParameter("propertyName");
            String comparisonOperator = (String) condition.getParameter("comparisonOperator");

            Object actualValue = getPropertyValue(item, propertyName);
            boolean result = evaluateCondition(actualValue, comparisonOperator, condition);

            tracer.endOperation(result, "Property condition evaluation completed");
            return result;
        };
    }

    private static boolean evaluateCondition(Object actualValue, String operator, Condition condition) {
        // Handle null cases first
        if (operator == null) {
            return false;
        }
        if (actualValue == null) {
            return operator.equals("missing") || operator.equals("notIn") ||
                   operator.equals("notEquals") || operator.equals("hasNoneOf");
        }
        if (operator.equals("exists")) {
            return !(actualValue instanceof List) || !((List<?>) actualValue).isEmpty();
        }

        // Get all possible expected values
        String expectedValue = condition.getParameter("propertyValue") == null ? null :
            ConditionContextHelper.foldToASCII(condition.getParameter("propertyValue").toString());
        Object expectedValueInteger = condition.getParameter("propertyValueInteger");
        Object expectedValueDouble = condition.getParameter("propertyValueDouble");
        Object expectedValueDate = condition.getParameter("propertyValueDate");
        Object expectedValueDateExpr = condition.getParameter("propertyValueDateExpr");

        // Convert string values to ASCII folded form
        if (actualValue instanceof String || expectedValue != null) {
            actualValue = ConditionContextHelper.foldToASCII(actualValue.toString());
        }

        // Handle comparison operators
        switch (operator) {
            case "equals":
            case "notEquals":
            case "greaterThan":
            case "greaterThanOrEqualTo":
            case "lessThan":
            case "lessThanOrEqualTo":
                int comparisonResult = compareValues(actualValue, expectedValue, expectedValueDate,
                    expectedValueInteger, expectedValueDateExpr, expectedValueDouble);
                return evaluateComparison(operator, comparisonResult);

            case "between":
                return evaluateBetweenCondition(actualValue, condition);

            case "contains":
            case "notContains":
            case "startsWith":
            case "endsWith":
            case "matchesRegex":
                return evaluateStringCondition(actualValue.toString(), expectedValue, operator);

            case "in":
            case "inContains":
            case "notIn":
            case "hasSomeOf":
            case "hasNoneOf":
            case "all":
                return evaluateCollectionCondition(actualValue, condition, operator);

            case "isDay":
            case "isNotDay":
                return evaluateDateCondition(actualValue, expectedValueDate, expectedValueDateExpr, operator);

            case "distance":
                return evaluateDistanceCondition(actualValue, condition);

            default:
                return false;
        }
    }

    private static boolean evaluateComparison(String operator, int comparisonResult) {
        switch (operator) {
            case "equals": return comparisonResult == 0;
            case "notEquals": return comparisonResult != 0;
            case "greaterThan": return comparisonResult > 0;
            case "greaterThanOrEqualTo": return comparisonResult >= 0;
            case "lessThan": return comparisonResult < 0;
            case "lessThanOrEqualTo": return comparisonResult <= 0;
            default: return false;
        }
    }

    private static boolean evaluateStringCondition(String actualValue, String expectedValue, String operator) {
        if (expectedValue == null) {
            return false;
        }
        switch (operator) {
            case "contains": return actualValue.contains(expectedValue);
            case "notContains": return !actualValue.contains(expectedValue);
            case "startsWith": return actualValue.startsWith(expectedValue);
            case "endsWith": return actualValue.endsWith(expectedValue);
            case "matchesRegex": return Pattern.compile(expectedValue).matcher(actualValue).matches();
            default: return false;
        }
    }

    private static boolean evaluateBetweenCondition(Object actualValue, Condition condition) {
        Collection<?> expectedValuesInteger = (Collection<?>) condition.getParameter("propertyValuesInteger");
        Collection<?> expectedValuesDouble = (Collection<?>) condition.getParameter("propertyValuesDouble");
        Collection<?> expectedValuesDate = (Collection<?>) condition.getParameter("propertyValuesDate");
        Collection<?> expectedValuesDateExpr = (Collection<?>) condition.getParameter("propertyValuesDateExpr");

        int lowerBoundComparison = compareValues(actualValue, null,
                getDate(getFirst(expectedValuesDate)),
                getFirst(expectedValuesInteger),
                getFirst(expectedValuesDateExpr),
                getFirst(expectedValuesDouble));

        int upperBoundComparison = compareValues(actualValue, null,
                getDate(getSecond(expectedValuesDate)),
                getSecond(expectedValuesInteger),
                getSecond(expectedValuesDateExpr),
                getSecond(expectedValuesDouble));

        return lowerBoundComparison >= 0 && upperBoundComparison <= 0;
    }

    private static boolean evaluateDateCondition(Object actualValue, Object expectedValueDate,
                                               Object expectedValueDateExpr, String operator) {
        Object expectedDate = expectedValueDate == null ? expectedValueDateExpr : expectedValueDate;
        boolean isSameDay = yearMonthDayDateFormat.format(getDate(actualValue))
                .equals(yearMonthDayDateFormat.format(getDate(expectedDate)));
        return operator.equals("isDay") ? isSameDay : !isSameDay;
    }

    private static boolean evaluateDistanceCondition(Object actualValue, Condition condition) {
        GeoPoint actualCenter = null;
        if (actualValue instanceof GeoPoint) {
            actualCenter = (GeoPoint) actualValue;
        } else if (actualValue instanceof Map) {
            actualCenter = GeoPoint.fromMap((Map<String, Double>) actualValue);
        } else if (actualValue instanceof String) {
            actualCenter = GeoPoint.fromString((String) actualValue);
        }
        if (actualCenter == null) {
            return false;
        }

        String unitString = (String) condition.getParameter("unit");
        String centerString = (String) condition.getParameter("center");
        Double distance = (Double) condition.getParameter("distance");
        if (centerString == null || distance == null) {
            return false;
        }

        GeoPoint expectedCenter = GeoPoint.fromString(centerString);
        DistanceUnit expectedUnit = unitString != null ? DistanceUnit.fromString(unitString) : DistanceUnit.METERS;
        return expectedCenter.distanceTo(actualCenter) <= expectedUnit.toMeters(distance);
    }

    private static boolean evaluateCollectionCondition(Object actualValue, Condition condition, String operator) {
        Collection<?> expectedValues = ConditionContextHelper.foldToASCII((Collection<?>) condition.getParameter("propertyValues"));
        Collection<?> expectedValuesInteger = (Collection<?>) condition.getParameter("propertyValuesInteger");
        Collection<?> expectedValuesDate = (Collection<?>) condition.getParameter("propertyValuesDate");
        Collection<?> expectedValuesDateExpr = (Collection<?>) condition.getParameter("propertyValuesDateExpr");
        Collection<?> expectedValuesDouble = (Collection<?>) condition.getParameter("propertyValuesDouble");

        Collection<Object> expectedDateExpr = expectedValuesDateExpr != null ?
            expectedValuesDateExpr.stream().map(DateUtils::getDate).collect(Collectors.toList()) : null;

        @SuppressWarnings("unchecked")
        Collection<?> expected = ObjectUtils.firstNonNull(expectedValues, expectedValuesDate,
            expectedValuesInteger, expectedValuesDouble, expectedDateExpr);

        if (expected == null) {
            return actualValue == null;
        }

        Collection<Object> actual = ConditionContextHelper.foldToASCII(getValueSet(actualValue));

        switch (operator) {
            case "in": return actual.stream().anyMatch(expected::contains);
            case "inContains": return actual.stream().anyMatch(a ->
                expected.stream().anyMatch(b -> ((String) a).contains((String) b)));
            case "notIn": return actual.stream().noneMatch(expected::contains);
            case "all": return expected.stream().allMatch(actual::contains);
            case "hasNoneOf": return Collections.disjoint(actual, expected);
            case "hasSomeOf": return !Collections.disjoint(actual, expected);
            default: return false;
        }
    }

    private static int compareValues(Object actualValue, String expectedValue, Object expectedValueDate,
                                   Object expectedValueInteger, Object expectedValueDateExpr, Object expectedValueDouble) {
        if (expectedValue == null && expectedValueDate == null && expectedValueInteger == null &&
            getDate(expectedValueDateExpr) == null && expectedValueDouble == null) {
            return actualValue == null ? 0 : 1;
        } else if (actualValue == null) {
            return -1;
        }

        if (expectedValueInteger != null) {
            return PropertyHelper.getInteger(actualValue).compareTo(PropertyHelper.getInteger(expectedValueInteger));
        } else if (expectedValueDouble != null) {
            return PropertyHelper.getDouble(actualValue).compareTo(PropertyHelper.getDouble(expectedValueDouble));
        } else if (expectedValueDate != null) {
            return getDate(actualValue).compareTo(getDate(expectedValueDate));
        } else if (expectedValueDateExpr != null) {
            return getDate(actualValue).compareTo(getDate(expectedValueDateExpr));
        } else {
            return actualValue.toString().toLowerCase().compareTo(expectedValue);
        }
    }

    private static List<Object> getValueSet(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        } else if (value instanceof Collection) {
            return new ArrayList<>((Collection<?>) value);
        } else {
            return Collections.singletonList(value);
        }
    }

    private static Object getFirst(Collection<?> collection) {
        if (collection == null || collection.isEmpty()) {
            return null;
        }
        return collection.iterator().next();
    }

    private static Object getSecond(Collection<?> collection) {
        if (collection == null || collection.size() < 2) {
            return null;
        }
        Iterator<?> iterator = collection.iterator();
        iterator.next();
        return iterator.next();
    }

    private static ConditionEvaluator createMatchAllConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
            tracer.startEvaluation(condition, "Evaluating matchAll condition");
            tracer.endEvaluation(condition, true, "MatchAll condition always returns true");
            return true;
        };
    }

    private static ConditionEvaluator createEventTypeConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
            tracer.startOperation("eventType", "Evaluating event type condition", condition);
            String eventType = (String) condition.getParameter("eventTypeId");
            boolean result = item instanceof Event && eventType.equals(((Event) item).getEventType());
            tracer.endOperation(result, "Event type condition evaluation completed");
            return result;
        };
    }

    private static ConditionEvaluator createPastEventConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
            tracer.startOperation("pastEvent", "Evaluating past event condition", condition);
            if (!(item instanceof Profile)) {
                tracer.endOperation(false, "Item is not a profile");
                return false;
            }

            Profile profile = (Profile) item;
            Map<String, Object> parameters = condition.getParameterValues();
            long count;

            if (parameters.containsKey("generatedPropertyKey")) {
                String key = (String) parameters.get("generatedPropertyKey");
                tracer.trace(condition, "Using generated property key: " + key);

                List<Map<String, Object>> pastEvents = (ArrayList<Map<String, Object>>) profile.getSystemProperties().get("pastEvents");
                if (pastEvents != null) {
                    tracer.trace(condition, "Found pastEvents in profile system properties");
                    Number l = (Number) pastEvents
                            .stream()
                            .filter(pastEvent -> pastEvent.get("key").equals(key))
                            .findFirst()
                            .map(pastEvent -> pastEvent.get("count")).orElse(0L);
                    count = l.longValue();
                    tracer.trace(condition, "Found count=" + count + " for key=" + key);
                } else {
                    tracer.trace(condition, "No pastEvents found in profile system properties");
                    count = 0;
                }
            } else {
                tracer.trace(condition, "No generatedPropertyKey found, querying events directly");
                Condition eventCondition = (Condition) condition.getParameter("eventCondition");
                count = eventService.searchEvents(eventCondition, 0, 1).size();
                tracer.trace(condition, "Direct event query returned count=" + count);
            }

            boolean eventsOccurred = "true".equals(condition.getParameter("operator"));
            if (eventsOccurred) {
                int minimumEventCount = parameters.get("minimumEventCount") == null ? 0 : (Integer) parameters.get("minimumEventCount");
                int maximumEventCount = parameters.get("maximumEventCount") == null ? Integer.MAX_VALUE : (Integer) parameters.get("maximumEventCount");
                boolean result = count > 0 && (count >= minimumEventCount && count <= maximumEventCount);
                tracer.endEvaluation(condition, result,
                    String.format("Events occurred check: count=%d, min=%d, max=%d", count, minimumEventCount, maximumEventCount));
                return result;
            } else {
                boolean result = count == 0;
                tracer.endEvaluation(condition, result, "Events not occurred check: count=" + count);
                return result;
            }
        };
    }

    private static ConditionEvaluator createNotConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
            Condition subCondition = (Condition) condition.getParameter("subCondition");
            if (subCondition == null) {
                return false;
            }
            return !dispatcher.eval(subCondition, item, context);
        };
    }

    private static ConditionEvaluator createProfileUpdatedEventConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
            if (!(item instanceof Event)) {
                return false;
            }
            Event event = (Event) item;
            return "profileUpdated".equals(event.getEventType());
        };
    }

    private static ConditionEvaluator createNestedConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
            tracer.startEvaluation(condition, "Evaluating nested condition");

            String path = (String) condition.getParameter("path");
            Condition subCondition = (Condition) condition.getParameter("subCondition");

            if (subCondition == null || path == null) {
                tracer.endEvaluation(condition, false, "Missing required parameters: subCondition or path is null");
                return false;
            }

            tracer.trace(condition, "Evaluating nested condition with path: " + path);

            try {
                // Get list of nested items to be evaluated
                Object nestedItems = getPropertyValue(item, path);
                if (nestedItems instanceof List) {
                    tracer.trace(condition, "Found list of nested items at path: " + path + ", size: " + ((List<?>) nestedItems).size());

                    // Evaluate each nested item until one matches the nested condition
                    for (Object nestedItem : (List<?>) nestedItems) {
                        if (nestedItem instanceof Map) {
                            Map<String, Object> flattenedNestedItem = flattenNestedItem(path, (Map<String, Object>) nestedItem);
                            Item finalNestedItem = createFinalNestedItemForEvaluation(item, path, flattenedNestedItem);

                            if (finalNestedItem != null) {
                                tracer.trace(condition, "Evaluating subcondition on nested item");
                                boolean result = dispatcher.eval(subCondition, finalNestedItem, context);
                                if (result) {
                                    tracer.endEvaluation(condition, true, "Found matching nested item");
                                    return true;
                                }
                            }
                        }
                    }
                    tracer.endEvaluation(condition, false, "No matching nested items found");
                } else {
                    tracer.endEvaluation(condition, false, "Property at path is not a list: " + path);
                }
            } catch (Exception e) {
                tracer.trace(condition, "Error evaluating nested condition: " + e.getMessage());
                tracer.endEvaluation(condition, false, "Failed to evaluate nested condition: " + e.getMessage());
                return false;
            }
            return false;
        };
    }

    private static Map<String, Object> flattenNestedItem(String path, Map<String, Object> nestedItem) {
        Map<String, Object> flattenedNestedItem = new HashMap<>();

        if (path != null && !path.isEmpty()) {
            String propertyPath = path.contains(".") ? path.substring(path.indexOf(".") + 1) : path;
            if (!propertyPath.isEmpty()) {
                String[] propertyKeys = propertyPath.split("\\.");
                Map<String, Object> currentLevel = flattenedNestedItem;

                for (int i = 0; i < propertyKeys.length; i++) {
                    String key = propertyKeys[i];
                    if (i == propertyKeys.length - 1) {
                        currentLevel.put(key, nestedItem);
                    } else {
                        Map<String, Object> nextLevel = new HashMap<>();
                        currentLevel.put(key, nextLevel);
                        currentLevel = nextLevel;
                    }
                }
            }
        }

        return flattenedNestedItem;
    }

    private static Item createFinalNestedItemForEvaluation(Item parentItem, String path, Map<String, Object> flattenedNestedItem) {
        if (parentItem instanceof Profile) {
            Profile profile = new Profile(parentItem.getItemId());
            if (path.startsWith("properties.")) {
                profile.setProperties(flattenedNestedItem);
            } else if (path.startsWith("systemProperties.")) {
                profile.setSystemProperties(flattenedNestedItem);
            }
            return profile;
        } else if (parentItem instanceof Session) {
            Session session = new Session();
            if (path.startsWith("properties.")) {
                session.setProperties(flattenedNestedItem);
            } else if (path.startsWith("systemProperties.")) {
                session.setSystemProperties(flattenedNestedItem);
            }
            return session;
        }
        return null;
    }

    private static void initializeConditionTypes() {

        ConditionType propertyConditionType = createConditionType("propertyCondition", "propertyConditionEvaluator", "propertyConditionQueryBuilder", null);
        conditionTypes.put("propertyCondition", propertyConditionType);

        // Create boolean condition type
        ConditionType booleanConditionType = createConditionType("booleanCondition",
                "booleanConditionEvaluator",
                "booleanConditionQueryBuilder", Set.of("profileTags",
                        "logical",
                        "condition",
                        "profileCondition",
                        "eventCondition",
                        "sessionCondition",
                        "sourceEventCondition"));
        conditionTypes.put("booleanCondition", booleanConditionType);

        // Create matchAll condition type
        ConditionType matchAllConditionType = createConditionType("matchAllCondition", "matchAllConditionEvaluator",
                "matchAllConditionQueryBuilder", Set.of("profileTags", "logical", "condition", "profileCondition",
                        "eventCondition", "sessionCondition", "sourceEventCondition"));
        conditionTypes.put("matchAllCondition", matchAllConditionType);

        // Create eventType condition type
        ConditionType eventTypeConditionType = createConditionType("eventTypeCondition", "eventTypeConditionEvaluator",
                "eventTypeConditionQueryBuilder", Set.of("profileTags", "event", "condition", "eventCondition"));
        conditionTypes.put("eventTypeCondition", eventTypeConditionType);

        // Create eventProperty condition type
        ConditionType eventPropertyConditionType = createConditionType("eventPropertyCondition", "propertyConditionEvaluator",
                "propertyConditionQueryBuilder", Set.of("profileTags", "demographic", "condition", "eventCondition"));
        conditionTypes.put("eventPropertyCondition", eventPropertyConditionType);

        // Create sessionProperty condition type
        ConditionType sessionPropertyConditionType = createConditionType("sessionPropertyCondition", "propertyConditionEvaluator",
                "propertyConditionQueryBuilder", Set.of("availableToEndUser", "sessionBased", "profileTags", "event", "condition", "sessionCondition"));
        conditionTypes.put("sessionPropertyCondition", sessionPropertyConditionType);

        // Create profileProperty condition type
        ConditionType profilePropertyConditionType = createConditionType("profilePropertyCondition", "propertyConditionEvaluator",
                "propertyConditionQueryBuilder", Set.of("availableToEndUser", "profileTags", "demographic", "condition", "profileCondition"));
        conditionTypes.put("profilePropertyCondition", profilePropertyConditionType);

        // Create pastEvent condition type
        ConditionType pastEventConditionType = createConditionType("pastEventCondition", "pastEventConditionEvaluator",
                "pastEventConditionQueryBuilder", Set.of("profileTags", "event", "condition", "pastEventCondition"));
        conditionTypes.put("pastEventCondition", pastEventConditionType);

        // Create not condition type
        ConditionType notConditionType = createConditionType("notCondition", "notConditionEvaluator",
                "notConditionQueryBuilder", Set.of("profileTags", "logical", "condition", "profileCondition",
                        "eventCondition", "sessionCondition", "sourceEventCondition"));
        conditionTypes.put("notCondition", notConditionType);

        // Create profileUpdatedEvent condition type
        ConditionType profileUpdatedEventConditionType = createConditionType("profileUpdatedEventCondition",
                "profileUpdatedEventConditionEvaluator",
                "eventTypeConditionQueryBuilder",
                Set.of("profileTags", "event", "condition", "eventCondition"));
        conditionTypes.put("profileUpdatedEventCondition", profileUpdatedEventConditionType);

        // Create nested condition type
        ConditionType nestedConditionType = createConditionType("nestedCondition",
                "nestedConditionEvaluator",
                "nestedConditionQueryBuilder",
                Set.of("profileTags", "logical", "condition", "profileCondition", "sessionCondition"));
        conditionTypes.put("nestedCondition", nestedConditionType);

        // Create ids condition type
        ConditionType idsConditionType = createConditionType("idsCondition", "idsConditionEvaluator",
                "idsConditionQueryBuilder", Set.of("profileTags", "logical", "condition", "profileCondition",
                        "eventCondition", "sessionCondition", "sourceEventCondition"));
        conditionTypes.put("idsCondition", idsConditionType);
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

        // Add parameter validation requirements based on condition type
        switch (typeId) {
            case "profilePropertyCondition":
            case "sessionPropertyCondition":
            case "eventPropertyCondition":
                // Property conditions require propertyName, comparisonOperator, and one of the propertyValue* parameters
                conditionType.setParameters(Arrays.asList(
                    createParameter("propertyName", "string", true, null, false),
                    createParameter("comparisonOperator", "string", true, null, false),
                    createParameter("propertyValue", "string", false, "propertyValue", false),
                    createParameter("propertyValueInteger", "integer", false, "propertyValue", false),
                    createParameter("propertyValueDouble", "double", false, "propertyValue", false),
                    createParameter("propertyValueDate", "date", false, "propertyValue", false)
                ));
                break;
            case "booleanCondition":
                // Boolean conditions require operator and subConditions (which is multivalued)
                conditionType.setParameters(Arrays.asList(
                    createParameter("operator", "string", true, null, false),
                    createParameter("subConditions", "Condition", true, null, true)
                ));
                break;
            case "pastEventCondition":
                // Past event conditions require eventCondition, operator is recommended
                conditionType.setParameters(Arrays.asList(
                    createParameter("eventCondition", "Condition", true, null, false),
                    createParameterRecommended("operator", "string", null, false),
                    createParameter("numberOfDays", "integer", false, null, false),
                    createParameter("minimumEventCount", "integer", false, null, false),
                    createParameter("maximumEventCount", "integer", false, null, false)
                ));
                break;
            case "eventTypeCondition":
                // Event type conditions require eventTypeId
                conditionType.setParameters(Arrays.asList(
                    createParameter("eventTypeId", "string", true, null, false)
                ));
                break;
            case "notCondition":
                // Not conditions require subCondition (single condition, not multivalued)
                conditionType.setParameters(Arrays.asList(
                    createParameter("subCondition", "Condition", true, null, false)
                ));
                break;
            case "nestedCondition":
                // Nested conditions require path and subCondition (single condition, not multivalued)
                conditionType.setParameters(Arrays.asList(
                    createParameter("path", "string", true, null, false),
                    createParameter("subCondition", "Condition", true, null, false)
                ));
                break;
            case "idsCondition":
                // Ids conditions require ids collection (which is multivalued)
                conditionType.setParameters(Arrays.asList(
                    createParameter("ids", "string", true, null, true)
                ));
                break;
            case "matchAllCondition":
                // Match all doesn't require any parameters
                break;
            case "profileUpdatedEventCondition":
                // Profile updated event doesn't require any parameters
                break;
        }

        return conditionType;
    }

    private static Parameter createParameter(String name, String type, boolean required, String exclusiveGroup, boolean multivalued) {
        Parameter parameter = new Parameter();
        parameter.setId(name);
        parameter.setType(type);
        parameter.setMultivalued(multivalued);

        // Create validation settings
        ConditionValidation validation = new ConditionValidation();

        // Set required flag
        validation.setRequired(required);

        // Set exclusive group if provided
        if (exclusiveGroup != null) {
            validation.setExclusive(true);
            validation.setExclusiveGroup(exclusiveGroup);
        }

        parameter.setValidation(validation);

        return parameter;
    }

    private static Parameter createParameterRecommended(String name, String type, String exclusiveGroup, boolean multivalued) {
        Parameter parameter = new Parameter();
        parameter.setId(name);
        parameter.setType(type);
        parameter.setMultivalued(multivalued);

        // Create validation settings
        ConditionValidation validation = new ConditionValidation();

        // Set recommended flag instead of required
        validation.setRequired(false);
        validation.setRecommended(true);

        // Set exclusive group if provided
        if (exclusiveGroup != null) {
            validation.setExclusive(true);
            validation.setExclusiveGroup(exclusiveGroup);
        }

        parameter.setValidation(validation);

        return parameter;
    }

    public static Map<String, ConditionType> getConditionTypes() {
        return conditionTypes;
    }

    public static ConditionType getConditionType(String conditionTypeId) {
        return conditionTypes.get(conditionTypeId);
    }

    private static Date getDate(Object value) {
        return DateUtils.getDate(value);
    }

    private static ConditionEvaluator createIdsConditionEvaluator() {
        return (condition, item, context, dispatcher) -> {
            tracer.startEvaluation(condition, "Evaluating ids condition");

            if (item == null) {
                tracer.endEvaluation(condition, false, "Item is null");
                return false;
            }

            Object idsObj = condition.getParameter("ids");
            if (idsObj == null) {
                tracer.endEvaluation(condition, false, "No ids provided in condition");
                return false;
            }

            Collection<String> ids;
            if (idsObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<String> temp = (Collection<String>) idsObj;
                ids = temp;
            } else {
                tracer.endEvaluation(condition, false, "Ids parameter is not a collection");
                return false;
            }

            if (ids.isEmpty()) {
                tracer.endEvaluation(condition, false, "Empty ids collection");
                return false;
            }

            tracer.trace(condition, "Checking if item id " + item.getItemId() + " is in collection: " + ids);
            boolean result = ids.contains(item.getItemId());
            tracer.endEvaluation(condition, result, "Item id " + (result ? "found" : "not found") + " in collection");
            return result;
        };
    }
}

