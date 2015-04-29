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

import org.oasis_open.contextserver.api.query.GenericRange;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

@XmlRootElement
public class PropertyType implements Comparable<PropertyType>, PluginType {
    private String id;
    private String target;
    private String nameKey;
    private String valueTypeId;
    private ValueType valueType;
    private String choiceListInitializerFilter;
    private String defaultValue;
    private Map<String, GenericRange> ranges = new TreeMap<>();
    private String selectorId;
    private Set<String> automaticMappingsFrom;
    private double rank;
    private long pluginId;
    private String mergeStrategy;
    private Set<Tag> tags = new TreeSet<Tag>();
    private Set<String> tagIds = new LinkedHashSet<String>();
    private boolean multivalued;
    private boolean protekted = false;

    public PropertyType() {
    }

    public Map<String, GenericRange> getRanges() {
        return ranges;
    }

    public void setRanges(Map<String, GenericRange> ranges) {
        this.ranges = ranges;
    }

    public PropertyType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XmlTransient
    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getNameKey() {
        if (nameKey == null) {
            nameKey = target + "Property." + id;
        }
        return nameKey;
    }

    public void setNameKey(String nameKey) {
        this.nameKey = nameKey;
    }

    @XmlElement(name = "type")
    public String getValueTypeId() {
        return valueTypeId;
    }

    public void setValueTypeId(String valueTypeId) {
        this.valueTypeId = valueTypeId;
    }

    @XmlTransient
    public ValueType getValueType() {
        return valueType;
    }

    public void setValueType(ValueType valueType) {
        this.valueType = valueType;
    }

    @XmlTransient
    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    @XmlElement(name = "tags")
    public Set<String> getTagIds() {
        return tagIds;
    }

    public void setTagIds(Set<String> tagIds) {
        this.tagIds = tagIds;
    }

    public String getChoiceListInitializerFilter() {
        return choiceListInitializerFilter;
    }

    public void setChoiceListInitializerFilter(String choiceListInitializerFilter) {
        this.choiceListInitializerFilter = choiceListInitializerFilter;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getSelectorId() {
        return selectorId;
    }

    public void setSelectorId(String selectorId) {
        this.selectorId = selectorId;
    }

    public Set<String> getAutomaticMappingsFrom() {
        return automaticMappingsFrom;
    }

    public void setAutomaticMappingsFrom(Set<String> automaticMappingsFrom) {
        this.automaticMappingsFrom = automaticMappingsFrom;
    }

    public double getRank() {
        return rank;
    }

    public void setRank(double rank) {
        this.rank = rank;
    }

    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

    public String getMergeStrategy() {
        return mergeStrategy;
    }

    public void setMergeStrategy(String mergeStrategy) {
        this.mergeStrategy = mergeStrategy;
    }

    public int compareTo(PropertyType o) {
        int rankCompare = Double.compare(rank, o.rank);
        if (rankCompare != 0) {
            return rankCompare;
        }
        int idCompare = id.compareTo(o.id);
        if (idCompare != 0) {
            return idCompare;
        }
        return idCompare;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PropertyType that = (PropertyType) o;

        if (Double.compare(that.rank, rank) != 0) return false;
        if (!id.equals(that.id)) return false;
        if (pluginId != that.pluginId) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = id.hashCode();
        temp = Double.doubleToLongBits(rank);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (pluginId ^ (pluginId >>> 32));
        return result;
    }

    public boolean isMultivalued() {
        return multivalued;
    }

    public void setMultivalued(boolean multvalued) {
        this.multivalued = multvalued;
    }

    public boolean isProtected() {
        return protekted;
    }

    public void setProtected(boolean protekted) {
        this.protekted = protekted;
    }
}
