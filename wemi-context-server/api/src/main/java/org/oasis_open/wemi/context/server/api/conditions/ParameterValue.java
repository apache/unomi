package org.oasis_open.wemi.context.server.api.conditions;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.fasterxml.jackson.databind.annotation.JsonTypeResolver;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by loom on 25.06.14.
 */
@XmlRootElement
@JsonTypeIdResolver(ParameterValueTypeIdResolver.class)
public class ParameterValue {

    private List<Object> parameterValues;

    public ParameterValue() {
    }

    public ParameterValue(String parameterId, List<Object> parameterValues) {
        this.parameterValues = parameterValues;
    }

    @XmlElement(name="value")
    public Object getParameterValue() {
        if (parameterValues != null && parameterValues.size() > 1) {
            return null;
        } else {
            return parameterValues.get(0);
        }
    }

    public void setParameterValue(Object value) {
        if (parameterValues == null) {
            parameterValues = new ArrayList<Object>();
            parameterValues.add(value);
        }
        // we do nothing if parameterValues already exists.
    }

    @XmlElement(name="values")
    public List<Object> getParameterValues() {
        return parameterValues;
    }

    public void setParameterValues(List<Object> parameterValues) {
        this.parameterValues = parameterValues;
    }
}
