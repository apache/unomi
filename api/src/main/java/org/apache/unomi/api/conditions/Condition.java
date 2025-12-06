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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * A set of elements that can be evaluated.
 */
public class Condition implements Serializable {
    private static final long serialVersionUID = 7584522402785053206L;

    ConditionType conditionType;
    String conditionTypeId;
    Map<String, Object> parameterValues = new HashMap<>();

    /**
     * Instantiates a new Condition.
     */
    public Condition() {
    }

    /**
     * Instantiates a new Condition with the specified {@link ConditionType}.
     *
     * @param conditionType the condition type
     */
    public Condition(ConditionType conditionType) {
        setConditionType(conditionType);
    }

    /**
     * Retrieves the associated condition type.
     *
     * @return the condition type
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
     * Retrieves the identifier of the associated condition type.
     *
     * @return the identifier of the associated condition type
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
     * Retrieves a Map of all parameter name - value pairs for this condition.
     *
     * @return a Map of all parameter name - value pairs for this condition. These depend on the condition type being used in the condition.
     *
     */
    public Map<String, Object> getParameterValues() {
        return parameterValues;
    }

    /**
     * Sets the parameter name - value pairs for this profile.
     *
     * @param parameterValues a Map containing the parameter name - value pairs for this profile
     */
    public void setParameterValues(Map<String, Object> parameterValues) {
        this.parameterValues = parameterValues;
    }

    /**
     * Determines whether this condition contains the parameter identified by the specified name.
     *
     * @param name the name identifying the parameter whose existence we want to determine
     * @return {@code true} if this condition contains a parameter with the specified name, {@code false} otherwise
     */
    public boolean containsParameter(String name) {
        return parameterValues.containsKey(name);
    }

    /**
     * Retrieves the parameter identified by the specified name.
     *
     * @param name the name of the parameter to retrieve
     * @return the value of the specified parameter or {@code null} if no such parameter exists
     */
    public Object getParameter(String name) {
        return parameterValues.get(name);
    }

    /**
     * Sets the parameter identified by the specified name to the specified value. If a parameter with that name already exists, replaces its value, otherwise adds the new
     * parameter with the specified name and value.
     *
     * @param name  the name of the parameter to set
     * @param value the value of the parameter
     */
    public void setParameter(String name, Object value) {
        parameterValues.put(name, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Condition condition = (Condition) o;

        if (!conditionTypeId.equals(condition.conditionTypeId)) return false;
        return parameterValues.equals(condition.parameterValues);

    }

    @Override
    public int hashCode() {
        int result = conditionTypeId.hashCode();
        result = 31 * result + parameterValues.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Condition{");
        sb.append("conditionType=").append(conditionType);
        sb.append(", conditionTypeId='").append(conditionTypeId).append('\'');
        sb.append(", parameterValues=").append(parameterValues);
        sb.append('}');
        return sb.toString();
    }
}
