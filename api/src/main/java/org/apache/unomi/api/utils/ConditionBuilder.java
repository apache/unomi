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

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Utility class for creating various types of {@link Condition} objects.
 * This class provides methods to easily construct conditions used for querying data based on specific criteria.
 * <p>
 * The ConditionBuilder supports building complex queries with logical operators (AND, OR, NOT),
 * property comparisons, nested conditions, and special condition types. The fluent API style
 * makes it easier to construct readable and maintainable conditions.
 * <p>
 * Example usage:
 * <pre>
 * ConditionBuilder builder = new ConditionBuilder(definitionsService);
 * Condition condition = builder.and(
 *     builder.profileProperty("age").greaterThan(18),
 *     builder.profileProperty("gender").equalTo("male")
 * ).build();
 * </pre>
 */
public class ConditionBuilder {

    private DefinitionsService definitionsService;

    /**
     * Constructs a new Builder with a specified DefinitionsService.
     *
     * @param definitionsService the DefinitionsService to use for obtaining condition types.
     */
    public ConditionBuilder(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    /**
     * Sets the definitions service to use for resolving condition types.
     *
     * @param definitionsService the definitions service
     */
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    /**
     * Creates an AND condition that combines two sub-conditions, requiring both to be true.
     *
     * @param condition1 the first condition to include in the AND operation
     * @param condition2 the second condition to include in the AND operation
     * @return a compound condition representing the logical AND of the two conditions
     */
    public CompoundCondition and(ConditionItem condition1, ConditionItem condition2) {
        return new CompoundCondition(condition1, condition2, "and");
    }

    /**
     * Creates an AND condition that combines multiple sub-conditions, requiring all to be true.
     *
     * @param conditions the conditions to include in the AND operation
     * @return a compound condition representing the logical AND of all the conditions
     */
    public CompoundCondition and(ConditionItem... conditions) {
        if (conditions == null || conditions.length == 0) {
            throw new IllegalArgumentException("At least one condition must be provided");
        }
        
        List<Condition> subConditions = new ArrayList<>(conditions.length);
        for (ConditionItem condition : conditions) {
            subConditions.add(condition.build());
        }
        
        ConditionItem conditionItem = new ConditionItem("booleanCondition", definitionsService);
        conditionItem.parameter("operator", "and");
        conditionItem.parameter("subConditions", subConditions);
        
        return (CompoundCondition) conditionItem;
    }

    /**
     * Creates a NOT condition that negates the provided sub-condition.
     *
     * @param subCondition the condition to negate
     * @return a NOT condition that evaluates to true when the sub-condition is false
     */
    public NotCondition not(ConditionItem subCondition) {
        return new NotCondition(subCondition);
    }

    /**
     * Creates an OR condition that combines two sub-conditions, requiring at least one to be true.
     *
     * @param condition1 the first condition to include in the OR operation
     * @param condition2 the second condition to include in the OR operation
     * @return a compound condition representing the logical OR of the two conditions
     */
    public CompoundCondition or(ConditionItem condition1, ConditionItem condition2) {
        return new CompoundCondition(condition1, condition2, "or");
    }

    /**
     * Creates an OR condition that combines multiple sub-conditions, requiring at least one to be true.
     *
     * @param conditions the conditions to include in the OR operation
     * @return a compound condition representing the logical OR of all the conditions
     */
    public CompoundCondition or(ConditionItem... conditions) {
        if (conditions == null || conditions.length == 0) {
            throw new IllegalArgumentException("At least one condition must be provided");
        }
        
        List<Condition> subConditions = new ArrayList<>(conditions.length);
        for (ConditionItem condition : conditions) {
            subConditions.add(condition.build());
        }
        
        ConditionItem conditionItem = new ConditionItem("booleanCondition", definitionsService);
        conditionItem.parameter("operator", "or");
        conditionItem.parameter("subConditions", subConditions);
        
        return (CompoundCondition) conditionItem;
    }

    /**
     * Creates a matchAll condition that will match all items regardless of other criteria.
     * This is useful for creating queries that need to return all records.
     *
     * @return a condition that matches all items
     */
    public ConditionItem matchAll() {
        ConditionItem conditionItem = new ConditionItem("matchAllCondition", definitionsService);
        return conditionItem;
    }

    /**
     * Creates a nested condition for querying nested objects or nested fields.
     *
     * @param subCondition the condition to apply on the nested object or field
     * @param path the path to the nested object or field
     * @return a nested condition for the specified path and sub-condition
     */
    public NestedCondition nested(ConditionItem subCondition, String path) {
        return new NestedCondition(subCondition, path);
    }

    /**
     * Creates a condition for comparing a profile property value.
     * This is a convenience method for creating conditions on profile properties.
     *
     * @param propertyName the name of the profile property to use in the condition
     * @return a property condition configured for the specified profile property
     */
    public PropertyCondition profileProperty(String propertyName) {
        return new PropertyCondition("profilePropertyCondition", propertyName, definitionsService);
    }

    /**
     * Creates a condition for comparing a session property value.
     *
     * @param propertyName the name of the session property to use in the condition
     * @return a property condition configured for the specified session property
     */
    public PropertyCondition sessionProperty(String propertyName) {
        return new PropertyCondition("sessionPropertyCondition", propertyName, definitionsService);
    }

    /**
     * Creates a condition for comparing an event property value.
     *
     * @param propertyName the name of the event property to use in the condition
     * @return a property condition configured for the specified event property
     */
    public PropertyCondition eventProperty(String propertyName) {
        return new PropertyCondition("eventPropertyCondition", propertyName, definitionsService);
    }

    /**
     * Creates a condition for comparing any property value based on the specified condition type.
     *
     * @param conditionTypeId the ID of the condition type to use
     * @param propertyName the name of the property to use in the condition
     * @return a property condition for the specified property and condition type
     */
    public PropertyCondition property(String conditionTypeId, String propertyName) {
        return new PropertyCondition(conditionTypeId, propertyName, definitionsService);
    }

    /**
     * Creates a custom condition of the specified type.
     *
     * @param conditionTypeId the ID of the condition type to create
     * @return a new condition item of the specified type
     */
    public ConditionItem condition(String conditionTypeId) {
        return new ConditionItem(conditionTypeId, definitionsService);
    }

    public abstract class ComparisonCondition extends ConditionItem {

        /**
         * Constructs a new comparison condition of the specified type.
         *
         * @param conditionTypeId the ID of the condition type
         * @param definitionsService the definitions service to resolve condition types
         */
        ComparisonCondition(String conditionTypeId, DefinitionsService definitionsService) {
            super(conditionTypeId, definitionsService);
        }

        /**
         * Checks if all values match the compared property.
         *
         * @param values the string values to check
         * @return the condition with the all comparison operator and string values
         */
        public ComparisonCondition all(String... values) {
            return op("all").stringValues(values);
        }

        /**
         * Checks if all date values match the compared property.
         *
         * @param values the date values to check
         * @return the condition with the all comparison operator and date values
         */
        public ComparisonCondition all(Date... values) {
            return op("all").dateValues(values);
        }

        /**
         * Checks if all integer values match the compared property.
         *
         * @param values the integer values to check
         * @return the condition with the all comparison operator and integer values
         */
        public ComparisonCondition all(Integer... values) {
            return op("all").integerValues(values);
        }

        /**
         * Checks if the property contains the specified string value.
         *
         * @param value the string value to check for
         * @return the condition with the contains comparison operator
         */
        public ComparisonCondition contains(String value) {
            return op("contains").stringValue(value);
        }

        /**
         * Checks if the property ends with the specified string value.
         *
         * @param value the string value to check against
         * @return the condition with the endsWith comparison operator
         */
        public ComparisonCondition endsWith(String value) {
            return op("endsWith").stringValue(value);
        }

        /**
         * Checks if the property equals the specified string value.
         *
         * @param value the string value to compare with
         * @return the condition with the equals comparison operator
         */
        public ComparisonCondition equalTo(String value) {
            return op("equals").stringValue(value);
        }

        /**
         * Checks if the property equals the specified date value.
         *
         * @param value the date value to compare with
         * @return the condition with the equals comparison operator
         */
        public ComparisonCondition equalTo(Date value) {
            return op("equals").dateValue(value);
        }

        /**
         * Checks if the property equals the specified integer value.
         *
         * @param value the integer value to compare with
         * @return the condition with the equals comparison operator
         */
        public ComparisonCondition equalTo(Integer value) {
            return op("equals").integerValue(value);
        }

        /**
         * Checks if the property equals the specified double value.
         *
         * @param value the double value to compare with
         * @return the condition with the equals comparison operator
         */
        public ComparisonCondition equalTo(Double value) {
            return op("equals").doubleValue(value);
        }

        /**
         * Checks if the property exists (is not null).
         *
         * @return the condition with the exists comparison operator
         */
        public ComparisonCondition exists() {
            return op("exists");
        }

        /**
         * Checks if the property is greater than the specified date value.
         *
         * @param value the date value to compare with
         * @return the condition with the greaterThan comparison operator
         */
        public ComparisonCondition greaterThan(Date value) {
            return op("greaterThan").dateValue(value);
        }

        /**
         * Checks if the property is greater than the specified integer value.
         *
         * @param value the integer value to compare with
         * @return the condition with the greaterThan comparison operator
         */
        public ComparisonCondition greaterThan(Integer value) {
            return op("greaterThan").integerValue(value);
        }

        /**
         * Checks if the property is greater than the specified double value.
         *
         * @param value the double value to compare with
         * @return the condition with the greaterThan comparison operator
         */
        public ComparisonCondition greaterThan(Double value) {
            return op("greaterThan").doubleValue(value);
        }

        /**
         * Checks if the property is greater than or equal to the specified date value.
         *
         * @param value the date value to compare with
         * @return the condition with the greaterThanOrEqualTo comparison operator
         */
        public ComparisonCondition greaterThanOrEqualTo(Date value) {
            return op("greaterThanOrEqualTo").dateValue(value);
        }

        /**
         * Checks if the property is greater than or equal to the specified integer value.
         *
         * @param value the integer value to compare with
         * @return the condition with the greaterThanOrEqualTo comparison operator
         */
        public ComparisonCondition greaterThanOrEqualTo(Integer value) {
            return op("greaterThanOrEqualTo").integerValue(value);
        }

        /**
         * Checks if the property is greater than or equal to the specified double value.
         *
         * @param value the double value to compare with
         * @return the condition with the greaterThanOrEqualTo comparison operator
         */
        public ComparisonCondition greaterThanOrEqualTo(Double value) {
            return op("greaterThanOrEqualTo").doubleValue(value);
        }

        /**
         * Checks if the property is in the set of specified string values.
         *
         * @param values the string values to check against
         * @return the condition with the in comparison operator
         */
        public ComparisonCondition in(String... values) {
            return op("in").stringValues(values);
        }

        /**
         * Checks if the property is in the date range specified by expressions.
         *
         * @param values the date expression values to check against
         * @return the condition with the in comparison operator
         */
        public ComparisonCondition inDateExpr(String... values) {
            return op("in").dateExprValues(values);
        }

        /**
         * Checks if the property is in the set of specified date values.
         *
         * @param values the date values to check against
         * @return the condition with the in comparison operator
         */
        public ComparisonCondition in(Date... values) {
            return op("in").dateValues(values);
        }

        /**
         * Checks if the property is the same day as the specified date.
         *
         * @param value the date value to compare with
         * @return the condition with the isDay comparison operator
         */
        public ComparisonCondition isDay(Date value) {
            return op("isDay").dateValue(value);
        }

        /**
         * Checks if the property is the same day as the date specified by the expression.
         *
         * @param expression the date expression to compare with
         * @return the condition with the isDay comparison operator
         */
        public ComparisonCondition isDay(String expression) {
            return op("isDay").dateValueExpr(expression);
        }

        /**
         * Checks if the property is not the same day as the specified date.
         *
         * @param value the date value to compare with
         * @return the condition with the isNotDay comparison operator
         */
        public ComparisonCondition isNotDay(Date value) {
            return op("isNotDay").dateValue(value);
        }

        /**
         * Checks if the property is not the same day as the date specified by the expression.
         *
         * @param expression the date expression to compare with
         * @return the condition with the isNotDay comparison operator
         */
        public ComparisonCondition isNotDay(String expression) {
            return op("isNotDay").dateValueExpr(expression);
        }

        /**
         * Checks if the property is in the set of specified integer values.
         *
         * @param values the integer values to check against
         * @return the condition with the in comparison operator
         */
        public ComparisonCondition in(Integer... values) {
            return op("in").integerValues(values);
        }

        /**
         * Checks if the property is in the set of specified double values.
         *
         * @param values the double values to check against
         * @return the condition with the in comparison operator
         */
        public ComparisonCondition in(Double... values) {
            return op("in").doubleValues(values);
        }

        /**
         * Checks if the property is less than the specified date value.
         *
         * @param value the date value to compare with
         * @return the condition with the lessThan comparison operator
         */
        public ComparisonCondition lessThan(Date value) {
            return op("lessThan").dateValue(value);
        }

        /**
         * Checks if the property is less than the specified integer value.
         *
         * @param value the integer value to compare with
         * @return the condition with the lessThan comparison operator
         */
        public ComparisonCondition lessThan(Integer value) {
            return op("lessThan").integerValue(value);
        }

        /**
         * Checks if the property is less than the specified double value.
         *
         * @param value the double value to compare with
         * @return the condition with the lessThan comparison operator
         */
        public ComparisonCondition lessThan(Double value) {
            return op("lessThan").doubleValue(value);
        }

        /**
         * Checks if the property is less than or equal to the specified date value.
         *
         * @param value the date value to compare with
         * @return the condition with the lessThanOrEqualTo comparison operator
         */
        public ComparisonCondition lessThanOrEqualTo(Date value) {
            return op("lessThanOrEqualTo").dateValue(value);
        }

        /**
         * Checks if the property is less than or equal to the specified integer value.
         *
         * @param value the integer value to compare with
         * @return the condition with the lessThanOrEqualTo comparison operator
         */
        public ComparisonCondition lessThanOrEqualTo(Integer value) {
            return op("lessThanOrEqualTo").integerValue(value);
        }

        /**
         * Checks if the property is between the specified date bounds (inclusive).
         *
         * @param lowerBound the lower bound date (inclusive)
         * @param upperBound the upper bound date (inclusive)
         * @return the condition with the between comparison operator
         */
        public ComparisonCondition between(Date lowerBound, Date upperBound) {
            return op("between").dateValues(lowerBound, upperBound);
        }

        /**
         * Checks if the property is between the specified integer bounds (inclusive).
         *
         * @param lowerBound the lower bound integer (inclusive)
         * @param upperBound the upper bound integer (inclusive)
         * @return the condition with the between comparison operator
         */
        public ComparisonCondition between(Integer lowerBound, Integer upperBound) {
            return op("between").integerValues(lowerBound, upperBound);
        }

        /**
         * Checks if the property is between the specified double bounds (inclusive).
         *
         * @param lowerBound the lower bound double (inclusive)
         * @param upperBound the upper bound double (inclusive)
         * @return the condition with the between comparison operator
         */
        public ComparisonCondition between(Double lowerBound, Double upperBound) {
            return op("between").doubleValues(lowerBound, upperBound);
        }

        /**
         * Checks if the property matches the specified regular expression.
         *
         * @param value the regular expression to match against
         * @return the condition with the matchesRegex comparison operator
         */
        public ComparisonCondition matchesRegex(String value) {
            return op("matchesRegex").stringValue(value);
        }

        /**
         * Checks if the property is missing (null).
         *
         * @return the condition with the missing comparison operator
         */
        public ComparisonCondition missing() {
            return op("missing");
        }

        /**
         * Checks if the property is not equal to the specified string value.
         *
         * @param value the string value to compare with
         * @return the condition with the notEquals comparison operator
         */
        public ComparisonCondition notEqualTo(String value) {
            return op("notEquals").stringValue(value);
        }

        /**
         * Checks if the property is not equal to the specified date value.
         *
         * @param value the date value to compare with
         * @return the condition with the notEquals comparison operator
         */
        public ComparisonCondition notEqualTo(Date value) {
            return op("notEquals").dateValue(value);
        }

        /**
         * Checks if the property is not equal to the specified integer value.
         *
         * @param value the integer value to compare with
         * @return the condition with the notEquals comparison operator
         */
        public ComparisonCondition notEqualTo(Integer value) {
            return op("notEquals").integerValue(value);
        }

        /**
         * Checks if the property is not equal to the specified double value.
         *
         * @param value the double value to compare with
         * @return the condition with the notEquals comparison operator
         */
        public ComparisonCondition notEqualTo(Double value) {
            return op("notEquals").doubleValue(value);
        }

        /**
         * Checks if the property is not in the set of specified string values.
         *
         * @param values the string values to check against
         * @return the condition with the notIn comparison operator
         */
        public ComparisonCondition notIn(String... values) {
            return op("notIn").stringValues(values);
        }

        /**
         * Checks if the property is not in the set of specified date values.
         *
         * @param values the date values to check against
         * @return the condition with the notIn comparison operator
         */
        public ComparisonCondition notIn(Date... values) {
            return op("notIn").dateValues(values);
        }

        /**
         * Checks if the property is not in the date range specified by expressions.
         *
         * @param values the date expression values to check against
         * @return the condition with the notIn comparison operator
         */
        public ComparisonCondition notInDateExpr(String... values) {
            return op("notIn").dateExprValues(values);
        }

        /**
         * Checks if the property is not in the set of specified integer values.
         *
         * @param values the integer values to check against
         * @return the condition with the notIn comparison operator
         */
        public ComparisonCondition notIn(Integer... values) {
            return op("notIn").integerValues(values);
        }

        /**
         * Checks if the property is not in the set of specified double values.
         *
         * @param values the double values to check against
         * @return the condition with the notIn comparison operator
         */
        public ComparisonCondition notIn(Double... values) {
            return op("notIn").doubleValues(values);
        }

        /**
         * Sets the comparison operator for this condition.
         *
         * @param op the comparison operator to set
         * @return the condition with the specified operator
         */
        private ComparisonCondition op(String op) {
            return parameter("comparisonOperator", op);
        }

        /**
         * Sets a parameter value for this condition.
         *
         * @param name the parameter name
         * @param value the parameter value
         * @return the condition with the parameter set
         */
        @Override
        public ComparisonCondition parameter(String name, Object value) {
            return (ComparisonCondition) super.parameter(name, value);
        }

        /**
         * Sets a parameter with multiple values for this condition.
         *
         * @param name the parameter name
         * @param values the parameter values
         * @return the condition with the parameter set
         */
        public ComparisonCondition parameter(String name, Object... values) {
            return (ComparisonCondition) super.parameter(name, values);
        }

        /**
         * Checks if the property starts with the specified string value.
         *
         * @param value the string value to check against
         * @return the condition with the startsWith comparison operator
         */
        public ComparisonCondition startsWith(String value) {
            return op("startsWith").stringValue(value);
        }

        /**
         * Sets a string value for the property comparison.
         *
         * @param value the string value to set
         * @return the condition with the string value set
         */
        private ComparisonCondition stringValue(String value) {
            return parameter("propertyValue", value);
        }

        /**
         * Sets an integer value for the property comparison.
         *
         * @param value the integer value to set
         * @return the condition with the integer value set
         */
        private ComparisonCondition integerValue(Integer value) {
            return parameter("propertyValueInteger", value);
        }

        /**
         * Sets a double value for the property comparison.
         *
         * @param value the double value to set
         * @return the condition with the double value set
         */
        private ComparisonCondition doubleValue(Double value) {
            return parameter("propertyValueDouble", value);
        }

        /**
         * Sets a date value for the property comparison.
         *
         * @param value the date value to set
         * @return the condition with the date value set
         */
        private ComparisonCondition dateValue(Date value) {
            return parameter("propertyValueDate", value);
        }

        /**
         * Sets a date expression value for the property comparison.
         *
         * @param value the date expression value to set
         * @return the condition with the date expression value set
         */
        private ComparisonCondition dateValueExpr(String value) {
            return parameter("propertyValueDateExpr", value);
        }

        /**
         * Sets multiple string values for the property comparison.
         *
         * @param values the string values to set
         * @return the condition with the string values set
         */
        private ComparisonCondition stringValues(String... values) {
            return parameter("propertyValues", values != null ? Arrays.asList(values) : null);
        }

        /**
         * Sets multiple integer values for the property comparison.
         *
         * @param values the integer values to set
         * @return the condition with the integer values set
         */
        private ComparisonCondition integerValues(Integer... values) {
            return parameter("propertyValuesInteger", values != null ? Arrays.asList(values) : null);
        }

        /**
         * Sets multiple double values for the property comparison.
         *
         * @param values the double values to set
         * @return the condition with the double values set
         */
        private ComparisonCondition doubleValues(Double... values) {
            return parameter("propertyValuesDouble", values != null ? Arrays.asList(values) : null);
        }

        /**
         * Sets multiple date values for the property comparison.
         *
         * @param values the date values to set
         * @return the condition with the date values set
         */
        private ComparisonCondition dateValues(Date... values) {
            return parameter("propertyValuesDate", values != null ? Arrays.asList(values) : null);
        }

        /**
         * Sets multiple date expression values for the property comparison.
         *
         * @param values the date expression values to set
         * @return the condition with the date expression values set
         */
        private ComparisonCondition dateExprValues(String... values) {
            return parameter("propertyValuesDateExpr", values != null ? Arrays.asList(values) : null);
        }
    }

    /**
     * Represents a compound condition combining multiple sub-conditions with a logical operator.
     */
    public class CompoundCondition extends ConditionItem {

        /**
         * Creates a compound condition with two sub-conditions and the specified logical operator.
         *
         * @param condition1 the first condition
         * @param condition2 the second condition
         * @param operator the logical operator to combine the conditions ("and", "or")
         */
        CompoundCondition(ConditionItem condition1, ConditionItem condition2, String operator) {
            super("booleanCondition", condition1.definitionsService);
            parameter("operator", operator);
            ArrayList<Condition> subConditions = new ArrayList<Condition>(2);
            subConditions.add(condition1.build());
            subConditions.add(condition2.build());
            parameter("subConditions", subConditions);
        }
    }

    /**
     * Represents a nested condition for querying nested objects or fields.
     */
    public class NestedCondition extends ConditionItem {
        /**
         * Creates a nested condition for the specified path with the given sub-condition.
         *
         * @param subCondition the condition to apply on the nested path
         * @param path the path to the nested field
         */
        NestedCondition(ConditionItem subCondition, String path) {
            super("nestedCondition", subCondition.definitionsService);
            parameter("path", path);
            parameter("subCondition", subCondition.build());
        }
    }

    /**
     * Base class for all condition items. Provides methods to build conditions and set parameters.
     */
    public class ConditionItem {

        protected Condition condition;
        protected DefinitionsService definitionsService;

        /**
         * Creates a new condition item of the specified type.
         *
         * @param conditionTypeId the ID of the condition type to create
         * @param definitionsService the definitions service to resolve condition types
         * @throws IllegalArgumentException if the condition type is not found
         */
        ConditionItem(String conditionTypeId, DefinitionsService definitionsService) {
            this.definitionsService = definitionsService;
            ConditionType conditionType = definitionsService.getConditionType(conditionTypeId);
            if (conditionType == null) {
                throw new IllegalArgumentException("ConditionType not found: " + conditionTypeId);
            }
            condition = new Condition(
                    this.definitionsService.getConditionType(conditionTypeId));
        }

        /**
         * Builds and returns the final condition object.
         *
         * @return the built condition
         */
        public Condition build() {
            return condition;
        }

        /**
         * Sets a parameter value for this condition.
         *
         * @param name the parameter name
         * @param value the parameter value
         * @return this condition item for method chaining
         */
        public ConditionItem parameter(String name, Object value) {
            condition.setParameter(name, value);
            return this;
        }

        /**
         * Sets a parameter with multiple values for this condition.
         *
         * @param name the parameter name
         * @param values the parameter values
         * @return this condition item for method chaining
         */
        public ConditionItem parameter(String name, Object... values) {
            condition.setParameter(name, values != null ? Arrays.asList(values) : null);
            return this;
        }

    }

    /**
     * Represents a NOT condition that negates the result of a sub-condition.
     */
    public class NotCondition extends ConditionItem {

        /**
         * Creates a NOT condition with the specified sub-condition.
         *
         * @param subCondition the condition to negate
         */
        NotCondition(ConditionItem subCondition) {
            super("notCondition", subCondition.definitionsService);
            parameter("subCondition", subCondition.build());
        }
    }

    /**
     * Represents a condition that compares a property value.
     */
    public class PropertyCondition extends ComparisonCondition {

        /**
         * Creates a property condition of the specified type for the given property name.
         *
         * @param conditionTypeId the ID of the condition type
         * @param propertyName the name of the property to compare
         * @param definitionsService the definitions service to resolve condition types
         */
        PropertyCondition(String conditionTypeId, String propertyName, DefinitionsService definitionsService) {
            super(conditionTypeId, definitionsService);
            condition.setParameter("propertyName", propertyName);
        }

    }

}
