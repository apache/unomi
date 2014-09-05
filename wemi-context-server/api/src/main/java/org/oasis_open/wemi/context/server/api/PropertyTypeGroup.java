package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlTransient;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by loom on 28.08.14.
 */
public class PropertyTypeGroup extends Item implements Comparable<PropertyTypeGroup>, PluginType {

    private String id;
    private double rank;
    private String resourceBundle;
    private String pluginId;
    private SortedSet<PropertyType> propertyTypes = new TreeSet<PropertyType>();

    public PropertyTypeGroup() {
    }

    public PropertyTypeGroup(String itemId) {
        super(itemId);
        this.id = itemId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getRank() {
        return rank;
    }

    public void setRank(double rank) {
        this.rank = rank;
    }

    @XmlTransient
    public SortedSet<PropertyType> getPropertyTypes() {
        return propertyTypes;
    }

    public void setPropertyTypes(SortedSet<PropertyType> propertyTypes) {
        this.propertyTypes = propertyTypes;
    }

    public String getResourceBundle() {
        return resourceBundle;
    }

    public void setResourceBundle(String resourceBundle) {
        this.resourceBundle = resourceBundle;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public int compareTo(PropertyTypeGroup o) {
        return Double.compare(rank, o.rank);
    }
}
