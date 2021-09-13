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

import org.apache.unomi.api.query.DateRange;
import org.apache.unomi.api.query.IpRange;
import org.apache.unomi.api.query.NumericRange;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

/**
 * A user-defined profile or session property, specifying how possible values are constrained, if the value is multi-valued (a vector of values as opposed to a scalar value).
 */
@XmlRootElement
public class PropertyType extends MetadataItem {
    /**
     * The PropertyType ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "propertyType";

    private String target;
    private String valueTypeId;
    private ValueType valueType;
    private String defaultValue;
    private List<DateRange> dateRanges = new ArrayList<>();
    private List<NumericRange> numericRanges = new ArrayList<>();
    private List<IpRange> ipRanges = new ArrayList<>();
    private Set<String> automaticMappingsFrom = new HashSet<>();
    private Double rank;
    private String mergeStrategy;
    private Boolean multivalued;
    private Boolean protekted;
    private Set<PropertyType> childPropertyTypes = new LinkedHashSet<>();

    /**
     * Instantiates a new Property type.
     */
    public PropertyType() {
    }

    /**
     * Instantiates a new Property type with the specified Metadata.
     *
     * @param metadata the metadata associated with the specified metadata
     */
    public PropertyType(Metadata metadata) {
        super(metadata);
    }

    /**
     * Retrieves the target for this property type, indicating the type of elements this property type is defined for. For example, for property types attached to profiles, {@code
     * target} would be {@code "profiles"}.
     *
     * TODO: deprecated?
     *
     * @return the target for this property type
     */
    public String getTarget() {
        return target;
    }

    /**
     * Sets the target for this property type.
     *
     * TODO: deprecated?
     *
     * @param target the target for this property type, indicating the type of elements this property type is defined for
     */
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * Retrieves the identifier of the value type constraining values for properties using this PropertyType.
     *
     * @return the value type identifier associated with values defined by this PropertyType
     * @see ValueType
     */
    @XmlElement(name = "type")
    public String getValueTypeId() {
        return valueTypeId;
    }

    /**
     * Sets the value type identifier.
     *
     * @param valueTypeId the value type identifier
     */
    public void setValueTypeId(String valueTypeId) {
        this.valueTypeId = valueTypeId;
    }

    /**
     * Retrieves the value type associated with values defined for properties using this PropertyType.
     *
     * @return the value type associated with values defined for properties using this PropertyType
     */
    @XmlTransient
    public ValueType getValueType() {
        return valueType;
    }

    /**
     * Sets the value type.
     *
     * @param valueType the value type associated with values defined for properties using this PropertyType
     */
    public void setValueType(ValueType valueType) {
        this.valueType = valueType;
    }

    /**
     * Retrieves the default value defined for property using this PropertyType.
     *
     * @return the default value defined for property using this PropertyType
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value that properties using this PropertyType will use if no value is specified explicitly.
     *
     * @param defaultValue the default value that properties using this PropertyType will use if no value is specified explicitly
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Retrieves the set of JCR properties from which properties of this type would be automatically initialized from.
     *
     * TODO: remove from API?
     *
     * @return the name of JCR properties properties of this type would be automatically initialized from
     */
    public Set<String> getAutomaticMappingsFrom() {
        return automaticMappingsFrom;
    }

    /**
     * Specifies the set of JCR properties from which properties of this type would be automatically initialized from.
     * TODO: remove from API?
     *
     * @param automaticMappingsFrom the set of JCR properties from which properties of this type would be automatically initialized from
     */
    public void setAutomaticMappingsFrom(Set<String> automaticMappingsFrom) {
        this.automaticMappingsFrom = automaticMappingsFrom;
    }

    /**
     * Retrieves the rank of this PropertyType for ordering purpose.
     *
     * @return the rank of this PropertyType for ordering purpose
     */
    public Double getRank() {
        return rank;
    }

    /**
     * Specifies the rank of this PropertyType for ordering purpose.
     *
     * @param rank the rank of this PropertyType for ordering purpose
     */
    public void setRank(Double rank) {
        this.rank = rank;
    }

    /**
     * Retrieves the identifier of the {@link PropertyMergeStrategyType} to be used in case profiles with properties using this PropertyType are being merged.
     *
     * @return the identifier of the {@link PropertyMergeStrategyType} to be used in case profiles with properties using this PropertyType are being merged
     */
    public String getMergeStrategy() {
        return mergeStrategy;
    }

    /**
     * Sets the identifier of the {@link PropertyMergeStrategyType} to be used in case profiles with properties using this PropertyType are being merged
     *
     * @param mergeStrategy the identifier of the {@link PropertyMergeStrategyType} to be used in case profiles with properties using this PropertyType are being merged
     */
    public void setMergeStrategy(String mergeStrategy) {
        this.mergeStrategy = mergeStrategy;
    }

    /**
     * Retrieves the date ranges.
     *
     * @return the date ranges
     */
    public List<DateRange> getDateRanges() {
        return dateRanges;
    }

    /**
     * Sets the date ranges.
     *
     * @param dateRanges the date ranges
     */
    public void setDateRanges(List<DateRange> dateRanges) {
        this.dateRanges = dateRanges;
    }

    /**
     * Retrieves the numeric ranges.
     *
     * @return the numeric ranges
     */
    public List<NumericRange> getNumericRanges() {
        return numericRanges;
    }

    /**
     * Sets the numeric ranges.
     *
     * @param numericRanges the numeric ranges
     */
    public void setNumericRanges(List<NumericRange> numericRanges) {
        this.numericRanges = numericRanges;
    }

    /**
     * Retrieves the ip ranges.
     *
     * @return the ip ranges
     */
    public List<IpRange> getIpRanges() {
        return ipRanges;
    }

    /**
     * Sets the ip ranges.
     *
     * @param ipRanges the ip ranges
     */
    public void setIpRanges(List<IpRange> ipRanges) {
        this.ipRanges = ipRanges;
    }

    /**
     * Whether properties using this property type are multi-valued.
     *
     * @return {@code true} if properties of this type should be multi-valued, {@code false} otherwise
     */
    public Boolean isMultivalued() {
        return multivalued;
    }

    /**
     * Specifies whether properties using this property type are multi-valued.
     *
     * @param multivalued {@code true} if properties of this type should be multi-valued, {@code false} otherwise
     */
    public void setMultivalued(Boolean multivalued) {
        this.multivalued = multivalued;
    }

    /**
     * Whether properties with this type are marked as protected. Protected properties can be displayed but their value cannot be changed.
     *
     * TODO: rename to readOnly?
     *
     * @return {@code true} if properties of this type are protected, {@code false} otherwise
     */
    public Boolean isProtected() {
        return protekted;
    }

    /**
     * Specifies whether properties with this type are marked as protected.
     *
     * @param protekted {@code true} if properties of this type are protected, {@code false} otherwise
     */
    public void setProtected(boolean protekted) {
        this.protekted = protekted;
    }

    public Set<PropertyType> getChildPropertyTypes() {
        return childPropertyTypes;
    }

    public void setChildPropertyTypes(Set<PropertyType> childPropertyTypes) {
        this.childPropertyTypes = childPropertyTypes;
    }

}
