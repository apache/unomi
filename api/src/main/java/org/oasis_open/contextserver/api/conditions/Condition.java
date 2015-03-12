package org.oasis_open.contextserver.api.conditions;

/*
 * #%L
 * context-server-api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a node in the segment definition expression tree.
 */
@XmlRootElement
public class Condition {

    ConditionType conditionType;
    String conditionTypeId;
    Map<String, Object> parameterValues = new HashMap<String, Object>();

    public Condition() {
    }

    public Condition(ConditionType conditionType) {
        setConditionType(conditionType);
    }

    @XmlTransient
    public ConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
        this.conditionTypeId = conditionType.getId();
    }

    @XmlElement(name="type")
    public String getConditionTypeId() {
        return conditionTypeId;
    }

    public void setConditionTypeId(String conditionTypeId) {
        this.conditionTypeId = conditionTypeId;
    }

    public Map<String, Object> getParameterValues() {
        return parameterValues;
    }

    public Object getParameter(String name) {
        return parameterValues.get(name);
    }

    public void setParameter(String name, Object value) {
        parameterValues.put(name, value);
    }

    public void setParameterValues(Map<String, Object> parameterValues) {
        this.parameterValues = parameterValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Condition condition = (Condition) o;

        if (!conditionTypeId.equals(condition.conditionTypeId)) return false;
        if (!parameterValues.equals(condition.parameterValues)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = conditionTypeId.hashCode();
        result = 31 * result + parameterValues.hashCode();
        return result;
    }
}
