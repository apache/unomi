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

import java.util.Set;

/**
 * An event type definition, used to define the structure of accepted events
 */
public class EventType implements PluginType {

    private String type;

    private Set<PropertyType> propertyTypes;

    private long pluginId;

    public EventType() {
    }

    public EventType(String type, Set<PropertyType> propertyTypes, long pluginId) {
        this.type = type;
        this.propertyTypes = propertyTypes;
        this.pluginId = pluginId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Set<PropertyType> getPropertyTypes() {
        return propertyTypes;
    }

    public void setPropertyTypes(Set<PropertyType> propertyTypes) {
        this.propertyTypes = propertyTypes;
    }

    @Override
    public long getPluginId() {
        return pluginId;
    }

    @Override
    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

    public void merge(final EventType source) {
        if (source == null || source.getPropertyTypes() == null || source.getPropertyTypes().isEmpty()) {
            return;
        }
        this.propertyTypes.addAll(source.getPropertyTypes());
    }
}
