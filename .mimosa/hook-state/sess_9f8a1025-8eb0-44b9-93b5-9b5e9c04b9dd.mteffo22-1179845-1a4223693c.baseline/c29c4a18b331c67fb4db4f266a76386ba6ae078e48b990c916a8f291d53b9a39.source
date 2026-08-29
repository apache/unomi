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
package org.apache.unomi.api.services;

import org.apache.unomi.api.conditions.Condition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service that validates {@link Condition} instances against their {@link org.apache.unomi.api.conditions.ConditionType} definitions.
 * <p>
 * Used during save operations to ensure condition parameters satisfy type constraints. Parameters containing
 * references ({@code parameter::}) or script expressions ({@code script::}) are skipped because their values
 * are resolved at evaluation time.
 *
 * @see DefinitionsService#getConditionValidationService()
 */
public interface ConditionValidationService {

    /**
     * Validates a condition against its type definition.
     * <p>
     * Skips validation for parameters that contain references ({@code parameter::}) or script expressions
     * ({@code script::}). Only literal parameter values are validated.
     *
     * @param condition the condition to validate
     * @return validation errors, or an empty list when valid
     */
    List<ValidationError> validate(Condition condition);

    /**
     * A validation error with detailed context about the failing condition, parameter, and cause.
     */
    class ValidationError {
        private final String parameterName;
        private final String message;
        private final ValidationErrorType type;
        private final String conditionId;
        private final String conditionTypeId;
        private final Map<String, Object> context;
        private final ValidationError parentError;

        /**
         * Creates a validation error for a single parameter.
         *
         * @param parameterName the parameter that caused the error
         * @param message the error description
         * @param type the error type
         */
        public ValidationError(String parameterName, String message, ValidationErrorType type) {
            this(parameterName, message, type, null, null, null, null);
        }

        /**
         * Creates a validation error with full condition context and optional parent error.
         *
         * @param parameterName the parameter that caused the error
         * @param message the error description
         * @param type the error type
         * @param conditionId the identifier of the condition being validated
         * @param conditionTypeId the identifier of the condition type
         * @param context additional context key-value pairs
         * @param parentError the parent error that caused this error, or {@code null}
         */
        public ValidationError(String parameterName, String message, ValidationErrorType type,
                             String conditionId, String conditionTypeId, Map<String, Object> context,
                             ValidationError parentError) {
            this.parameterName = parameterName;
            this.message = message;
            this.type = type;
            this.conditionId = conditionId;
            this.conditionTypeId = conditionTypeId;
            this.context = context != null ? new HashMap<>(context) : new HashMap<>();
            this.parentError = parentError;
        }

        /**
         * Parameter that failed validation.
         *
         * @return parameter name, or {@code null} if not applicable
         */
        public String getParameterName() {
            return parameterName;
        }

        /**
         * Human-readable error description.
         *
         * @return error message
         */
        public String getMessage() {
            return message;
        }

        /**
         * Category of validation failure.
         *
         * @return error type
         */
        public ValidationErrorType getType() {
            return type;
        }

        /**
         * Condition instance that failed validation.
         *
         * @return condition identifier, or {@code null} if not applicable
         */
        public String getConditionId() {
            return conditionId;
        }

        /**
         * Condition type definition that was validated.
         *
         * @return condition type identifier, or {@code null} if not applicable
         */
        public String getConditionTypeId() {
            return conditionTypeId;
        }

        /**
         * @deprecated Use {@link #getConditionTypeId()} instead.
         *
         * @return condition type identifier (same as {@link #getConditionTypeId()})
         */
        @Deprecated
        public String getConditionTypeName() {
            return conditionTypeId;
        }

        /**
         * Defensive copy of supplemental error context.
         *
         * @return context map
         */
        public Map<String, Object> getContext() {
            return new HashMap<>(context);
        }

        /**
         * Underlying error that caused this one, when chained.
         *
         * @return parent error, or {@code null} if none
         */
        public ValidationError getParentError() {
            return parentError;
        }

        /**
         * Builds a message with condition, parameter, context, and parent-error details.
         *
         * @return detailed error message
         */
        public String getDetailedMessage() {
            StringBuilder sb = new StringBuilder();

            // Build location context
            if (conditionTypeId != null) {
                sb.append("In condition type '").append(conditionTypeId).append("'");
                if (conditionId != null) {
                    sb.append(" (ID: ").append(conditionId).append(")");
                }
                if (parameterName != null) {
                    sb.append(", parameter '").append(parameterName).append("'");
                }
                sb.append(": ");
            } else if (parameterName != null) {
                sb.append("In parameter '").append(parameterName).append("': ");
            }

            // Add main error message
            sb.append(message);

            // Add context information if available
            if (!context.isEmpty()) {
                sb.append(" (Context: ");
                boolean first = true;
                for (Map.Entry<String, Object> entry : context.entrySet()) {
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                    first = false;
                }
                sb.append(")");
            }

            // Add parent error if available
            if (parentError != null) {
                sb.append("\nCaused by: ").append(parentError.getDetailedMessage());
            }

            return sb.toString();
        }

        @Override
        public String toString() {
            return getDetailedMessage();
        }
    }

    /**
     * Categories of validation errors returned by {@link #validate(Condition)}.
     */
    enum ValidationErrorType {
        /** A required parameter is absent. */
        MISSING_REQUIRED_PARAMETER("Required parameter is missing"),
        /** The value provided for a parameter is not valid. */
        INVALID_VALUE("Invalid value provided"),
        /** The condition type is invalid or not supported. */
        INVALID_CONDITION_TYPE("Invalid or unsupported condition type"),
        /** Mutually exclusive parameters are both present. */
        EXCLUSIVE_PARAMETER_VIOLATION("Mutually exclusive parameters conflict"),
        /** A recommended parameter is absent. */
        MISSING_RECOMMENDED_PARAMETER("Recommended parameter is missing");

        private final String description;

        /**
         * Associates a human-readable label with this error category.
         *
         * @param description descriptive text for this error type
         */
        ValidationErrorType(String description) {
            this.description = description;
        }

        /**
         * Human-readable label for this error category.
         *
         * @return error type description
         */
        public String getDescription() {
            return description;
        }
    }
}
