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
 * Definition of allowed values for a {@link PropertyType}.
 * Value types describe validation rules, ranges, and serializers so profile
 * and session properties stay consistent with their schema.
 */
public class ValueType implements PluginType, Serializable {

    private String id;
    private String nameKey;
    private String descriptionKey;
    private long pluginId;
    private Set<String> tags = new LinkedHashSet<>();

    /**
     * Default constructor.
     */
    public ValueType() {
    }

    /**
     * Creates a value type with the given identifier.
     *
     * @param id the value type id
     */
    public ValueType(String id) {
        this.id = id;
    }

    /**
     * Value type identifier.
     *
     * @return the value type id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value type identifier.
     *
     * @param id the value type id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Resource bundle key for localizing the display name.
     * Defaults to {@code type.<id>} when unset.
     *
     * @return the name localization key
     */
    public String getNameKey() {
        if (nameKey == null) {
            nameKey = "type." + id;
        }
        return nameKey;
    }

    /**
     * Sets the name localization key.
     *
     * @param nameKey the resource bundle key
     */
    public void setNameKey(String nameKey) {
        this.nameKey = nameKey;
    }

    /**
     * Resource bundle key for localizing the description.
     * Defaults to {@code type.<id>.description} when unset.
     *
     * @return the description localization key
     */
    public String getDescriptionKey() {
        if (descriptionKey == null) {
            descriptionKey = "type." + id + ".description";
        }
        return descriptionKey;
    }

    /**
     * Sets the description localization key.
     *
     * @param descriptionKey the resource bundle key
     */
    public void setDescriptionKey(String descriptionKey) {
        this.descriptionKey = descriptionKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

    /**
     * Tags associated with this value type.
     *
     * @return the tag names
     */
    public Set<String> getTags() {
        return tags;
    }

    /**
     * Sets the tags for this value type.
     *
     * @param tags the tag names
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
