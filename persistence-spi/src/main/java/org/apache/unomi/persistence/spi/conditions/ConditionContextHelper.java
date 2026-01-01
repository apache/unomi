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
import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.scripting.ScriptExecutor;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ConditionContextHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionContextHelper.class);

    /**
     * Prefix for parameter references in condition values.
     * Format: "parameter::key" where key is a key in the context map.
     */
    public static final String PARAMETER_REFERENCE_PREFIX = "parameter::";

    /**
     * Prefix for script expressions in condition values.
     * Format: "script::..." where ... is a script to be evaluated.
     */
    public static final String SCRIPT_EXPRESSION_PREFIX = "script::";

    /**
     * Maximum depth for resolving parameter reference chains.
     * Prevents infinite recursion and stack overflow.
     */
    private static final int MAX_RESOLUTION_DEPTH = 50;

    /**
     * Sentinel object to mark resolution errors (cycles or max depth exceeded).
     * This allows us to distinguish between missing parameters (null) and errors.
     */
    private static final Object RESOLUTION_ERROR = new Object();

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
        return getContextualCondition(condition, context, scriptExecutor, null, null);
    }

    /**
     * Resolves parameter references and script expressions in a condition,
     * with optional type validation.
     *
     * @param condition the condition to resolve
     * @param context context map for parameter resolution
     * @param scriptExecutor executor for script expressions
     * @param definitionsService optional service for parameter type information
     * @param tracerService optional tracer service for validation warnings
     * @return resolved condition with all parameter references resolved
     */
    public static Condition getContextualCondition(
        Condition condition,
        Map<String, Object> context,
        ScriptExecutor scriptExecutor,
        DefinitionsService definitionsService,
        TracerService tracerService) {

        // Debug logging

        if (!hasContextualParameter(condition.getParameterValues())) {
            return condition;
        }

        // Ensure context is not null and merge condition's non-reference parameters into it
        // This allows parameter references to resolve from the condition's own parameters
        if (context == null) {
            context = new HashMap<>();
        }
        // Merge condition's parameters into context (for parameter reference resolution)
        // Only merge non-reference/script values to avoid overwriting resolved values
        for (Map.Entry<String, Object> entry : condition.getParameterValues().entrySet()) {
            Object value = entry.getValue();
            if (!isParameterReference(value) && !(value instanceof String && ((String) value).startsWith(SCRIPT_EXPRESSION_PREFIX))) {
                if (!context.containsKey(entry.getKey())) {
                    context.put(entry.getKey(), value);
                }
            }
        }

        ConditionType conditionType = condition.getConditionType();
        Map<String, Parameter> parameterDefs = null;
        if (conditionType != null && definitionsService != null) {
            parameterDefs = new HashMap<>();
            for (Parameter param : conditionType.getParameters()) {
                parameterDefs.put(param.getId(), param);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) parseParameterWithValidation(
            context, condition.getParameterValues(), scriptExecutor,
            parameterDefs, tracerService, condition.getConditionTypeId());

        if (values == null) {
            return null;
        }
        Condition n = new Condition(condition.getConditionType());
        n.setParameterValues(values);

        return n;
    }

    @SuppressWarnings("unchecked")
    private static Object parseParameter(Map<String, Object> context, Object value, ScriptExecutor scriptExecutor) {
        return parseParameterWithValidation(context, value, scriptExecutor, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static Object parseParameterWithValidation(
        Map<String, Object> context,
        Object value,
        ScriptExecutor scriptExecutor,
        Map<String, Parameter> parameterDefs,
        TracerService tracerService,
        String conditionTypeId) {

        return parseParameterWithValidationRecursive(
            context, value, scriptExecutor, parameterDefs,
            tracerService, conditionTypeId, new HashSet<>(), 0);
    }

    /**
     * Recursively resolves parameter references and script expressions,
     * handling chains of references and detecting cycles.
     *
     * @param context context map for parameter resolution
     * @param value the value to resolve
     * @param scriptExecutor executor for script expressions
     * @param parameterDefs optional parameter definitions for validation
     * @param tracerService optional tracer service for warnings
     * @param conditionTypeId condition type ID for context
     * @param resolutionChain set of parameter keys/scripts already being resolved (for cycle detection)
     * @param depth current resolution depth
     * @return resolved value, or null if cycle detected or max depth exceeded
     */
    @SuppressWarnings("unchecked")
    private static Object parseParameterWithValidationRecursive(
        Map<String, Object> context,
        Object value,
        ScriptExecutor scriptExecutor,
        Map<String, Parameter> parameterDefs,
        TracerService tracerService,
        String conditionTypeId,
        Set<String> resolutionChain,
        int depth) {

        // Check maximum depth to prevent stack overflow
        if (depth >= MAX_RESOLUTION_DEPTH) {
            String message = String.format(
                "Maximum resolution depth (%d) exceeded when resolving parameter reference. " +
                "Possible infinite chain or very deep nesting. Resolution chain: %s",
                MAX_RESOLUTION_DEPTH, resolutionChain);
            LOGGER.error(message);
            if (tracerService != null) {
                RequestTracer tracer = tracerService.getCurrentTracer();
                if (tracer != null && tracer.isEnabled()) {
                    Map<String, Object> traceContext = new HashMap<>();
                    traceContext.put("maxDepth", MAX_RESOLUTION_DEPTH);
                    traceContext.put("resolutionChain", new ArrayList<>(resolutionChain));
                    traceContext.put("conditionTypeId", conditionTypeId);
                    tracer.trace("Parameter resolution depth exceeded", traceContext);
                }
            }
            return RESOLUTION_ERROR;
        }

        if (isParameterReference(value)) {
            String s = (String) value;
            String referenceKey = s; // Use full string as key for cycle detection

            // Check for cyclic reference
            if (resolutionChain.contains(referenceKey)) {
                String message = String.format(
                    "Circular parameter reference detected: %s. Resolution chain: %s",
                    referenceKey, resolutionChain);
                LOGGER.warn(message);
                if (tracerService != null) {
                    RequestTracer tracer = tracerService.getCurrentTracer();
                    if (tracer != null && tracer.isEnabled()) {
                        Map<String, Object> traceContext = new HashMap<>();
                        traceContext.put("circularReference", referenceKey);
                        traceContext.put("resolutionChain", new ArrayList<>(resolutionChain));
                        traceContext.put("conditionTypeId", conditionTypeId);
                        tracer.trace("Circular parameter reference detected", traceContext);
                    }
                }
                // Return a special marker to indicate cycle (we'll check for this in Map processing)
                return RESOLUTION_ERROR;
            }

            // Add to resolution chain
            resolutionChain.add(referenceKey);

            try {
                Object resolvedValue = null;

                if (s.startsWith(PARAMETER_REFERENCE_PREFIX)) {
                    String paramKey = StringUtils.substringAfter(s, PARAMETER_REFERENCE_PREFIX);
                    resolvedValue = context.get(paramKey);

                    if (resolvedValue == null) {
                        LOGGER.debug("Parameter reference '{}' not found in context", paramKey);
                    }
                } else if (s.startsWith(SCRIPT_EXPRESSION_PREFIX)) {
                    if (scriptExecutor == null) {
                        LOGGER.warn("Script executor is null, cannot execute script expression: {}", s);
                        return RESOLUTION_ERROR;
                    }
                    String script = StringUtils.substringAfter(s, SCRIPT_EXPRESSION_PREFIX);
                    resolvedValue = scriptExecutor.execute(script, context);
                }

                // If resolved value is itself a parameter reference, continue resolving
                if (resolvedValue != null && isParameterReference(resolvedValue)) {
                    Object furtherResolved = parseParameterWithValidationRecursive(
                        context, resolvedValue, scriptExecutor, parameterDefs,
                        tracerService, conditionTypeId, resolutionChain, depth + 1);
                    // If further resolution returns null due to cycle or max depth, propagate it
                    // But if it's just a missing parameter, return null (not a cycle)
                    return furtherResolved;
                }

                // Return resolved value (can be null if parameter not found, which is valid)
                return resolvedValue;
            } finally {
                // Remove from resolution chain when done (allows same reference at different levels)
                resolutionChain.remove(referenceKey);
            }
        } else if (value instanceof Map) {
            Map<String, Object> values = new HashMap<String, Object>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();
                Parameter paramDef = parameterDefs != null ? parameterDefs.get(paramName) : null;

                Object parameter = parseParameterWithValidationRecursive(
                    context, paramValue, scriptExecutor, parameterDefs,
                    tracerService, conditionTypeId, resolutionChain, depth);

                // If resolution returned an error marker, return null for entire map
                if (parameter == RESOLUTION_ERROR) {
                    return null;
                }

                // Validate type if parameter definition is available and value is not null
                if (parameter != null && paramDef != null && paramDef.getType() != null) {
                    validateParameterType(paramName, parameter, paramDef,
                        conditionTypeId, tracerService);
                }

                // Always put the value, even if null (missing parameter is valid)
                values.put(entry.getKey(), parameter);
            }
            return values;
        } else if (value instanceof List) {
            List<Object> values = new ArrayList<Object>();
            for (Object o : ((List<?>) value)) {
                Object parameter = parseParameterWithValidationRecursive(
                    context, o, scriptExecutor, parameterDefs,
                    tracerService, conditionTypeId, resolutionChain, depth);
                // If resolution returned an error marker, skip this element
                if (parameter == RESOLUTION_ERROR) {
                    continue;
                }
                // Add the value, even if null (missing parameter is valid in lists)
                values.add(parameter);
            }
            return values;
        }
        return value;
    }

    /**
     * Validates that a resolved parameter value matches the expected type.
     * Logs warnings via tracer if type mismatch is detected.
     *
     * @param paramName parameter name
     * @param resolvedValue resolved parameter value
     * @param paramDef parameter definition
     * @param conditionTypeId condition type ID for context
     * @param tracerService optional tracer service
     */
    private static void validateParameterType(
        String paramName,
        Object resolvedValue,
        Parameter paramDef,
        String conditionTypeId,
        TracerService tracerService) {

        if (resolvedValue == null || paramDef == null || paramDef.getType() == null) {
            return;
        }

        String expectedType = paramDef.getType().toLowerCase();
        String actualType = getValueType(resolvedValue);

        if (!isTypeCompatible(actualType, expectedType)) {
            String message = String.format(
                "Parameter '%s' in condition type '%s' resolved to type '%s' but expected '%s'",
                paramName, conditionTypeId, actualType, expectedType);

            LOGGER.warn(message + " (value: {})", resolvedValue);

            if (tracerService != null) {
                RequestTracer tracer = tracerService.getCurrentTracer();
                if (tracer != null && tracer.isEnabled()) {
                    Map<String, Object> traceContext = new HashMap<>();
                    traceContext.put("parameter", paramName);
                    traceContext.put("expectedType", expectedType);
                    traceContext.put("actualType", actualType);
                    traceContext.put("value", resolvedValue);
                    traceContext.put("conditionTypeId", conditionTypeId);
                    tracer.trace("Parameter type mismatch detected", traceContext);
                }
            }
        }
    }

    /**
     * Gets the type name of a value for validation purposes.
     *
     * @param value the value to check
     * @return type name (e.g., "integer", "date", "string")
     */
    private static String getValueType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return "integer";
        }
        if (value instanceof Float || value instanceof Double) {
            return "float";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Date || value instanceof Instant ||
            value instanceof OffsetDateTime || value instanceof ZonedDateTime ||
            value instanceof LocalDateTime) {
            return "date";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Condition) {
            return "condition";
        }
        if (value instanceof Collection) {
            return "collection";
        }
        if (value instanceof Map) {
            return "object";
        }
        return value.getClass().getSimpleName().toLowerCase();
    }

    /**
     * Checks if an actual type is compatible with an expected type.
     *
     * @param actualType the actual type of the value
     * @param expectedType the expected type from parameter definition
     * @return true if types are compatible
     */
    private static boolean isTypeCompatible(String actualType, String expectedType) {
        if (actualType == null || expectedType == null) {
            return true; // Can't validate without type info
        }

        // Exact match
        if (actualType.equals(expectedType)) {
            return true;
        }

        // Integer/Long compatibility
        if (("integer".equals(expectedType) || "long".equals(expectedType)) &&
            ("integer".equals(actualType) || "long".equals(actualType))) {
            return true;
        }

        // Float/Double compatibility
        if (("float".equals(expectedType) || "double".equals(expectedType)) &&
            ("float".equals(actualType) || "double".equals(actualType))) {
            return true;
        }

        // Date compatibility (various date types)
        if ("date".equals(expectedType) &&
            (actualType.contains("date") || actualType.contains("time") ||
             actualType.contains("instant"))) {
            return true;
        }

        // String can often be converted, so be lenient
        // But log warnings for type mismatches that might indicate issues

        return false;
    }

    /**
     * Checks if a value or any nested value contains parameter references or script expressions.
     * Recursively checks Maps, Lists, Collections, and Condition objects.
     *
     * @param value the value to check
     * @return true if the value contains any parameter references or script expressions
     */
    public static boolean hasContextualParameter(Object value) {
        if (value == null) {
            return false;
        }

        // Check if value itself is a reference/script
        if (isParameterReference(value)) {
            return true;
        }

        // Check nested conditions
        if (value instanceof Condition) {
            Condition nestedCondition = (Condition) value;
            Map<String, Object> nestedParams = nestedCondition.getParameterValues();
            if (nestedParams != null) {
                for (Object nestedValue : nestedParams.values()) {
                    if (hasContextualParameter(nestedValue)) {
                        return true;
                    }
                }
            }
        } else if (value instanceof Map) {
            for (Object o : ((Map<?, ?>) value).values()) {
                if (hasContextualParameter(o)) {
                    return true;
                }
            }
        } else if (value instanceof Collection) {
            for (Object o : ((Collection<?>) value)) {
                if (hasContextualParameter(o)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if a value is a parameter reference or script expression that
     * needs to be resolved later.
     *
     * Parameter references use the format "parameter::key" where key is a
     * key in the context map. Script expressions use the format "script::..."
     * where ... is a script to be evaluated.
     *
     * @param value the value to check
     * @return true if the value is a parameter reference or script expression
     */
    public static boolean isParameterReference(Object value) {
        if (value instanceof String) {
            String stringValue = (String) value;
            return stringValue.startsWith(PARAMETER_REFERENCE_PREFIX) ||
                   stringValue.startsWith(SCRIPT_EXPRESSION_PREFIX);
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
