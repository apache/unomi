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

import org.apache.unomi.api.conditions.ConditionType;

import java.util.*;

/**
 * A representation of a {@link ConditionType} better suited for definitions.
 */
public class RESTConditionType {
    private String id;
    private String name;
    private String description;
    private Set<String> tags = new LinkedHashSet<>();
    private Set<String> systemTags = new LinkedHashSet<>();
    private List<RESTParameter> parameters = new ArrayList<RESTParameter>();
    protected Long version;

    public RESTConditionType() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public Set<String> getSystemTags() {
        return systemTags;
    }

    public void setSystemTags(Set<String> systemTags) {
        this.systemTags = systemTags;
    }

    public List<RESTParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<RESTParameter> parameters) {
        this.parameters = parameters;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
