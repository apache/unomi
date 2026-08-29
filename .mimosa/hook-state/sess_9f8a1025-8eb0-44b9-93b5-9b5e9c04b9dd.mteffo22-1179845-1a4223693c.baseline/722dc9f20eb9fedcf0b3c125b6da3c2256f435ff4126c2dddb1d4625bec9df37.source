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
 * Parameter definition for a {@link org.apache.unomi.api.conditions.ConditionType}.
 * Describes how the segment builder UI should collect a value: free text, choice
 * list, or nested structure. Saved on condition types and validated by
 * {@link org.apache.unomi.api.services.ConditionValidationService} when rules
 * or segments are stored.
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
     * Default constructor.
     */
    public Parameter() {
    }

    /**
     * Creates a parameter with id, type, and multivalued flag.
     *
     * @param id          the parameter identifier
     * @param type        the parameter data type (for example {@code string} or {@code integer})
     * @param multivalued whether multiple values are allowed
     */
    public Parameter(String id, String type, boolean multivalued) {
        this.id = id;
        this.type = type;
        this.multivalued = multivalued;
    }

    /**
     * Parameter identifier.
     *
     * @return the parameter id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the parameter identifier.
     *
     * @param id the parameter id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Parameter data type (for example {@code string}, {@code integer}, or {@code boolean}).
     *
     * @return the type id
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the parameter data type.
     *
     * @param type the type id
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Whether this parameter accepts multiple values.
     *
     * @return {@code true} if multivalued, {@code false} otherwise
     */
    public boolean isMultivalued() {
        return multivalued;
    }

    /**
     * Sets whether this parameter accepts multiple values.
     *
     * @param multivalued {@code true} to allow multiple values
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
     * Default value used when a condition does not supply this parameter.
     *
     * @return the default value, or {@code null} if none is configured
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value for this parameter.
     *
     * @param defaultValue the default value
     */
    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Validation rules applied when this parameter is used in a condition.
     *
     * @return the validation rules, or {@code null} if none are configured
     */
    public ConditionValidation getValidation() {
        return validation;
    }

    /**
     * Sets validation rules for this parameter.
     *
     * @param validation the validation rules
     */
    public void setValidation(ConditionValidation validation) {
        this.validation = validation;
    }

    /**
     * Converts this parameter to a Map structure for YAML output.
     * Implements YamlConvertible interface.
     *
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
