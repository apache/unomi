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

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import static org.apache.unomi.api.utils.YamlUtils.*;

/**
 * Validation metadata for condition parameters
 */
public class ConditionValidation implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        STRING,
        INTEGER,
        LONG,
        FLOAT,
        DOUBLE,
        BOOLEAN,
        DATE,
        CONDITION,
        OBJECT
    }

    private boolean required;
    private Set<String> allowedValues;
    private Set<String> allowedConditionTags;
    private Set<String> disallowedConditionTypes;
    private boolean exclusive;  // Only one of the exclusive parameters in a group can have a value
    private String exclusiveGroup;  // Name of the exclusive group this parameter belongs to
    private boolean recommended;  // Parameter is recommended but not required
    private Class<?> customType;  // For OBJECT type, specifies the exact class expected

    public ConditionValidation() {
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Set<String> getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(Set<String> allowedValues) {
        this.allowedValues = allowedValues;
    }

    public Set<String> getAllowedConditionTags() {
        return allowedConditionTags;
    }

    public void setAllowedConditionTags(Set<String> allowedConditionTags) {
        this.allowedConditionTags = allowedConditionTags;
    }

    public Set<String> getDisallowedConditionTypes() {
        return disallowedConditionTypes;
    }

    public void setDisallowedConditionTypes(Set<String> disallowedConditionTypes) {
        this.disallowedConditionTypes = disallowedConditionTypes;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public String getExclusiveGroup() {
        return exclusiveGroup;
    }

    public void setExclusiveGroup(String exclusiveGroup) {
        this.exclusiveGroup = exclusiveGroup;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }

    public Class<?> getCustomType() {
        return customType;
    }

    public void setCustomType(Class<?> customType) {
        this.customType = customType;
    }

    /**
     * Converts this validation to a Map structure for YAML output.
     *
     * @return a Map representation of this validation
     */
    public Map<String, Object> toYaml() {
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
