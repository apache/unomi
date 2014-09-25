package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.Set;

/**
 * Created by loom on 27.08.14.
 */
@XmlRootElement
public class PropertyType extends Item implements Comparable<PropertyType>, PluginType {
    public static final String ITEM_TYPE = "propertyType";

    private String id;
    private String valueTypeId;
    private ValueType valueType;
    private String groupId;
    private String choiceListInitializerFilter;
    private String defaultValue;
    private String selectorId;
    private Set<String> automaticMappingsFrom;
    private double rank;
    private String pluginId;
    private String resourceBundle;

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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
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

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getResourceBundle() {
        return resourceBundle;
    }

    public void setResourceBundle(String resourceBundle) {
        this.resourceBundle = resourceBundle;
    }

    public int compareTo(PropertyType o) {
        return Double.compare(rank, o.rank);
    }
}
