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

import org.apache.unomi.api.services.ValueTypeValidator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ComparisonOperatorValueTypeValidator implements ValueTypeValidator {
    private static final Set<String> VALID_OPERATORS = new HashSet<>(Arrays.asList(
        // Equality operators
        "equals", "notEquals",
        // Comparison operators
        "lessThan", "greaterThan", "lessThanOrEqualTo", "greaterThanOrEqualTo",
        // Range operator
        "between",
        // Existence operators
        "exists", "missing",
        // Content operators
        "contains", "notContains", "startsWith", "endsWith", "matchesRegex",
        // Collection operators
        "in", "notIn", "all", "inContains", "hasSomeOf", "hasNoneOf",
        // Date operators
        "isDay", "isNotDay",
        // Geographic operator
        "distance"
    ));

    @Override
    public String getValueTypeId() {
        return "comparisonOperator";
    }

    @Override
    public boolean validate(Object value) {
        return value == null || (value instanceof String && VALID_OPERATORS.contains(value));
    }

    @Override
    public String getValueTypeDescription() {
        return "Value must be a valid comparison operator: " + String.join(", ", VALID_OPERATORS);
    }
}
