package org.oasis_open.contextserver.api;

/*
 * #%L
 * context-server-api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.Set;
import java.util.TreeSet;

/**
 * A simple label used to classify all other objects inside unomi. A tag can define sub-tags.
 */
public class Tag implements PluginType, Comparable<Tag> {

    private Set<Tag> subTags = new TreeSet<>();
    private String id;
    private String nameKey;
    private String descriptionKey;
    private String parentId;
    private double rank = 0.0;
    private long pluginId;
    private boolean hidden = false;

    /**
     * Instantiates a new Tag.
     */
    public Tag() {
    }

    /**
     * Instantiates a new Tag.
     *
     * @param id             the identifier
     * @param nameKey        the {@link java.util.ResourceBundle} key used to localize this Tag's name
     * @param descriptionKey the {@link java.util.ResourceBundle} key used to localize this Tag's description
     * @param parentId       the identifier of this Tag's parent Tag
     */
    public Tag(String id, String nameKey, String descriptionKey, String parentId) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.parentId = parentId;
    }

    /**
     * Retrieves this Tag's identifier.
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Retrieves the {@link java.util.ResourceBundle} key used to localize this Tag's name.
     *
     * @return the {@link java.util.ResourceBundle} key used to localize this Tag's name
     */
    public String getNameKey() {
        if (nameKey == null) {
            nameKey = "tag." + id + ".name";
        }
        return nameKey;
    }

    /**
     * Retrieves the {@link java.util.ResourceBundle} key used to localize this Tag's description.
     *
     * @return the {@link java.util.ResourceBundle} key used to localize this Tag's name
     */
    public String getDescriptionKey() {
        if (descriptionKey == null) {
            descriptionKey = "tag." + id + ".description";
        }
        return descriptionKey;
    }

    /**
     * Retrieves the identifier of this Tag's parent Tag.
     *
     * @return the identifier of this Tag's parent Tag
     */
    @XmlElement(name = "parent")
    public String getParentId() {
        return parentId;
    }

    /**
     * Retrieves the sub tags.
     *
     * @return the sub tags
     */
    public Set<Tag> getSubTags() {
        return subTags;
    }

    /**
     * Sets the sub tags.
     *
     * @param subTags the sub tags
     */
    public void setSubTags(Set<Tag> subTags) {
        this.subTags = subTags;
    }

    /**
     * Retrieves the rank of this PropertyType for ordering purpose.
     *
     * @return the rank of this PropertyType for ordering purpose
     */
    public double getRank() {
        return rank;
    }

    /**
     * Specifies the rank of this PropertyType for ordering purpose.
     *
     * @param rank the rank of this PropertyType for ordering purpose
     */
    public void setRank(double rank) {
        this.rank = rank;
    }

    /**
     * Whether this Tag is considered for internal purposes only and should therefore be hidden to accessing UIs.
     *
     * @return {@code true} if the Tag needs to be hidden, {@code false} otherwise
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * Specifies whether this Tag is hidden.
     *
     * @param hidden {@code true} if the Tag needs to be hidden, {@code false} otherwise
     */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tag that = (Tag) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        return result;
    }

    public int compareTo(Tag otherRank) {
        int rankCompare = Double.compare(rank, otherRank.rank);
        if (rankCompare != 0) {
            return rankCompare;
        }
        return id.compareTo(otherRank.id);
    }
}
