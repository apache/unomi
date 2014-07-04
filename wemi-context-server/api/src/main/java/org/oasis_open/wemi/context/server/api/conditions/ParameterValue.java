package org.oasis_open.wemi.context.server.api.conditions;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.List;

/**
 * Created by loom on 25.06.14.
 */
@XmlRootElement
public class ParameterValue {

    private String parameterId;
    private List<Object> parameterValues;

    public ParameterValue() {
    }

    public ParameterValue(String parameterId, List<Object> parameterValues) {
        this.parameterId = parameterId;
        this.parameterValues = parameterValues;
    }

    @XmlTransient
    public String getParameterId() {
        return parameterId;
    }

    @XmlElement(name="value")
    public Object getParameterValue() {
        if (parameterValues != null && parameterValues.size() > 1) {
            return null;
        } else {
            return parameterValues.get(0);
        }
    }

    @XmlElement(name="values")
    public List<Object> getParameterValues() {
        return parameterValues;
    }
}
