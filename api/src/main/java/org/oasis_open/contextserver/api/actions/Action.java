package org.oasis_open.contextserver.api.actions;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import java.util.HashMap;
import java.util.Map;

@XmlRootElement
public class Action {
    protected ActionType actionType;
    protected String actionTypeId;

    protected Map<String,Object> parameterValues = new HashMap<String, Object>();

    public Action() {
    }

    public Action(ActionType actionType) {
        setActionType(actionType);
    }

    @XmlTransient
    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
        this.actionTypeId = actionType.getId();
    }

    @XmlElement(name="type")
    public String getActionTypeId() {
        return actionTypeId;
    }

    public void setActionTypeId(String actionTypeId) {
        this.actionTypeId = actionTypeId;
    }

    public Map<String, Object> getParameterValues() {
        return parameterValues;
    }

    public void setParameter(String name, Object value) {
        parameterValues.put(name, value);
    }

    public void setParameterValues(Map<String, Object> parameterValues) {
        this.parameterValues = parameterValues;
    }

}
