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

package org.apache.unomi.rest.models;

import org.apache.unomi.api.Parameter;

/**
 * A representation of a {@link Parameter} better suited for definitions.
 */
public class RESTParameter {
    private String id;
    private String type;
    private boolean multivalued = false;
    private Object defaultValue = null;

    /**
     * Returns the parameter identifier.
     *
     * @return the parameter identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the parameter identifier.
     *
     * @param id the parameter identifier
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the parameter type.
     *
     * @return the parameter type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the parameter type.
     *
     * @param type the parameter type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns whether the parameter accepts multiple values.
     *
     * @return {@code true} when the parameter is multivalued
     */
    public boolean isMultivalued() {
        return multivalued;
    }

    /**
     * Sets whether the parameter accepts multiple values.
     *
     * @param multivalued {@code true} when the parameter is multivalued
     */
    public void setMultivalued(boolean multivalued) {
        this.multivalued = multivalued;
    }

    /**
     * Returns the default parameter value.
     *
     * @return the default value
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default parameter value.
     *
     * @param defaultValue the default value
     */
    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

}
