package org.oasis_open.wemi.context.server.api.actions;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by toto on 26/06/14.
 */
@XmlRootElement
public class Action {
    protected ActionType actionType;
    protected String actionTypeId;

    protected Map<String,Object> parameterValues = new HashMap<String, Object>();

    @XmlTransient
    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
        this.actionTypeId = actionType.id;
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

    public void setParameterValues(Map<String, Object> parameterValues) {
        this.parameterValues = parameterValues;
    }

}
