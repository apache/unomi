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

    /**
     * Creates an empty REST condition type.
     */
    public RESTConditionType() {
    }

    /**
     * Returns the condition type identifier.
     *
     * @return the condition type identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the condition type identifier.
     *
     * @param id the condition type identifier
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the condition type name.
     *
     * @return the condition type name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the condition type name.
     *
     * @param name the condition type name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the condition type description.
     *
     * @return the condition type description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the condition type description.
     *
     * @param description the condition type description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the condition type tags.
     *
     * @return the condition type tags
     */
    public Set<String> getTags() {
        return tags;
    }

    /**
     * Sets the condition type tags.
     *
     * @param tags the condition type tags
     */
    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    /**
     * Returns the condition type system tags.
     *
     * @return the condition type system tags
     */
    public Set<String> getSystemTags() {
        return systemTags;
    }

    /**
     * Sets the condition type system tags.
     *
     * @param systemTags the condition type system tags
     */
    public void setSystemTags(Set<String> systemTags) {
        this.systemTags = systemTags;
    }

    /**
     * Returns the condition type parameters.
     *
     * @return the condition type parameters
     */
    public List<RESTParameter> getParameters() {
        return parameters;
    }

    /**
     * Sets the condition type parameters.
     *
     * @param parameters the condition type parameters
     */
    public void setParameters(List<RESTParameter> parameters) {
        this.parameters = parameters;
    }

    /**
     * Returns the condition type version.
     *
     * @return the condition type version
     */
    public Long getVersion() {
        return version;
    }

    /**
     * Sets the condition type version.
     *
     * @param version the condition type version
     */
    public void setVersion(Long version) {
        this.version = version;
    }
}
