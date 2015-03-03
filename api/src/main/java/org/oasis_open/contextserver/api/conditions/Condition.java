package org.oasis_open.contextserver.api.conditions;

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
