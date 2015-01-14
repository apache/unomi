package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by loom on 27.08.14.
 */
@XmlRootElement
public class PropertyType extends Item implements Comparable<PropertyType>, PluginType {
    public static final String ITEM_TYPE = "propertyType";
    Set<String> tagIds = new LinkedHashSet<String>();
    private String id;
    private String valueTypeId;
    private ValueType valueType;
    private String choiceListInitializerFilter;
    private String defaultValue;
    private String selectorId;
    private Set<String> automaticMappingsFrom;
    private double rank;
    private long pluginId;
    private String resourceBundle;
    private String mergeStrategy;

    public PropertyType() {
    }

    public PropertyType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.itemId = id;
        this.id = id;
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

    public String getResourceBundle() {
        return resourceBundle;
    }

    public void setResourceBundle(String resourceBundle) {
        this.resourceBundle = resourceBundle;
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
}
