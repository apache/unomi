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
package org.apache.unomi.services.impl.validation.validators;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.api.services.ValueTypeValidator;

import java.util.Collection;

public class ConditionValueTypeValidator implements ValueTypeValidator {
    @Override
    public String getValueTypeId() {
        return "condition";
    }

    @Override
    public boolean validate(Object value) {
        return validate(value, null);
    }

    public boolean validate(Object value, ConditionValidationService validationService) {
        if (value == null) {
            return true;
        }
        // Handle collections for multivalued parameters
        if (value instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) value;
            // Empty collections are considered valid, parameter required validation is handled separately
            if (collection.isEmpty()) {
                return true;
            }
            // Check each element in the collection
            return collection.stream().allMatch(element -> element == null || validateSingleCondition(element, validationService));
        }
        return validateSingleCondition(value, validationService);
    }

    private boolean validateSingleCondition(Object value, ConditionValidationService validationService) {
        if (!(value instanceof Condition)) {
            return false;
        }
        Condition condition = (Condition) value;

        // Note: This validator performs basic structure validation.
        // Condition type resolution should happen before validation in ConditionValidationServiceImpl.
        // If the type is not resolved here, it will be caught by the main validation.
        // Basic validation: must have type and metadata
        ConditionType type = condition.getConditionType();
        if (type == null || type.getMetadata() == null) {
            return false;
        }

        // For basic structure validation, we only check if it's a valid condition
        // Let the parent validator handle the nested validation
        return true;
    }

    @Override
    public String getValueTypeDescription() {
        return "Value must be a valid condition with a condition type and metadata";
    }
}
