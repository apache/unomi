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
 * A service to validate conditions against their type definitions
 */
public interface ConditionValidationService {

    /**
     * Validates a condition against its type definition
     * @param condition the condition to validate
     * @return a list of validation errors, empty if the condition is valid
     */
    List<ValidationError> validate(Condition condition);

    /**
     * Represents a validation error with detailed context
     */
    class ValidationError {
        private final String parameterName;
        private final String message;
        private final ValidationErrorType type;
        private final String conditionId;
        private final String conditionTypeName;
        private final Map<String, Object> context;
        private final ValidationError parentError;

        public ValidationError(String parameterName, String message, ValidationErrorType type) {
            this(parameterName, message, type, null, null, null, null);
        }

        public ValidationError(String parameterName, String message, ValidationErrorType type,
                             String conditionId, String conditionTypeName, Map<String, Object> context,
                             ValidationError parentError) {
            this.parameterName = parameterName;
            this.message = message;
            this.type = type;
            this.conditionId = conditionId;
            this.conditionTypeName = conditionTypeName;
            this.context = context != null ? new HashMap<>(context) : new HashMap<>();
            this.parentError = parentError;
        }

        public String getParameterName() {
            return parameterName;
        }

        public String getMessage() {
            return message;
        }

        public ValidationErrorType getType() {
            return type;
        }

        public String getConditionId() {
            return conditionId;
        }

        public String getConditionTypeName() {
            return conditionTypeName;
        }

        public Map<String, Object> getContext() {
            return new HashMap<>(context);
        }

        public ValidationError getParentError() {
            return parentError;
        }

        /**
         * Returns a detailed error message including all context information
         * @return A detailed error message
         */
        public String getDetailedMessage() {
            StringBuilder sb = new StringBuilder();

            // Build location context
            if (conditionTypeName != null) {
                sb.append("In condition type '").append(conditionTypeName).append("'");
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
     * Types of validation errors
     */
    enum ValidationErrorType {
        MISSING_REQUIRED_PARAMETER("Required parameter is missing"),
        INVALID_VALUE("Invalid value provided"),
        INVALID_CONDITION_TYPE("Invalid or unsupported condition type"),
        EXCLUSIVE_PARAMETER_VIOLATION("Mutually exclusive parameters conflict"),
        MISSING_RECOMMENDED_PARAMETER("Recommended parameter is missing");

        private final String description;

        ValidationErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
