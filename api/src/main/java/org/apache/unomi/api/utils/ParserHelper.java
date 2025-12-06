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

package org.apache.unomi.api.utils;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.DefinitionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Helper class to resolve condition, action and values types when loading definitions from JSON files
 */
public class ParserHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParserHelper.class);

    private static final Set<String> unresolvedActionTypes = new HashSet<>();
    private static final Set<String> unresolvedConditionTypes = new HashSet<>();

    private static final String VALUE_NAME_SEPARATOR = "::";
    private static final String PLACEHOLDER_PREFIX = "${";
    private static final String PLACEHOLDER_SUFFIX = "}";
    
    private static final int MAX_RECURSION_DEPTH = 1000;

    public interface ConditionVisitor {
        void visit(Condition condition);
        void postVisit(Condition condition);
    }

    public interface ValueExtractor {
        Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException;
    }

    public static final Map<String,ValueExtractor> DEFAULT_VALUE_EXTRACTORS = new HashMap<>();
    static {
        DEFAULT_VALUE_EXTRACTORS.put("profileProperty", (valueAsString, event) -> PropertyUtils.getProperty(event.getProfile(), "properties." + valueAsString));
        DEFAULT_VALUE_EXTRACTORS.put("simpleProfileProperty", (valueAsString, event) -> event.getProfile().getProperty(valueAsString));
        DEFAULT_VALUE_EXTRACTORS.put("sessionProperty", (valueAsString, event) -> PropertyUtils.getProperty(event.getSession(), "properties." + valueAsString));
        DEFAULT_VALUE_EXTRACTORS.put("simpleSessionProperty", (valueAsString, event) -> event.getSession().getProperty(valueAsString));
        DEFAULT_VALUE_EXTRACTORS.put("eventProperty", (valueAsString, event) -> PropertyUtils.getProperty(event, valueAsString));
        DEFAULT_VALUE_EXTRACTORS.put("simpleEventProperty", (valueAsString, event) -> event.getProperty(valueAsString));
    }

    public static boolean resolveConditionType(final DefinitionsService definitionsService, Condition rootCondition, String contextObjectName) {
        return resolveConditionType(definitionsService, rootCondition, contextObjectName, 
                new HashSet<>(), false, 0);
    }

    private static boolean resolveConditionType(final DefinitionsService definitionsService, Condition rootCondition,
            String contextObjectName, Set<String> parentChainPath, boolean isGoingUp, int depth) {
        if (rootCondition == null) {
            LOGGER.warn("Couldn't resolve null condition for {}", contextObjectName);
            return false;
        }

        if (depth > MAX_RECURSION_DEPTH) {
            LOGGER.error("Maximum recursion depth ({}) exceeded when resolving condition type {} in {}",
                    MAX_RECURSION_DEPTH, rootCondition.getConditionTypeId(), contextObjectName);
            return false;
        }

        if (isGoingUp) {
            if (!parentChainPath.add(rootCondition.getConditionTypeId())) {
                LOGGER.warn("Detected circular reference for condition type {} in {}", rootCondition.getConditionTypeId(), contextObjectName);
                return false;
            }
        }

        try {
            // Resolve current condition type if needed
            if (rootCondition.getConditionType() == null) {
                ConditionType conditionType = definitionsService.getConditionType(rootCondition.getConditionTypeId());
                if (conditionType == null) {
                    if (!unresolvedConditionTypes.contains(rootCondition.getConditionTypeId())) {
                        unresolvedConditionTypes.add(rootCondition.getConditionTypeId());
                        LOGGER.warn("Couldn't resolve condition type: {} for {}", rootCondition.getConditionTypeId(), contextObjectName);
                    }
                    return false;
                }
                unresolvedConditionTypes.remove(rootCondition.getConditionTypeId());
                rootCondition.setConditionType(conditionType);

                if (conditionType.getParentCondition() != null) {
                    Set<String> pathForParent = new HashSet<>(parentChainPath);
                    if (!isGoingUp) {
                        pathForParent.add(rootCondition.getConditionTypeId());
                    }
                    if (!resolveConditionType(definitionsService, conditionType.getParentCondition(), contextObjectName, 
                            pathForParent, true, depth + 1)) {
                        rootCondition.setConditionType(null);
                        LOGGER.warn("Failed to resolve parent condition for type: {} in {}",
                            rootCondition.getConditionTypeId(), contextObjectName);
                        return false;
                    }
                }
            }

            for (Object value : rootCondition.getParameterValues().values()) {
                if (value instanceof Condition) {
                    if (!resolveConditionType(definitionsService, (Condition) value, contextObjectName, 
                            parentChainPath, false, depth + 1)) {
                        return false;
                    }
                } else if (value instanceof Collection) {
                    for (Object item : (Collection<?>) value) {
                        if (item instanceof Condition) {
                            if (!resolveConditionType(definitionsService, (Condition) item, contextObjectName, 
                                    parentChainPath, false, depth + 1)) {
                                return false;
                            }
                        }
                    }
                }
            }

            return true;
        } finally {
            if (isGoingUp) {
                parentChainPath.remove(rootCondition.getConditionTypeId());
            }
        }
    }

    public static List<String> getConditionTypeIds(Condition rootCondition) {
        final List<String> result = new ArrayList<String>();
        visitConditions(rootCondition, new ConditionVisitor() {
            @Override
            public void visit(Condition condition) {
                result.add(condition.getConditionTypeId());
            }

            @Override
            public void postVisit(Condition condition) {
            }
        });
        return result;
    }

    public static void visitConditions(Condition rootCondition, ConditionVisitor visitor) {
        visitor.visit(rootCondition);
        // recursive call for sub-conditions as parameters
        for (Object parameterValue : rootCondition.getParameterValues().values()) {
            if (parameterValue instanceof Condition) {
                Condition parameterValueCondition = (Condition) parameterValue;
                visitConditions(parameterValueCondition, visitor);
            } else if (parameterValue instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<Object> valueList = (Collection<Object>) parameterValue;
                for (Object value : valueList) {
                    if (value instanceof Condition) {
                        Condition valueCondition = (Condition) value;
                        visitConditions(valueCondition, visitor);
                    }
                }
            }
        }
        visitor.postVisit(rootCondition);
    }

    public static boolean resolveActionTypes(DefinitionsService definitionsService, Rule rule, boolean ignoreErrors) {
        boolean result = true;
        if (rule.getActions() == null) {
            if (!ignoreErrors) {
                LOGGER.warn("Rule {}:{} has null actions", rule.getItemId(), rule.getMetadata().getName());
            }
            return false;
        }
        if (rule.getActions().isEmpty()) {
            if (!ignoreErrors) {
                LOGGER.warn("Rule {}:{} has empty actions", rule.getItemId(), rule.getMetadata().getName());
            }
            return false;
        }
        for (Action action : rule.getActions()) {
            result &= ParserHelper.resolveActionType(definitionsService, action);
        }
        return result;
    }

    public static boolean resolveActionType(DefinitionsService definitionsService, Action action) {
        if (definitionsService == null) {
            return false;
        }
        if (action.getActionType() == null) {
            ActionType actionType = definitionsService.getActionType(action.getActionTypeId());
            if (actionType != null) {
                unresolvedActionTypes.remove(action.getActionTypeId());
                action.setActionType(actionType);
            } else {
                if (!unresolvedActionTypes.contains(action.getActionTypeId())) {
                    LOGGER.warn("Couldn't resolve action type : {}", action.getActionTypeId());
                    unresolvedActionTypes.add(action.getActionTypeId());
                }
                return false;
            }
        }
        return true;
    }

    public static void resolveValueType(DefinitionsService definitionsService, PropertyType propertyType) {
        if (propertyType.getValueType() == null) {
            ValueType valueType = definitionsService.getValueType(propertyType.getValueTypeId());
            if (valueType != null) {
                propertyType.setValueType(valueType);
            }
        }
    }


    public static Set<String> resolveConditionEventTypes(Condition rootCondition) {
        if (rootCondition == null) {
            return new HashSet<>();
        }
        EventTypeConditionVisitor eventTypeConditionVisitor = new EventTypeConditionVisitor();
        visitConditions(rootCondition, eventTypeConditionVisitor);
        return eventTypeConditionVisitor.getEventTypeIds();
    }

    public static class EventTypeConditionVisitor implements ConditionVisitor {

        private Set<String> eventTypeIds = new HashSet<>();
        private Stack<String> conditionTypeStack = new Stack<>();

        public void visit(Condition condition) {
            conditionTypeStack.push(condition.getConditionTypeId());
            if ("eventTypeCondition".equals(condition.getConditionTypeId())) {
                String eventTypeId = (String) condition.getParameter("eventTypeId");
                if (eventTypeId == null) {
                    LOGGER.warn("Null eventTypeId found!");
                } else {
                    // we must now check the stack to see how many notConditions we have in the parent stack
                    if (conditionTypeStack.contains("notCondition")) {
                        LOGGER.warn("Found complex negative event type condition, will always evaluate rule");
                        eventTypeIds.add("*");
                    } else {
                        eventTypeIds.add(eventTypeId);
                    }
                }
            } else if (condition.getConditionType() != null && condition.getConditionType().getParentCondition() != null) {
                visitConditions(condition.getConditionType().getParentCondition(), this);
            }
        }

        public void postVisit(Condition condition) {
            conditionTypeStack.pop();
        }

        public Set<String> getEventTypeIds() {
            return eventTypeIds;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMap(Event event, Map<String, Object> map, Map<String, ValueExtractor> valueExtractors) {
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                try {
                    if (s.contains(PLACEHOLDER_PREFIX)) {
                        while (s.contains(PLACEHOLDER_PREFIX)) {
                            String substring = s.substring(s.indexOf(PLACEHOLDER_PREFIX) + 2, s.indexOf(PLACEHOLDER_SUFFIX));
                            Object v = extractValue(substring, event, valueExtractors);
                            if (v != null) {
                                s = s.replace(PLACEHOLDER_PREFIX + substring + PLACEHOLDER_SUFFIX, v.toString());
                            } else {
                                break;
                            }
                        }
                        value = s;
                    } else {
                        // check if we have special values
                        if (s.contains(VALUE_NAME_SEPARATOR)) {
                            value = extractValue(s, event, valueExtractors);
                        }
                    }
                } catch (UnsupportedOperationException e) {
                    throw e;
                } catch (Exception e) {
                    throw new UnsupportedOperationException(e);
                }
            } else if (value instanceof Map) {
                value = parseMap(event, (Map<String, Object>) value, valueExtractors);
            }
            values.put(entry.getKey(), value);
        }
        return values;
    }

    public static Object extractValue(String s, Event event, Map<String, ValueExtractor> valueExtractors) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Object value = null;

        String valueType = StringUtils.substringBefore(s, VALUE_NAME_SEPARATOR);
        String valueAsString = StringUtils.substringAfter(s, VALUE_NAME_SEPARATOR);
        ValueExtractor extractor = valueExtractors.get(valueType);
        if (extractor != null) {
            try {
                value = extractor.extract(valueAsString, event);
            } catch (Exception e) {
                LOGGER.warn("Failed to extract value from event type {} using {} : {}, will return null instead", event.getEventType(), valueAsString, e.getMessage());
                return null;
            }
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    public static boolean hasContextualParameter(Map<String, Object> values, Map<String, ValueExtractor> valueExtractors) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                String str = s.contains(PLACEHOLDER_PREFIX) ?
                        s.substring(s.indexOf(PLACEHOLDER_PREFIX) + 2, s.indexOf(PLACEHOLDER_SUFFIX)) :
                        s;

                if (str.contains(VALUE_NAME_SEPARATOR) && valueExtractors
                        .containsKey(StringUtils.substringBefore(str, VALUE_NAME_SEPARATOR))) {
                    return true;
                }
            } else if (value instanceof Map) {
                if (hasContextualParameter((Map<String, Object>) value, valueExtractors)) {
                    return true;
                }
            }
        }
        return false;
    }

}
