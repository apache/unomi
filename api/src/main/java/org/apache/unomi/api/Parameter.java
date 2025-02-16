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

package org.apache.unomi.api;

import org.apache.unomi.api.conditions.ConditionValidation;

import java.io.Serializable;

/**
 * A representation of a condition parameter, to be used in the segment building UI to either select parameters from a
 * choicelist or to enter a specific value.
 */
public class Parameter implements Serializable {

    private static final long serialVersionUID = 7446061538573517071L;

    private String id;
    private String type;
    private boolean multivalued;
    private Object defaultValue;
    private ConditionValidation validation;

    public Parameter() {
    }

    public Parameter(String id, String type, boolean multivalued) {
        this.id = id;
        this.type = type;
        this.multivalued = multivalued;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMultivalued() {
        return multivalued;
    }

    public void setMultivalued(boolean multivalued) {
        this.multivalued = multivalued;
    }

    /**
     * @param choiceListInitializerFilter a reference to a choicelist
     * @deprecated As of version 1.1.0-incubating
     */
    @Deprecated
    public void setChoiceListInitializerFilter(String choiceListInitializerFilter) {
        // Avoid errors when deploying old definitions
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public ConditionValidation getValidation() {
        return validation;
    }

    public void setValidation(ConditionValidation validation) {
        this.validation = validation;
    }

    @Override
    public String toString() {
        return "Parameter{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", multivalued=" + multivalued +
                ", defaultValue=" + defaultValue +
                ", validation=" + validation +
                '}';
    }
}
