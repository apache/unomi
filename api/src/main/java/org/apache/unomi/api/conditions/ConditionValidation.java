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
package org.apache.unomi.api.conditions;

import org.apache.unomi.api.utils.YamlUtils;
import org.apache.unomi.api.utils.YamlUtils.YamlConvertible;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import static org.apache.unomi.api.utils.YamlUtils.setToSortedList;

/**
 * Validation metadata for condition parameters
 */
public class ConditionValidation implements Serializable, YamlConvertible {
    private static final long serialVersionUID = 1L;

    /** Defines the expected value types for condition parameters. */
    public enum Type {
        /** String value type. */
        STRING,
        /** Integer value type. */
        INTEGER,
        /** Long value type. */
        LONG,
        /** Float value type. */
        FLOAT,
        /** Double value type. */
        DOUBLE,
        /** Boolean value type. */
        BOOLEAN,
        /** Date value type. */
        DATE,
        /** Condition value type. */
        CONDITION,
        /** Object value type. */
        OBJECT
    }

    private boolean required;
    private Set<String> allowedValues;
    private Set<String> allowedConditionTags;
    private Set<String> disallowedConditionTypes;
    private boolean exclusive;  // Only one of the exclusive parameters in a group can have a value
    private String exclusiveGroup;  // Name of the exclusive group this parameter belongs to
    private boolean recommended;  // Parameter is recommended but not required
    private transient Class<?> customType;

    /**
     * Instantiates a new ConditionValidation.
     */
    public ConditionValidation() {
    }

    /**
     * Returns whether this parameter is required.
     *
     * @return true if the parameter is required
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Sets whether this parameter is required.
     *
     * @param required true if required
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * Returns the set of allowed string values.
     *
     * @return the set of allowed string values
     */
    public Set<String> getAllowedValues() {
        return allowedValues;
    }

    /**
     * Sets the allowed string values.
     *
     * @param allowedValues the allowed values
     */
    public void setAllowedValues(Set<String> allowedValues) {
        this.allowedValues = allowedValues;
    }

    /**
     * Returns the allowed condition tags.
     *
     * @return the allowed condition tags
     */
    public Set<String> getAllowedConditionTags() {
        return allowedConditionTags;
    }

    /**
     * Sets the allowed condition tags.
     *
     * @param allowedConditionTags the allowed tags
     */
    public void setAllowedConditionTags(Set<String> allowedConditionTags) {
        this.allowedConditionTags = allowedConditionTags;
    }

    /**
     * Returns the disallowed condition type identifiers.
     *
     * @return the disallowed condition type identifiers
     */
    public Set<String> getDisallowedConditionTypes() {
        return disallowedConditionTypes;
    }

    /**
     * Sets the disallowed condition type identifiers.
     *
     * @param disallowedConditionTypes the disallowed types
     */
    public void setDisallowedConditionTypes(Set<String> disallowedConditionTypes) {
        this.disallowedConditionTypes = disallowedConditionTypes;
    }

    /**
     * Returns whether this parameter is mutually exclusive with others in the same group.
     *
     * @return true if the parameter is mutually exclusive with others in the same group
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Sets whether this parameter is mutually exclusive.
     *
     * @param exclusive true if exclusive
     */
    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    /**
     * Returns the exclusivity group name.
     *
     * @return the exclusivity group name
     */
    public String getExclusiveGroup() {
        return exclusiveGroup;
    }

    /**
     * Sets the exclusivity group name.
     *
     * @param exclusiveGroup the group name
     */
    public void setExclusiveGroup(String exclusiveGroup) {
        this.exclusiveGroup = exclusiveGroup;
    }

    /**
     * Returns whether this parameter is recommended.
     *
     * @return true if the parameter is recommended
     */
    public boolean isRecommended() {
        return recommended;
    }

    /**
     * Sets whether this parameter is recommended.
     *
     * @param recommended true if recommended
     */
    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }

    /**
     * Returns the custom Java type for this parameter.
     *
     * @return the custom Java type for this parameter
     */
    public Class<?> getCustomType() {
        return customType;
    }

    /**
     * Sets the custom Java type for this parameter.
     *
     * @param customType the custom type class
     */
    public void setCustomType(Class<?> customType) {
        this.customType = customType;
    }

    /**
     * Converts this validation to a Map structure for YAML output.
     * Implements YamlConvertible interface.
     *
     * @param visited set of already visited objects to prevent infinite recursion (may be null)
     * @return a Map representation of this validation
     */
    @Override
    public Map<String, Object> toYaml(Set<Object> visited, int maxDepth) {
        return YamlUtils.YamlMapBuilder.create()
            .putIf("required", true, required)
            .putIf("recommended", true, recommended)
            .putIfNotNull("allowedValues", setToSortedList(allowedValues))
            .putIfNotNull("allowedConditionTags", setToSortedList(allowedConditionTags))
            .putIfNotNull("disallowedConditionTypes", setToSortedList(disallowedConditionTypes))
            .putIf("exclusive", true, exclusive)
            .putIfNotNull("exclusiveGroup", exclusiveGroup)
            .putIfNotNull("customType", customType != null ? customType.getName() : null)
            .build();
    }

    @Override
    public String toString() {
        return YamlUtils.format(toYaml());
    }
}
