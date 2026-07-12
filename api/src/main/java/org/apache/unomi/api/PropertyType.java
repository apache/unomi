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
import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

/**
 * Schema definition for a custom profile or session property.
 * Declares value type, defaults, allowed ranges, merge strategy, and whether
 * the property accepts multiple values.
 */
public class PropertyType extends MetadataItem {
    /**
     * The PropertyType ITEM_TYPE.
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
     * Default constructor.
     */
    public PropertyType() {
    }

    /**
     * Creates a property type with the given metadata.
     *
     * @param metadata the property type metadata
     */
    public PropertyType(Metadata metadata) {
        super(metadata);
    }

    /**
     * Item type this property applies to (for example {@code profiles}).
     *
     * @return the target item type
     */
    public String getTarget() {
        return target;
    }

    /**
     * Sets the item type this property applies to.
     *
     * @param target the target item type
     */
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * Identifier of the {@link ValueType} that constrains property values.
     *
     * @return the value type id
     * @see ValueType
     */
    @XmlElement(name = "type")
    public String getValueTypeId() {
        return valueTypeId;
    }

    /**
     * Sets the value type identifier.
     *
     * @param valueTypeId the value type id
     */
    public void setValueTypeId(String valueTypeId) {
        this.valueTypeId = valueTypeId;
    }

    /**
     * Resolved value type definition for properties using this schema.
     *
     * @return the value type
     */
    @XmlTransient
    public ValueType getValueType() {
        return valueType;
    }

    /**
     * Sets the resolved value type definition.
     *
     * @param valueType the value type
     */
    public void setValueType(ValueType valueType) {
        this.valueType = valueType;
    }

    /**
     * Default value applied when a property is created without an explicit value.
     *
     * @return the default value
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value for properties of this type.
     *
     * @param defaultValue the default value
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * External property names used to auto-populate values for this type.
     *
     * @return the automatic mapping source names
     */
    public Set<String> getAutomaticMappingsFrom() {
        return automaticMappingsFrom;
    }

    /**
     * Sets the external property names used for automatic mapping.
     *
     * @param automaticMappingsFrom the source property names
     */
    public void setAutomaticMappingsFrom(Set<String> automaticMappingsFrom) {
        this.automaticMappingsFrom = automaticMappingsFrom;
    }

    /**
     * Relative ordering weight when multiple property types are shown together.
     *
     * @return the rank
     */
    public Double getRank() {
        return rank;
    }

    /**
     * Sets the display ordering rank.
     *
     * @param rank the rank
     */
    public void setRank(Double rank) {
        this.rank = rank;
    }

    /**
     * Merge strategy id used when profiles with this property are merged.
     *
     * @return the merge strategy id
     */
    public String getMergeStrategy() {
        return mergeStrategy;
    }

    /**
     * Sets the merge strategy id for profile merges.
     *
     * @param mergeStrategy the merge strategy id
     */
    public void setMergeStrategy(String mergeStrategy) {
        this.mergeStrategy = mergeStrategy;
    }

    /**
     * Allowed date ranges for values of this type.
     *
     * @return the date ranges
     */
    public List<DateRange> getDateRanges() {
        return dateRanges;
    }

    /**
     * Sets the allowed date ranges.
     *
     * @param dateRanges the date ranges
     */
    public void setDateRanges(List<DateRange> dateRanges) {
        this.dateRanges = dateRanges;
    }

    /**
     * Allowed numeric ranges for values of this type.
     *
     * @return the numeric ranges
     */
    public List<NumericRange> getNumericRanges() {
        return numericRanges;
    }

    /**
     * Sets the allowed numeric ranges.
     *
     * @param numericRanges the numeric ranges
     */
    public void setNumericRanges(List<NumericRange> numericRanges) {
        this.numericRanges = numericRanges;
    }

    /**
     * Allowed IP ranges for values of this type.
     *
     * @return the IP ranges
     */
    public List<IpRange> getIpRanges() {
        return ipRanges;
    }

    /**
     * Sets the allowed IP ranges.
     *
     * @param ipRanges the IP ranges
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
     * The { protected} flag name may change to { readOnly} in a future release.     *
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

    /**
     * Nested property types that extend this schema.
     *
     * @return the child property types
     */
    public Set<PropertyType> getChildPropertyTypes() {
        return childPropertyTypes;
    }

    /**
     * Sets the nested child property types.
     *
     * @param childPropertyTypes the child property types
     */
    public void setChildPropertyTypes(Set<PropertyType> childPropertyTypes) {
        this.childPropertyTypes = childPropertyTypes;
    }

}
