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
package org.apache.unomi.services.impl.validation;

import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.conditions.ConditionValidation;
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.api.services.ValueTypeValidator;
import org.apache.unomi.services.impl.validation.validators.ConditionValueTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ConditionValidationServiceImpl implements ConditionValidationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionValidationServiceImpl.class);

    private final Map<String, ValueTypeValidator> validators = new ConcurrentHashMap<>();
    private List<ValueTypeValidator> builtInValidators;

    public void setBuiltInValidators(List<ValueTypeValidator> builtInValidators) {
        this.builtInValidators = builtInValidators;
        for (ValueTypeValidator validator : builtInValidators) {
            validators.put(validator.getValueTypeId().toLowerCase(), validator);
        }
        LOGGER.info("Initialized with {} built-in validators", builtInValidators.size());
    }

    public void bindValidator(ValueTypeValidator validator) {
        validators.put(validator.getValueTypeId().toLowerCase(), validator);
        LOGGER.debug("Added custom validator for type: {}", validator.getValueTypeId());
    }

    public void unbindValidator(ValueTypeValidator validator) {
        String typeId = validator.getValueTypeId().toLowerCase();
        // Only remove if it's not a built-in validator
        if (builtInValidators.stream().noneMatch(v -> v.getValueTypeId().equalsIgnoreCase(typeId))) {
            validators.remove(typeId);
            LOGGER.debug("Removed custom validator for type: {}", validator.getValueTypeId());
        }
    }

    private Map<String, Object> buildValidationContext(String paramName, Object value, Parameter param,
            String location, Map<String, Object> additionalContext) {
        Map<String, Object> context = new HashMap<>();
        
        // Always include location information
        context.put("location", location);
        
        // Add parameter type information
        if (param != null && param.getType() != null) {
            context.put("parameterType", param.getType().toLowerCase());
        }
        
        // Add value information if present
        if (value != null) {
            context.put("actualValue", value);
            context.put("valueType", value.getClass().getSimpleName());
        }
        
        // Add any additional context
        if (additionalContext != null) {
            context.putAll(additionalContext);
        }
        
        return context;
    }

    @Override
    public List<ValidationError> validate(Condition condition) {
        if (builtInValidators == null) {
            throw new IllegalStateException("ConditionValidationService not properly initialized");
        }
        List<ValidationError> errors = new ArrayList<>();

        if (condition == null) {
            Map<String, Object> context = buildValidationContext(null, null, null, "root condition", null);
            errors.add(new ValidationError(null, "Condition cannot be null",
                ValidationErrorType.MISSING_REQUIRED_PARAMETER, null, null, context, null));
            return errors;
        }

        ConditionType type = condition.getConditionType();
        if (type == null) {
            Map<String, Object> context = buildValidationContext(null, null, null, 
                "condition type", Collections.singletonMap("type", condition.getConditionTypeId()));
            errors.add(new ValidationError(null, "Condition type cannot be null",
                ValidationErrorType.INVALID_CONDITION_TYPE, condition.getConditionTypeId(), null, context, null));
            return errors;
        }

        // Group parameters by exclusive group (only for parameters with validation)
        Map<String, List<Parameter>> exclusiveGroups = new HashMap<>();
        for (Parameter param : type.getParameters()) {
            if (param.getValidation() != null &&
                param.getValidation().isExclusive() &&
                param.getValidation().getExclusiveGroup() != null) {
                String group = param.getValidation().getExclusiveGroup();
                exclusiveGroups.computeIfAbsent(group, k -> new ArrayList<>()).add(param);
            }
        }

        // Check each parameter
        for (Parameter param : type.getParameters()) {
            String paramName = param.getId();
            Object value = condition.getParameter(paramName);
            String location = "condition[" + condition.getConditionTypeId() + "]." + paramName;

            // Always validate basic type and multivalued constraints
            if (value != null) {
                errors.addAll(validateParameterType(paramName, value, param, condition, type, location));
            }

            // Only apply additional validation rules if they are present
            if (param.getValidation() != null) {
                errors.addAll(validateAdditionalRules(paramName, value, param, condition, type, location));
            }
        }

        // Check exclusive parameter groups
        for (Map.Entry<String, List<Parameter>> entry : exclusiveGroups.entrySet()) {
            List<Parameter> group = entry.getValue();
            long valuesCount = group.stream()
                .map(p -> condition.getParameter(p.getId()))
                .filter(Objects::nonNull)
                .count();

            if (valuesCount > 1) {
                String paramNames = group.stream()
                    .map(Parameter::getId)
                    .collect(Collectors.joining(", "));
                String location = "condition[" + condition.getConditionTypeId() + "].exclusiveGroup[" + entry.getKey() + "]";
                Map<String, Object> context = buildValidationContext(null, null, null, location,
                    Map.of("exclusiveGroup", entry.getKey(), "conflictingParameters", paramNames));
                
                errors.add(new ValidationError(null,
                    "Only one of these parameters can have a value: " + paramNames,
                    ValidationErrorType.EXCLUSIVE_PARAMETER_VIOLATION,
                    condition.getConditionTypeId(),
                    type.getItemId(),
                    context,
                    null));
            }
        }

        return errors;
    }

    private List<ValidationError> validateAdditionalRules(String paramName, Object value, Parameter param,
            Condition condition, ConditionType type, String parentLocation) {
        List<ValidationError> errors = new ArrayList<>();
        ConditionValidation validation = param.getValidation();

        Map<String, Object> context = buildValidationContext(paramName, value, param, parentLocation, null);

        // Check required parameters
        if (validation.isRequired() && value == null) {
            errors.add(new ValidationError(paramName,
                "Required parameter is missing",
                ValidationErrorType.MISSING_REQUIRED_PARAMETER,
                condition.getConditionTypeId(),
                type.getItemId(),
                context,
                null));
            return errors; // Skip other validations if required parameter is missing
        }

        // Check recommended parameters
        if (validation.isRecommended() && value == null) {
            errors.add(new ValidationError(paramName,
                "Parameter is recommended for optimal functionality",
                ValidationErrorType.MISSING_RECOMMENDED_PARAMETER,
                condition.getConditionTypeId(),
                type.getItemId(),
                context,
                null));
        }

        if (value != null) {
            // Check allowed values
            if (validation.getAllowedValues() != null && !validation.getAllowedValues().isEmpty()) {
                if (!validation.getAllowedValues().contains(value.toString())) {
                    Map<String, Object> allowedContext = new HashMap<>(context);
                    allowedContext.put("allowedValues", validation.getAllowedValues());
                    errors.add(new ValidationError(paramName,
                        "Value must be one of: " + String.join(", ", validation.getAllowedValues()),
                        ValidationErrorType.INVALID_VALUE,
                        condition.getConditionTypeId(),
                        type.getItemId(),
                        allowedContext,
                        null));
                }
            }

            // Check condition type parameters
            if (value instanceof Condition) {
                Condition subCondition = (Condition) value;
                ConditionType subConditionType = subCondition.getConditionType();
                String subLocation = parentLocation + ".condition[" + 
                    (subCondition.getConditionTypeId() != null ? subCondition.getConditionTypeId() : "unknown") + "]";

                // Check allowed condition tags
                if (validation.getAllowedConditionTags() != null && !validation.getAllowedConditionTags().isEmpty()) {
                    Set<String> conditionTags = subConditionType.getMetadata().getSystemTags();
                    if (conditionTags == null || conditionTags.isEmpty() ||
                        Collections.disjoint(conditionTags, validation.getAllowedConditionTags())) {
                        Map<String, Object> tagContext = buildValidationContext(paramName, value, param, subLocation,
                            Map.of("allowedTags", validation.getAllowedConditionTags(),
                                  "actualTags", conditionTags != null ? conditionTags : Collections.emptySet()));
                        errors.add(new ValidationError(paramName,
                            "Condition must have one of these tags: " +
                            String.join(", ", validation.getAllowedConditionTags()),
                            ValidationErrorType.INVALID_CONDITION_TYPE,
                            condition.getConditionTypeId(),
                            type.getItemId(),
                            tagContext,
                            null));
                    }
                }

                // Check disallowed condition types
                if (validation.getDisallowedConditionTypes() != null && !validation.getDisallowedConditionTypes().isEmpty()) {
                    if (validation.getDisallowedConditionTypes().contains(subConditionType.getItemId())) {
                        Map<String, Object> typeContext = buildValidationContext(paramName, value, param, subLocation,
                            Collections.singletonMap("disallowedTypes", validation.getDisallowedConditionTypes()));
                        errors.add(new ValidationError(paramName,
                            "Condition type " + subConditionType.getItemId() + " is not allowed",
                            ValidationErrorType.INVALID_CONDITION_TYPE,
                            condition.getConditionTypeId(),
                            type.getItemId(),
                            typeContext,
                            null));
                    }
                }
            }
        }

        return errors;
    }

    private List<ValidationError> validateParameterType(String paramName, Object value, Parameter param,
            Condition condition, ConditionType type, String parentLocation) {
        List<ValidationError> errors = new ArrayList<>();

        Map<String, Object> context = buildValidationContext(paramName, value, param, parentLocation, null);

        // Skip type validation if type is not specified (for backward compatibility)
        if (param.getType() == null) {
            return errors;
        }

        // Handle multivalued parameters
        if (param.isMultivalued()) {
            if (!(value instanceof Collection)) {
                errors.add(new ValidationError(paramName,
                    "Value must be a collection for multivalued parameter",
                    ValidationErrorType.INVALID_VALUE,
                    condition.getConditionTypeId(),
                    type.getItemId(),
                    context,
                    null));
                return errors;
            }
            Collection<?> values = (Collection<?>) value;
            
            // Add validation for empty collections when parameter is required
            if (param.getValidation() != null && param.getValidation().isRequired() && values.isEmpty()) {
                errors.add(new ValidationError(paramName,
                    "Required parameter cannot be an empty collection",
                    ValidationErrorType.MISSING_REQUIRED_PARAMETER,
                    condition.getConditionTypeId(),
                    type.getItemId(),
                    context,
                    null));
                return errors;
            }
            
            int index = 0;
            for (Object item : values) {
                String itemLocation = parentLocation + "[" + index + "]";
                Map<String, Object> itemContext = new HashMap<>(context);
                itemContext.put("collectionIndex", index++);
                errors.addAll(validateSingleParameterValue(paramName, item, param, condition, type, itemContext, itemLocation));
            }
        } else {
            if (value instanceof Collection) {
                errors.add(new ValidationError(paramName,
                    "Value cannot be a collection for non-multivalued parameter",
                    ValidationErrorType.INVALID_VALUE,
                    condition.getConditionTypeId(),
                    type.getItemId(),
                    context,
                    null));
                return errors;
            }
            errors.addAll(validateSingleParameterValue(paramName, value, param, condition, type, context, parentLocation));
        }

        return errors;
    }

    private List<ValidationError> validateSingleParameterValue(String paramName, Object value, Parameter param,
            Condition condition, ConditionType type, Map<String, Object> parentContext, String location) {
        List<ValidationError> errors = new ArrayList<>();

        String paramType = param.getType() != null ? param.getType().toLowerCase() : null;
        Map<String, Object> context = buildValidationContext(paramName, value, param, location, parentContext);

        // Add collection index to error message if present
        String parameterDescription = paramName;
        if (context.containsKey("collectionIndex")) {
            parameterDescription += "[" + context.get("collectionIndex") + "]";
        }

        // Skip type validation if type is not specified (for backward compatibility)
        if (paramType == null) {
            return errors;
        }

        // Special handling for object type with custom validation
        if ("object".equals(paramType)) {
            if (param.getValidation() != null && param.getValidation().getCustomType() != null) {
                Class<?> expectedType = param.getValidation().getCustomType();
                if (!expectedType.isInstance(value)) {
                    context.put("expectedType", expectedType.getName());
                    errors.add(new ValidationError(parameterDescription,
                        "Value must be of type " + expectedType.getSimpleName(),
                        ValidationErrorType.INVALID_VALUE,
                        condition.getConditionTypeId(),
                        type.getItemId(),
                        context,
                        null));
                }
            }
            return errors;
        }

        // Use registered validator if available
        ValueTypeValidator validator = validators.get(paramType);
        if (validator != null) {
            boolean isValid;
            if (validator instanceof ConditionValueTypeValidator) {
                // Pass the validation service for recursive validation
                ConditionValueTypeValidator conditionValidator = (ConditionValueTypeValidator) validator;
                if (!conditionValidator.validate(value)) {
                    // Basic condition structure validation failed
                    context.put("validatorType", validator.getClass().getSimpleName());
                    errors.add(new ValidationError(parameterDescription,
                        validator.getValueTypeDescription(),
                        ValidationErrorType.INVALID_VALUE,
                        condition.getConditionTypeId(),
                        type.getItemId(),
                        context,
                        null));
                } else if (value instanceof Condition) {
                    // Always validate the complete condition tree and report all errors
                    List<ValidationError> nestedErrors = validate((Condition) value);
                    errors.addAll(nestedErrors);
                }
                return errors;
            } else {
                isValid = validator.validate(value);
            }
            
            if (!isValid) {
                context.put("validatorType", validator.getClass().getSimpleName());
                errors.add(new ValidationError(parameterDescription,
                    validator.getValueTypeDescription(),
                    ValidationErrorType.INVALID_VALUE,
                    condition.getConditionTypeId(),
                    type.getItemId(),
                    context,
                    null));
            }
        } else {
            context.put("availableValidators", new ArrayList<>(validators.keySet()));
            errors.add(new ValidationError(parameterDescription,
                "No validator found for type: " + paramType,
                ValidationErrorType.INVALID_VALUE,
                condition.getConditionTypeId(),
                type.getItemId(),
                context,
                null));
        }

        return errors;
    }
}
