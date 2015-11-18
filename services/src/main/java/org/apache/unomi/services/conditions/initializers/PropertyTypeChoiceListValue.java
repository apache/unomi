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
package org.apache.unomi.services.conditions.initializers;

import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.conditions.initializers.ChoiceListValue;

/**
 * Choice list value for the properties, which also includes the required value type of the property.
 * 
 * @author Sergiy Shyrkov
 */
public class PropertyTypeChoiceListValue extends ChoiceListValue implements PluginType {

    private boolean multivalued;

    private String valueType = "string";

    private long pluginId;

    /**
     * Initializes an instance of this class.
     * 
     * @param id
     *            the ID of the property
     * @param name
     *            the display name
     * @param valueType
     *            the required property value type
     */
    public PropertyTypeChoiceListValue(String id, String name, String valueType) {
        this(id, name, valueType, false);
    }

    /**
     * Initializes an instance of this class.
     * 
     * @param id
     *            the ID of the property
     * @param name
     *            the display name
     * @param valueType
     *            the required property value type
     * @param multivalued
     *            {@code true} if the property supports multiple values; {@code false} - in case it is a single value property
     */
    public PropertyTypeChoiceListValue(String id, String name, String valueType, boolean multivalued) {
        super(id, name);
        this.valueType = valueType;
        this.multivalued = multivalued;
    }

    /**
     * Initializes an instance of this class.
     *
     * @param id
     *            the ID of the property
     * @param name
     *            the display name
     * @param valueType
     *            the required property value type
     * @param multivalued
     *            {@code true} if the property supports multiple values; {@code false} - in case it is a single value property
     * @param pluginId
     *            the PropertyType PluginId to retrieve bundle
     */
    public PropertyTypeChoiceListValue(String id, String name, String valueType, boolean multivalued, long pluginId) {
        super(id, name);
        this.multivalued = multivalued;
        this.valueType = valueType;
        this.pluginId = pluginId;
    }

    /**
     * Returns the required property value type.
     * 
     * @return the required property value type
     */
    public String getValueType() {
        return valueType;
    }

    /**
     * Sets the required property value type.
     *
     * @param valueType
     *            the required value type for this property
     */
    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    /**
     * Indicates if the property supports multiple values.
     *
     * @return {@code true} if the property supports multiple values; {@code false} - in case it is a single value property
     */
    public boolean isMultivalued() {
        return multivalued;
    }

    /**
     * Sets the indicator if the property supports multiple values.
     *
     * @param multivalued
     *            {@code true} if the property supports multiple values; {@code false} - in case it is a single value property
     */
    public void setMultivalued(boolean multivalued) {
        this.multivalued = multivalued;
    }

    @Override
    public long getPluginId() {
        return pluginId;
    }

    @Override
    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }
}
