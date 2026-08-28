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
import org.apache.unomi.api.utils.YamlUtils.YamlMapBuilder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.apache.unomi.api.utils.YamlUtils.circularRef;
import static org.apache.unomi.api.utils.YamlUtils.toYamlValue;

/**
 * Boolean expression evaluated against context (profiles, events, sessions).
 * Conditions combine parameters and nested sub-conditions; segments, rules,
 * and queries all use them to decide when logic applies.
 */
public class Condition implements Serializable, YamlConvertible {
    private static final long serialVersionUID = 7584522402785053206L;

    ConditionType conditionType;
    /**
     * Identifier of the condition type backing this expression.
     * In JSON request/response bodies this id is usually serialized as {@code type}
     * (for example {@code eventTypeCondition}, {@code profilePropertyCondition}, {@code booleanCondition}).
     * @api.example eventTypeCondition
     */
    String conditionTypeId;
    /**
     * Parameter values supplied for this condition instance (names depend on the condition type).
     * Examples: {@code eventTypeCondition} uses {@code eventTypeId};
     * {@code profilePropertyCondition} uses {@code propertyName}/{@code comparisonOperator}/{@code propertyValue*};
     * {@code booleanCondition} uses {@code operator} plus nested {@code subConditions}.
     * @api.example {"eventTypeId":"view"}
     */
    Map<String, Object> parameterValues = new HashMap<>();

    /**
     * Default constructor.
     */
    public Condition() {
    }

    /**
     * Creates a condition with the given condition type.
     *
     * @param conditionType the condition type definition
     */
    public Condition(ConditionType conditionType) {
        setConditionType(conditionType);
    }

    /**
     * Resolved condition type definition.
     *
     * @return the condition type, or {@code null} if only the id is set
     */
    @XmlTransient
    public ConditionType getConditionType() {
        return conditionType;
    }

    /**
     * Sets the condition type.
     *
     * @param conditionType the condition type
     */
    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
        if (conditionType != null) {
            this.conditionTypeId = conditionType.getItemId();
        }
    }

    /**
     * Identifier of the condition type backing this expression.
     *
     * @return the condition type id
     */
    @XmlElement(name="type")
    public String getConditionTypeId() {
        return conditionTypeId;
    }

    /**
     * Sets the identifier of the associated condition type.
     *
     * @param conditionTypeId the identifier of the associated condition type
     */
    public void setConditionTypeId(String conditionTypeId) {
        this.conditionTypeId = conditionTypeId;
    }

    /**
     * Parameter values supplied for this condition instance.
     *
     * @return the parameter map; contents depend on the condition type
     */
    public Map<String, Object> getParameterValues() {
        return parameterValues;
    }

    /**
     * Sets all parameter values for this condition.
     *
     * @param parameterValues the parameter map
     */
    public void setParameterValues(Map<String, Object> parameterValues) {
        this.parameterValues = parameterValues != null ? parameterValues : new HashMap<>();
    }

    /**
     * Determines whether this condition contains the parameter identified by the specified name.
     *
     * @param name the name identifying the parameter whose existence we want to determine
     * @return {@code true} if this condition contains a parameter with the specified name, {@code false} otherwise
     */
    public boolean containsParameter(String name) {
        return parameterValues != null && parameterValues.containsKey(name);
    }

    /**
     * Value of the named condition parameter.
     *
     * @param name the parameter name
     * @return the parameter value, or {@code null} if unset
     */
    public Object getParameter(String name) {
        return parameterValues != null ? parameterValues.get(name) : null;
    }

    /**
     * Sets the parameter identified by the specified name to the specified value. If a parameter with that name already exists, replaces its value, otherwise adds the new
     * parameter with the specified name and value.
     *
     * @param name  the name of the parameter to set
     * @param value the value of the parameter
     */
    public void setParameter(String name, Object value) {
        if (parameterValues == null) {
            parameterValues = new HashMap<>();
        }
        parameterValues.put(name, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Condition condition = (Condition) o;
        return Objects.equals(conditionTypeId, condition.conditionTypeId)
                && Objects.equals(parameterValues, condition.parameterValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conditionTypeId, parameterValues);
    }

    /**
     * Converts this condition to a Map structure for YAML output with depth limiting.
     * Implements YamlConvertible interface with circular reference detection and depth limiting
     * to prevent StackOverflowError from extremely deep nested structures.
     *
     * @param visited set of already visited objects to prevent infinite recursion (may be null)
     * @param maxDepth maximum recursion depth (prevents StackOverflowError from deep nesting)
     * @return a Map representation of this condition
     */
    @Override
    public Map<String, Object> toYaml(Set<Object> visited, int maxDepth) {
        if (maxDepth <= 0) {
            return YamlMapBuilder.create()
                .put("type", conditionTypeId != null ? conditionTypeId : "Condition")
                .put("parameterValues", "<max depth exceeded>")
                .build();
        }
        if (visited != null && visited.contains(this)) {
            return circularRef();
        }
        final Set<Object> visitedSet = visited != null ? visited : YamlUtils.newIdentityVisitedSet();
        visitedSet.add(this);
        try {
            YamlMapBuilder builder = YamlMapBuilder.create()
                .put("type", conditionTypeId != null ? conditionTypeId : "Condition");
            if (parameterValues != null && !parameterValues.isEmpty()) {
                builder.put("parameterValues", toYamlValue(parameterValues, visitedSet, maxDepth - 1));
            }
            return builder.build();
        } finally {
            visitedSet.remove(this);
        }
    }

    /**
     * Creates a deep copy of this condition, including all nested conditions in parameter values.
     * Recursively copies all nested conditions to avoid sharing references.
     *
     * @return a deep copy of this condition
     * @throws IllegalStateException if the condition graph contains a cycle through nested {@link Condition} values
     */
    public Condition deepCopy() {
        return deepCopy(new IdentityHashMap<>());
    }

    private Condition deepCopy(IdentityHashMap<Condition, Boolean> copying) {
        if (copying.put(this, Boolean.TRUE) != null) {
            throw new IllegalStateException("Cyclic Condition graph: cannot deepCopy()");
        }
        try {
            Condition copied = new Condition();
            if (this.conditionType != null) {
                copied.setConditionType(this.conditionType);
            } else if (this.conditionTypeId != null) {
                copied.setConditionTypeId(this.conditionTypeId);
            }

            // Deep copy parameter values
            Map<String, Object> copiedParams = new HashMap<>();
            if (this.parameterValues != null) {
                for (Map.Entry<String, Object> entry : this.parameterValues.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Condition) {
                        copiedParams.put(entry.getKey(), ((Condition) value).deepCopy(copying));
                    } else if (value instanceof Collection) {
                        Collection<?> collection = (Collection<?>) value;
                        Collection<Object> copiedCollection;
                        if (collection instanceof List) {
                            copiedCollection = new ArrayList<>();
                        } else {
                            copiedCollection = new LinkedHashSet<>();
                        }
                        for (Object item : collection) {
                            if (item instanceof Condition) {
                                copiedCollection.add(((Condition) item).deepCopy(copying));
                            } else {
                                copiedCollection.add(item);
                            }
                        }
                        copiedParams.put(entry.getKey(), copiedCollection);
                    } else {
                        copiedParams.put(entry.getKey(), value);
                    }
                }
            }
            copied.setParameterValues(copiedParams);

            return copied;
        } finally {
            copying.remove(this);
        }
    }

    @Override
    public String toString() {
        Map<String, Object> map = toYaml();
        return YamlUtils.format(map);
    }
}
