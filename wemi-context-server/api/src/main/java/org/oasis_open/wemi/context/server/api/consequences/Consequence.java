package org.oasis_open.wemi.context.server.api.consequences;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by toto on 26/06/14.
 */
@XmlRootElement
public class Consequence {
    protected ConsequenceType consequenceType;
    protected String consequenceTypeId;

    protected Map<String,Object> parameterValues = new HashMap<String, Object>();

    @XmlTransient
    public ConsequenceType getConsequenceType() {
        return consequenceType;
    }

    public void setConsequenceType(ConsequenceType consequenceType) {
        this.consequenceType = consequenceType;
        this.consequenceTypeId = consequenceType.id;
    }

    @XmlElement(name="type")
    public String getConsequenceTypeId() {
        return consequenceTypeId;
    }

    public void setConsequenceTypeId(String consequenceTypeId) {
        this.consequenceTypeId = consequenceTypeId;
    }

    public Map<String, Object> getParameterValues() {
        return parameterValues;
    }

    public void setParameterValues(Map<String, Object> parameterValues) {
        this.parameterValues = parameterValues;
    }

}
