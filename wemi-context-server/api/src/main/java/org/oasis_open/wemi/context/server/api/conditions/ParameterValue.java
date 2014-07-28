package org.oasis_open.wemi.context.server.api.conditions;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by loom on 25.06.14.
 */
@XmlRootElement
public class ParameterValue {

    private Object value;

    public ParameterValue() {
    }

    public ParameterValue(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

}
