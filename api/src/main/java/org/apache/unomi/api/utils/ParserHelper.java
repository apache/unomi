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
import org.apache.unomi.api.services.TypeResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilities for resolving condition, action, and value type references when
 * importing JSON definitions. Walks condition trees, fills in missing type
 * metadata, and extracts event types referenced by conditions.
 */
public class ParserHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParserHelper.class);

    private static final Set<String> unresolvedActionTypes = ConcurrentHashMap.newKeySet();
    private static final Set<String> unresolvedConditionTypes = ConcurrentHashMap.newKeySet();
    // Track rules that have already been warned about null/empty actions to avoid log spam
    private static final Set<String> warnedRulesWithNullActions = ConcurrentHashMap.newKeySet();

    private static final String VALUE_NAME_SEPARATOR = "::";
    private static final String PLACEHOLDER_PREFIX = "${";
    private static final String PLACEHOLDER_SUFFIX = "}";

    private static final int MAX_RECURSION_DEPTH = 1000;

    /**
     * A visitor implementation used by {@link ParserHelper} to traverse and
     * process {@link Condition} nodes found within parsed
     * definitions (e.g., from JSON).
     * Subclasses must implement the required methods to define how conditions
     * are visited upon entry and exited upon completion of processing.
     */
    public interface ConditionVisitor {
        /**
         * Visits a given {@link Condition} object, executing necessary logic
         * for that specific condition during traversal.
         * @param condition The condition being visited.
         */
        void visit(Condition condition);
        /**
         * Executes post-visit logic for the specified {@link Condition} object.
         * This method is called after all descendants of the given condition
         * have been processed.
         * @param condition The condition whose children have
         * finished processing.
         */
        void postVisit(Condition condition);
    }

    /**
     * Utility class responsible for extracting and converting raw string values
     * found in configuration definitions (such as conditions or actions) into
     * usable Java objects.
     * This abstract helper requires subclasses to implement the core logic of
     * value extraction, utilizing both the {@code String} representation of the
     * value and the associated {@link Event} context for resolution.
     */
    public interface ValueExtractor {
        /**
         * Extracts an object value from a given string representation using the
         * provided event context.
         * This method is abstract and must be implemented by
         * concrete subclasses.
         * @param valueAsString The string value that needs to be
         * extracted or parsed.
         * @param event The {@link Event} providing context for
         * the extraction process.
         * @return An object representing the extracted value.
         * @throws IllegalAccessException if access to a required member fails.
         * @throws NoSuchMethodException if a necessary method cannot be found.
         * @throws InvocationTargetException if an exception occurs during
         * invocation of the target method.
         */
        Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException;
    }

    /**
     * Default map containing predefined {@link ValueExtractor} implementations.
     * These extractors are used to retrieve values from various sources
     * (profile, session, event) when processing definitions.
     */
    public static final Map<String,ValueExtractor> DEFAULT_VALUE_EXTRACTORS = new HashMap<>();
    static {
        DEFAULT_VALUE_EXTRACTORS.put("profileProperty", (valueAsString, event) -> PropertyUtils.getProperty(event.getProfile(), "properties." + valueAsString));
        DEFAULT_VALUE_EXTRACTORS.put("simpleProfileProperty", (valueAsString, event) -> event.getProfile().getProperty(valueAsString));
        DEFAULT_VALUE_EXTRACTORS.put("sessionProperty", (valueAsString, event) -> PropertyUtils.getProperty(event.getSession(), "properties." + valueAsString));
        DEFAULT_VALUE_EXTRACTORS.put("simpleSessionProperty", (valueAsString, event) -> event.getSession().getProperty(valueAsString));
        DEFAULT_VALUE_EXTRACTORS.put("eventProperty", (valueAsString, event) -> PropertyUtils.getProperty(event, valueAsString));
        DEFAULT_VALUE_EXTRACTORS.put("simpleEventProperty", (valueAsString, event) -> event.getProperty(valueAsString));
    }

    /**
     * Resolves the condition types recursively starting from a root condition.
     * This method traverses the parent chain and parameter values of the given
     * {@code rootCondition} to ensure all nested conditions have their types
     * resolved against the provided definitions service. It updates the
     * condition type on the condition object if successful.
     * {@code false} otherwise.
     * @param definitionsService service used to resolve condition types.
     * @param rootCondition the starting condition for resolution.
     * @param contextObjectName name of the object providing context
     * (for error messages).
     * @return {@code true} if all reachable conditions were
     * successfully resolved,
     */
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
                String conditionTypeId = rootCondition.getConditionTypeId();
                if (conditionTypeId == null) {
                    LOGGER.warn("Condition has no type ID for {}", contextObjectName);
                    return false;
                }
                ConditionType conditionType = definitionsService.getConditionType(conditionTypeId);
                if (conditionType == null) {
                    if (!unresolvedConditionTypes.contains(conditionTypeId)) {
                        unresolvedConditionTypes.add(conditionTypeId);
                        LOGGER.warn("Couldn't resolve condition type: {} for {}", conditionTypeId, contextObjectName);
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

    /**
     * Collects a list of unique condition type IDs present within the
     * given root condition.
     * This method traverses the entire structure of the condition, including
     * nested parameters and collections, and gathers all associated
     * condition type ids.
     * @param rootCondition the condition whose types are to be collected.
     * @return a list of unique strings representing the condition type IDs
     * found in the condition tree.
     */
    public static List<String> getConditionTypeIds(Condition rootCondition) {
        final List<String> result = new ArrayList<String>();
        visitConditions(rootCondition, new ConditionVisitor() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void visit(Condition condition) {
                result.add(condition.getConditionTypeId());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void postVisit(Condition condition) {
            }
        });
        return result;
    }

    /**
     * Visits every condition within the given condition structure, executing
     * the provided visitor for each one. This method handles recursive
     * traversal through parameters and collections that may
     * contain nested conditions.
     * @param rootCondition the starting condition to visit.
     * @param visitor the {@link ConditionVisitor} implementation to execute on
     * every visited condition.
     */
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

    /**
     * Resolves action types for all actions contained within a given rule.
     * This method checks if the rule has null or empty actions. If not, it
     * attempts to resolve the type of each action using the
     * {@link DefinitionsService}. It logs warnings if resolution fails and
     * returns {@code false} if any structural error (null/empty actions) or
     * resolution failure occurs.
     * @param definitionsService service used to resolve action types.
     * @param rule the rule containing actions whose types need resolving.
     * @param ignoreErrors if {@code true}, warnings about missing or empty
     * actions are suppressed.
     * @return {@code true} if all actions were successfully resolved,
     * {@code false} otherwise.
     */
    public static boolean resolveActionTypes(DefinitionsService definitionsService, Rule rule, boolean ignoreErrors) {
        boolean result = true;
        String ruleId = rule.getItemId();
        if (rule.getActions() == null) {
            if (!ignoreErrors) {
                // Only warn once per rule to avoid log spam
                if (warnedRulesWithNullActions.add(ruleId)) {
                    LOGGER.warn("Rule {}:{} has null actions", ruleId, rule.getMetadata().getName());
                }
            }
            return false;
        }
        if (rule.getActions().isEmpty()) {
            if (!ignoreErrors) {
                // Only warn once per rule to avoid log spam
                if (warnedRulesWithNullActions.add(ruleId)) {
                    LOGGER.warn("Rule {}:{} has empty actions", ruleId, rule.getMetadata().getName());
                }
            }
            return false;
        }
        TypeResolutionService typeResolutionService = definitionsService != null ? definitionsService.getTypeResolutionService() : null;
        for (Action action : rule.getActions()) {
            if (typeResolutionService != null) {
                result &= typeResolutionService.resolveActionType(action);
            } else {
                // Fallback to direct resolution if TypeResolutionService is not available
                result &= ParserHelper.resolveActionType(definitionsService, action);
            }
        }
        return result;
    }

    /**
     * Attempts to resolve the action type for a single given action.
     * If the action's type is not already set, this method queries the
     * definitions service to find and set the correct {@link ActionType}. It
     * tracks unresolved types internally, logging warnings if resolution fails.
     * {@code false} otherwise.
     * @param definitionsService service used to resolve action types.
     * @param action the action whose type needs resolving.
     * @return {@code true} if the action's type was successfully
     * resolved or already set,
     */
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

    /**
     * Resolves the value type for a given property type.
     * This method first attempts to use the {@link DefinitionsService}'s
     * internal {@link TypeResolutionService}. If that service is unavailable,
     * it falls back to resolving the value type directly using the definitions
     * service's lookup mechanism.
     * The provided {@code PropertyType} object will be modified if successful.
     * @param definitionsService service used to resolve property types.
     * @param propertyType the property type whose value needs resolution.
     */
    public static void resolveValueType(DefinitionsService definitionsService, PropertyType propertyType) {
        if (definitionsService == null) {
            return;
        }
        TypeResolutionService typeResolutionService = definitionsService.getTypeResolutionService();
        if (typeResolutionService != null) {
            typeResolutionService.resolveValueType(propertyType);
            return;
        }
        // Fallback to direct resolution if TypeResolutionService is not available
        if (propertyType.getValueType() == null) {
            ValueType valueType = definitionsService.getValueType(propertyType.getValueTypeId());
            if (valueType != null) {
                propertyType.setValueType(valueType);
            }
        }
    }


    /**
     * @deprecated Use {@link #resolveConditionEventTypes(Condition, DefinitionsService)} instead.
     * @param rootCondition the condition whose event types are to be resolved
     * @return a set of unique event type identifiers
     */
    @Deprecated
    public static Set<String> resolveConditionEventTypes(Condition rootCondition) {
        return resolveConditionEventTypes(rootCondition, null);
    }

    /**
     * Resolves all unique event types associated with a condition structure.
     * This method traverses the entire condition tree starting from
     * {@code rootCondition} and collects every distinct event type ID found in
     * any nested condition or parameter.
     * @param rootCondition the condition whose event types are to be resolved.
     * @param definitionsService service used for resolving event types.
     * @return a set of unique strings representing all event types
     * associated with the condition.
     */
    public static Set<String> resolveConditionEventTypes(Condition rootCondition, DefinitionsService definitionsService) {
        if (rootCondition == null) {
            return new HashSet<>();
        }
        EventTypeConditionVisitor eventTypeConditionVisitor = new EventTypeConditionVisitor(definitionsService);
        visitConditions(rootCondition, eventTypeConditionVisitor);
        return eventTypeConditionVisitor.getEventTypeIds();
    }

    /**
     * A {@link ConditionVisitor} implementation used to traverse condition
     * definitions within a rule or definition structure. It collects all unique
     * event type IDs encountered during the traversal of conditions.
     * This visitor maintains internal state, using a stack to track the current
     * path of visited conditions and accumulating discovered {@code String}
     * event type identifiers in a set.
     */
    public static class EventTypeConditionVisitor implements ConditionVisitor {

        private final DefinitionsService definitionsService;
        private Set<String> eventTypeIds = new HashSet<>();
        private Stack<String> conditionTypeStack = new Stack<>();

        /**
         * Constructs an {@code EventTypeConditionVisitor} and initializes it
         * with a service used to resolve condition types.
         * @param definitionsService The service providing definitions necessary
         * for resolving type information.
         */
        public EventTypeConditionVisitor(DefinitionsService definitionsService) {
            this.definitionsService = definitionsService;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void visit(Condition condition) {
            conditionTypeStack.push(condition.getConditionTypeId());

            // Ensure condition type is resolved before checking parent conditions
            if (definitionsService != null && condition.getConditionType() == null) {
                TypeResolutionService typeResolutionService = definitionsService.getTypeResolutionService();
                if (typeResolutionService != null) {
                    typeResolutionService.resolveConditionType(condition, "eventTypeResolution");
                } else {
                    // Fallback to direct resolution if TypeResolutionService is not available
                    String conditionTypeId = condition.getConditionTypeId();
                    if (conditionTypeId != null) {
                        ConditionType conditionType = definitionsService.getConditionType(conditionTypeId);
                        if (conditionType != null) {
                            condition.setConditionType(conditionType);
                        } else {
                            LOGGER.warn("Condition type {} could not be resolved!", conditionTypeId);
                        }
                    }
                }
            }

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
                // Resolve parent condition type if needed before traversing
                Condition parentCondition = condition.getConditionType().getParentCondition();
                if (definitionsService != null && parentCondition.getConditionType() == null) {
                    TypeResolutionService typeResolutionService = definitionsService.getTypeResolutionService();
                    if (typeResolutionService != null) {
                        typeResolutionService.resolveConditionType(parentCondition, "eventTypeResolution");
                    } else {
                        // Fallback to direct resolution if TypeResolutionService is not available
                        resolveConditionType(definitionsService, parentCondition, "eventTypeResolution");
                    }
                }
                visitConditions(parentCondition, this);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void postVisit(Condition condition) {
            conditionTypeStack.pop();
        }

        /**
         * Retrieves the set of event type IDs that have been collected by
         * this visitor instance.
         * @return A {@link Set} containing all resolved event type identifiers.
         */
        public Set<String> getEventTypeIds() {
            return eventTypeIds;
        }
    }

    /**
     * Parses a map of values, resolving any placeholders found within string
     * values using provided extractors and event context.
     * This method iterates through the input map. If a value is a String
     * containing placeholders (e.g., ${placeholder}), it recursively resolves
     * these placeholders by calling {@link #extractValue(String, Event, Map)}.
     * The resulting map contains the processed values with resolved objects
     * instead of placeholder strings.
     * @param event The current event used for value
     * extraction during resolution.
     * @param map The input map containing potentially
     * placeholder-filled values.
     * @param valueExtractors A map linking value type names to their
     * respective extractors.
     * @return A new {@link Map<String, Object>} with the string placeholders
     * resolved into actual object values.
     */
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

    /**
     * Extracts an object value from a string that follows a specific
     * format (type::value).
     * The method splits the input string using "::" to separate the expected
     * value type from the raw value content. It then uses the provided
     * {@link ValueExtractor} to perform the actual extraction based
     * on the event context.
     * @param s The input string containing the type and
     * value separated by "::".
     * @param event The current event used during value extraction.
     * @param valueExtractors A map of available extractors
     * keyed by value type name.
     * @return The extracted object value, or null if no extractor is found
     * or extraction fails.
     * @throws IllegalAccessException If the underlying extractor
     * throws this exception.
     * @throws NoSuchMethodException If the underlying extractor
     * throws this exception.
     * @throws InvocationTargetException If the underlying extractor
     * throws this exception.
     */
    public static Object extractValue(String s, Event event, Map<String, ValueExtractor> valueExtractors) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Object value = null;

        String valueType = StringUtils.substringBefore(s, VALUE_NAME_SEPARATOR);
        String valueAsString = StringUtils.substringAfter(s, VALUE_NAME_SEPARATOR);
        ValueExtractor extractor = valueExtractors.get(valueType);
        if (extractor != null) {
            try {
                value = extractor.extract(valueAsString, event);
            } catch (Exception e) {
                LOGGER.warn("Failed to extract value from event type {} using {}, will return null instead", event.getEventType(), valueAsString, e);
                return null;
            }
        }

        return value;
    }

    /**
     * Determines if any string value within the provided map (including nested
     * maps) contains a placeholder that references a known
     * contextual parameter type.
     * This method recursively checks all values. For strings, it attempts to
     * extract content from placeholders and validates if the resulting type
     * name matches an available extractor.
     * @param values The map of parameters to check for contextual parameters.
     * @param valueExtractors A map containing all available value extractors,
     * used to validate parameter types.
     * @return True if at least one string value represents a contextual
     * parameter; otherwise, false.
     */
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

    /**
     * Gets the full parent chain for a condition type.
     * This method iteratively collects all parent conditions, detecting
     * circular references.
     * @param conditionType the condition type
     * @param definitionsService service to resolve types
     * @param contextName name for error messages
     * @param maxDepth maximum depth to traverse
     * @return list of parent conditions (ordered from immediate to root),
     *         or null if circular reference detected or max depth exceeded
     */
    public static List<Condition> getParentChain(
        ConditionType conditionType,
        DefinitionsService definitionsService,
        String contextName,
        int maxDepth) {

        if (conditionType == null || definitionsService == null) {
            return new ArrayList<>();
        }

        List<Condition> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        ConditionType current = conditionType;
        int depth = 0;

        while (current != null && current.getParentCondition() != null && depth < maxDepth) {
            Condition parentCondition = current.getParentCondition();

            // Resolve parent condition type if needed
            if (parentCondition.getConditionType() == null) {
                TypeResolutionService typeResolutionService = definitionsService.getTypeResolutionService();
                if (typeResolutionService != null) {
                    typeResolutionService.resolveConditionType(parentCondition, contextName);
                } else {
                    // Fallback to direct resolution if TypeResolutionService is not available
                    resolveConditionType(definitionsService, parentCondition, contextName);
                }
            }

            ConditionType parentType = parentCondition.getConditionType();
            if (parentType == null) {
                LOGGER.warn("Parent condition type could not be resolved for {} in {}",
                    current.getItemId(), contextName);
                break;
            }

            String parentId = parentType.getItemId();

            // Check for circular reference
            if (visited.contains(parentId)) {
                LOGGER.warn("Circular reference detected in parent chain for {} in {}: {}",
                    conditionType.getItemId(), contextName, visited);
                return null;
            }

            visited.add(parentId);
            chain.add(parentCondition);

            current = parentType;
            depth++;
        }

        if (depth >= maxDepth) {
            LOGGER.warn("Maximum depth ({}) exceeded when traversing parent chain for {} in {}",
                maxDepth, conditionType.getItemId(), contextName);
            return null;
        }

        return chain;
    }

    /**
     * Deep copies a parameter value, handling Condition objects and collections containing Conditions.
     * This is a helper method to avoid code duplication when merging parameters.
     * @param value the parameter value to deep copy
     * @return a deep copy of the value, or the original value if it's not a Condition or collection
     */
    private static Object deepCopyParameterValue(Object value) {
        if (value instanceof Condition) {
            return ((Condition) value).deepCopy();
        } else if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            Collection<Object> copiedCollection = new ArrayList<>();
            for (Object item : collection) {
                if (item instanceof Condition) {
                    copiedCollection.add(((Condition) item).deepCopy());
                } else {
                    copiedCollection.add(item);
                }
            }
            return copiedCollection;
        } else {
            return value;
        }
    }

    /**
     * Resolves the effective condition to use, following the parent chain.
     * This method traverses the parent condition chain and returns the condition
     * at the end of the chain (the root parent). It merges parameters from all
     * levels in the chain into the context and the effective condition.
     * @param condition the condition to resolve
     * @param definitionsService service to resolve condition types
     * @param context context map for parameter merging (will be modified)
     * @param contextName name for error messages
     * @return the effective condition (may be from parent chain), or the original condition if no parent chain
     */
    public static Condition resolveEffectiveCondition(
        Condition condition,
        DefinitionsService definitionsService,
        Map<String, Object> context,
        String contextName) {
        return resolveEffectiveCondition(condition, definitionsService, context,
            contextName, MAX_RECURSION_DEPTH);
    }

    /**
     * Resolves the effective condition to use, following the parent chain.
     * @param condition the condition to resolve
     * @param definitionsService service to resolve condition types
     * @param context context map for parameter merging (will be modified)
     * @param contextName name for error messages
     * @param maxDepth maximum depth to traverse (prevents infinite loops)
     * @return the effective condition (may be from parent chain), or the original condition if no parent chain
     */
    public static Condition resolveEffectiveCondition(
        Condition condition,
        DefinitionsService definitionsService,
        Map<String, Object> context,
        String contextName,
        int maxDepth) {

        if (condition == null || definitionsService == null) {
            return condition;
        }

        // Ensure condition type is resolved (this also resolves parent conditions)
        // definitionsService is guaranteed non-null here (checked at line 575)
        TypeResolutionService typeResolutionService = definitionsService.getTypeResolutionService();
        if (condition.getConditionType() == null) {
            if (typeResolutionService != null) {
                typeResolutionService.resolveConditionType(condition, contextName);
            } else {
                // Fallback to direct resolution if TypeResolutionService is not available
                resolveConditionType(definitionsService, condition, contextName);
            }
        } else {
            // Even if condition type is already resolved, ensure parent condition is also resolved
            ConditionType type = condition.getConditionType();
            if (type != null && type.getParentCondition() != null && type.getParentCondition().getConditionType() == null) {
                if (typeResolutionService != null) {
                    typeResolutionService.resolveConditionType(type.getParentCondition(), contextName);
                } else {
                    // Fallback to direct resolution if TypeResolutionService is not available
                    resolveConditionType(definitionsService, type.getParentCondition(), contextName);
                }
            }
        }

        ConditionType type = condition.getConditionType();
        if (type == null) {
            return condition;
        }
        if (type.getParentCondition() == null) {
            return condition;
        }

        List<Condition> parentChain = getParentChain(type, definitionsService,
            contextName, maxDepth);
        if (parentChain == null) {
            LOGGER.warn("Failed to build parent chain for condition type '{}' in '{}' (cycle or max depth exceeded), cannot resolve effective condition",
                type.getItemId(), contextName);
            return null;
        }
        if (parentChain.isEmpty()) {
            return condition;
        }

        // Use the last parent in the chain (root parent)
        Condition rootParent = parentChain.get(parentChain.size() - 1);

        // Create new condition from root parent with deep copy
        Condition effectiveCondition = rootParent.deepCopy();

        // Merge all parameters from chain into context
        // Start with condition's parameters
        if (context != null) {
            context.putAll(condition.getParameterValues());
        }

        // Merge parameters from all parents in the chain (skip rootParent as it's already copied)
        for (Condition parent : parentChain) {
            if (parent == rootParent) {
                continue; // Already copied above
            }
            if (context != null) {
                context.putAll(parent.getParameterValues());
            }
            // Merge into effective condition (only add parameters not already present, deep copying if nested condition)
            for (Map.Entry<String, Object> entry : parent.getParameterValues().entrySet()) {
                if (!effectiveCondition.getParameterValues().containsKey(entry.getKey())) {
                    effectiveCondition.getParameterValues().put(entry.getKey(), deepCopyParameterValue(entry.getValue()));
                }
            }
        }

        // Merge condition parameters into effective condition (highest priority)
        // Deep copy nested conditions from condition as well
        for (Map.Entry<String, Object> entry : condition.getParameterValues().entrySet()) {
            effectiveCondition.getParameterValues().put(entry.getKey(), deepCopyParameterValue(entry.getValue()));
        }

        // Resolve the effective condition's type to ensure nested conditions are resolved
        if (typeResolutionService != null) {
            typeResolutionService.resolveConditionType(effectiveCondition, contextName + " (effective condition)");
        } else {
            // Fallback to direct resolution if TypeResolutionService is not available
            resolveConditionType(definitionsService, effectiveCondition, contextName + " (effective condition)");
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolved effective condition: original={}, effective={}, chainDepth={}",
                condition.getConditionTypeId(),
                effectiveCondition.getConditionTypeId(),
                parentChain.size());
        }

        return effectiveCondition;
    }

}
