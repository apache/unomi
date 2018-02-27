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

package org.apache.unomi.api.actions;

import org.apache.unomi.api.rules.Rule;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * An action that can be executed as a consequence of a {@link Rule} being triggered. An action is characterized by its associated {@link
 * ActionType} and parameter values.
 */
public class Action implements Serializable {
    private ActionType actionType;
    private String actionTypeId;
    private Map<String, Object> parameterValues = new HashMap<>();

    /**
     * Instantiates a new Action.
     */
    public Action() {
    }

    /**
     * Instantiates a new Action with the specified {@link ActionType}
     *
     * @param actionType the action's type
     */
    public Action(ActionType actionType) {
        setActionType(actionType);
    }

    /**
     * Retrieves the action's type.
     *
     * @return the action's type
     */
    @XmlTransient
    public ActionType getActionType() {
        return actionType;
    }

    /**
     * Sets the action's type.
     *
     * @param actionType the action's type
     */
    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
        this.actionTypeId = actionType.getItemId();
    }

    /**
     * Retrieves the identifier of the associated action type.
     *
     * @return the identifier of the associated action type
     */
    @XmlElement(name = "type")
    public String getActionTypeId() {
        return actionTypeId;
    }

    /**
     * Sets the identifier of the associated action type.
     *
     * @param actionTypeId the identifier of the associated action type
     */
    public void setActionTypeId(String actionTypeId) {
        this.actionTypeId = actionTypeId;
    }

    /**
     * Retrieves the parameter values as a Map of parameter name - associated value pairs.
     *
     * @return a Map of parameter name - associated value pairs
     */
    public Map<String, Object> getParameterValues() {
        return parameterValues;
    }

    /**
     * Sets the parameter values as a Map of parameter name - associated value pairs.
     *
     * @param parameterValues the parameter values as a Map of parameter name - associated value pairs
     */
    public void setParameterValues(Map<String, Object> parameterValues) {
        this.parameterValues = parameterValues;
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

}
