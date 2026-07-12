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

package org.apache.unomi.api;

import org.apache.unomi.api.conditions.ConditionValidation;
import org.apache.unomi.api.utils.YamlUtils;
import org.apache.unomi.api.utils.YamlUtils.YamlConvertible;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import static org.apache.unomi.api.utils.YamlUtils.toYamlValue;

/**
 * A representation of a condition parameter, to be used in the segment building UI to either select parameters from a
 * choicelist or to enter a specific value.
 */
public class Parameter implements Serializable, YamlConvertible {

    /**
     * Java serialization version; Unomi does not rely on Java serialization of this type as a cross-version persistence contract.
     */
    private static final long serialVersionUID = 6019392686888941547L;

    private String id;
    private String type;
    private boolean multivalued;
    private Object defaultValue;
    private ConditionValidation validation;

    /**
     * Constructs a default {@link Parameter} instance.
     */
    public Parameter() {
    }

    /**
     * Constructs a {@link Parameter} with specified
     * identification and type details.
     * @param id The unique identifier for this parameter.
     * @param type The data type of the parameter (e.g., "string", "integer").
     * @param multivalued Indicates if the parameter can hold multiple values.
     */
    public Parameter(String id, String type, boolean multivalued) {
        this.id = id;
        this.type = type;
        this.multivalued = multivalued;
    }

    /**
     * Returns the unique identifier assigned to this parameter.
     * @return The ID string of the parameter.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this parameter.
     * @param id The new ID to assign to the parameter.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Retrieves the data type associated with this parameter.
     * @return The string representation of the parameter's type.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the data type for this parameter.
     * @param type The new data type string (e.g., "array", "boolean").
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Checks if this parameter is configured to handle multiple values.
     * @return {@code true} if the parameter is multivalued,
     * {@code false} otherwise.
     */
    public boolean isMultivalued() {
        return multivalued;
    }

    /**
     * Sets whether this parameter can hold multiple values.
     * @param multivalued If {@code true}, the parameter accepts multiple
     * values; otherwise, it accepts only one.
     */
    public void setMultivalued(boolean multivalued) {
        this.multivalued = multivalued;
    }

    /**
     * @param choiceListInitializerFilter a reference to a choicelist
     * @deprecated As of version 1.1.0-incubating
     */
    @Deprecated
    public void setChoiceListInitializerFilter(String choiceListInitializerFilter) {
        // Avoid errors when deploying old definitions
    }

    /**
     * Retrieves the default value configured for this parameter.
     * @return The stored default value, which may be null if none is set.
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value that should be used when evaluating conditions
     * involving this parameter. This value can be of any type.
     * @param defaultValue the object to be set as the default value
     */
    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Gets the condition validation rules associated with this parameter.
     * @return The {@link ConditionValidation} object defining constraints, or
     * null if none is configured.
     */
    public ConditionValidation getValidation() {
        return validation;
    }

    /**
     * Sets the specific condition validation rules for this parameter. This
     * allows controlling how the parameter behaves within a condition context.
     * @param validation the new {@link ConditionValidation} object to apply
     */
    public void setValidation(ConditionValidation validation) {
        this.validation = validation;
    }

    /**
     * Converts this parameter to a Map structure for YAML output.
     * Implements YamlConvertible interface.
     * @param visited set of already visited objects to prevent infinite recursion (may be null)
     * @return a Map representation of this parameter
     */
    @Override
    public Map<String, Object> toYaml(Set<Object> visited, int maxDepth) {
        if (maxDepth <= 0) {
            return YamlUtils.YamlMapBuilder.create()
                .putIfNotNull("id", id)
                .putIfNotNull("type", type)
                .putIf("multivalued", true, multivalued)
                .putIfNotNull("defaultValue", "<max depth exceeded>")
                .build();
        }
        return YamlUtils.YamlMapBuilder.create()
            .putIfNotNull("id", id)
            .putIfNotNull("type", type)
            .putIf("multivalued", true, multivalued)
            .putIfNotNull("defaultValue", defaultValue)
            .putIfNotNull("validation", validation != null ? toYamlValue(validation, visited, maxDepth - 1) : null)
            .build();
    }

    @Override
    public String toString() {
        return YamlUtils.format(toYaml());
    }
}
