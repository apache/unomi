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

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A value type to be used to constrain property values.
 */
public class ValueType implements PluginType, Serializable {

    private String id;
    private String nameKey;
    private String descriptionKey;
    private long pluginId;
    private Set<String> tags = new LinkedHashSet<>();

    /**
     * Instantiates a new Value type.
     */
    public ValueType() {
    }

    /**
     * Instantiates a new Value type with the specified identifier.
     *
     * @param id the identifier
     */
    public ValueType(String id) {
        this.id = id;
    }

    /**
     * Retrieves this ValueType's identifier.
     *
     * @return this ValueType's identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Sets this ValueType's identifier.
     *
     * @param id this ValueType's identifier
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Retrieves the {@link java.util.ResourceBundle} key used to localize this ValueType's name.
     *
     * @return the {@link java.util.ResourceBundle} key used to localize this ValueType's name
     */
    public String getNameKey() {
        if (nameKey == null) {
            nameKey = "type." + id;
        }
        return nameKey;
    }

    /**
     * Sets the name key.
     *
     * @param nameKey the name key
     */
    public void setNameKey(String nameKey) {
        this.nameKey = nameKey;
    }

    /**
     * Retrieves the {@link java.util.ResourceBundle} key used to localize this ValueType's description.
     *
     * @return the {@link java.util.ResourceBundle} key used to localize this ValueType's name
     */
    public String getDescriptionKey() {
        if (descriptionKey == null) {
            descriptionKey = "type." + id + ".description";
        }
        return descriptionKey;
    }

    /**
     * Sets the description key.
     *
     * @param descriptionKey the description key
     */
    public void setDescriptionKey(String descriptionKey) {
        this.descriptionKey = descriptionKey;
    }

    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

    /**
     * Retrieves the tags used by this ValueType.
     *
     * @return the tags used by this ValueType
     */
    public Set<String> getTags() {
        return tags;
    }

    /**
     * Sets the tags used by this ValueType.
     *
     * @param tags the tags used by this ValueType
     */
    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ValueType valueType = (ValueType) o;

        return id.equals(valueType.id);

    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
