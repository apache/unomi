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

import org.apache.unomi.api.actions.ActionType;

import java.util.List;
import java.util.Set;

/**
 * A representation of an {@link ActionType} better suited for definitions.
 */
public class RESTActionType {
    private String id;
    private String name;
    private String description;
    private Set<String> tags;
    private Set<String> systemTags;
    private List<RESTParameter> parameters;
    protected Long version;

    /**
     * Returns the action type identifier.
     *
     * @return the action type identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the action type identifier.
     *
     * @param id the action type identifier
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the action type name.
     *
     * @return the action type name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the action type name.
     *
     * @param name the action type name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the action type description.
     *
     * @return the action type description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the action type description.
     *
     * @param description the action type description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the action type tags.
     *
     * @return the action type tags
     */
    public Set<String> getTags() {
        return tags;
    }

    /**
     * Sets the action type tags.
     *
     * @param tags the action type tags
     */
    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    /**
     * Returns the action type system tags.
     *
     * @return the action type system tags
     */
    public Set<String> getSystemTags() {
        return systemTags;
    }

    /**
     * Sets the action type system tags.
     *
     * @param systemTags the action type system tags
     */
    public void setSystemTags(Set<String> systemTags) {
        this.systemTags = systemTags;
    }

    /**
     * Returns the action type parameters.
     *
     * @return the action type parameters
     */
    public List<RESTParameter> getParameters() {
        return parameters;
    }

    /**
     * Sets the action type parameters.
     *
     * @param parameters the action type parameters
     */
    public void setParameters(List<RESTParameter> parameters) {
        this.parameters = parameters;
    }

    /**
     * Returns the action type version.
     *
     * @return the action type version
     */
    public Long getVersion() {
        return version;
    }

    /**
     * Sets the action type version.
     *
     * @param version the action type version
     */
    public void setVersion(Long version) {
        this.version = version;
    }
}
