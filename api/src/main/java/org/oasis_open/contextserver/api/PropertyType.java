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

import org.oasis_open.contextserver.api.query.DateRange;
import org.oasis_open.contextserver.api.query.IpRange;
import org.oasis_open.contextserver.api.query.NumericRange;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

public class PropertyType extends MetadataItem {
    public static final String ITEM_TYPE = "propertyType";

    private String target;
    private String valueTypeId;
    private ValueType valueType;
    private String defaultValue;
    private List<DateRange> dateRanges = new ArrayList<>();
    private List<NumericRange> numericRanges = new ArrayList<>();
    private List<IpRange> ipRanges = new ArrayList<>();
    private Set<String> automaticMappingsFrom;
    private double rank;
    private String mergeStrategy;
    private Set<Tag> tags = new TreeSet<Tag>();
    private Set<String> tagIds = new LinkedHashSet<String>();
    private boolean multivalued;
    private boolean protekted = false;

    public PropertyType() {
    }

    public PropertyType(Metadata metadata) {
        super(metadata);
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
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

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
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

    public String getMergeStrategy() {
        return mergeStrategy;
    }

    public void setMergeStrategy(String mergeStrategy) {
        this.mergeStrategy = mergeStrategy;
    }

    public List<DateRange> getDateRanges() {
        return dateRanges;
    }

    public void setDateRanges(List<DateRange> dateRanges) {
        this.dateRanges = dateRanges;
    }

    public List<NumericRange> getNumericRanges() {
        return numericRanges;
    }

    public void setNumericRanges(List<NumericRange> numericRanges) {
        this.numericRanges = numericRanges;
    }

    public List<IpRange> getIpRanges() {
        return ipRanges;
    }

    public void setIpRanges(List<IpRange> ipRanges) {
        this.ipRanges = ipRanges;
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
