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

    private static final long serialVersionUID = 6019392686888941547L;

    private String id;
    private String type;
    private boolean multivalued;
    private Object defaultValue;
    private ConditionValidation validation;

    public Parameter() {
    }

    public Parameter(String id, String type, boolean multivalued) {
        this.id = id;
        this.type = type;
        this.multivalued = multivalued;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMultivalued() {
        return multivalued;
    }

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

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public ConditionValidation getValidation() {
        return validation;
    }

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
                .put("id", id)
                .put("validation", "<max depth exceeded>")
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
