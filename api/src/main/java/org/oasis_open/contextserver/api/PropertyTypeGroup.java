package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlTransient;

/**
 * Created by loom on 28.08.14.
 */
public class PropertyTypeGroup extends Item implements Comparable<PropertyTypeGroup>, PluginType {
    public static final String ITEM_TYPE = "propertyTypeGroup";

    private String id;
    private double rank;
    private String resourceBundle;
    private long pluginId;

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
        this.itemId = id;
        this.id = id;
    }

    public double getRank() {
        return rank;
    }

    public void setRank(double rank) {
        this.rank = rank;
    }

    public String getResourceBundle() {
        return resourceBundle;
    }

    public void setResourceBundle(String resourceBundle) {
        this.resourceBundle = resourceBundle;
    }

    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

    public int compareTo(PropertyTypeGroup o) {
        return Double.compare(rank, o.rank);
    }
}
