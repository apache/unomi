package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlTransient;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by loom on 28.08.14.
 */
public class PropertyTypeGroup extends Item implements Comparable<PropertyTypeGroup>, PluginType {
    public static final String ITEM_TYPE = "propertyTypeGroup";

    private String id;
    private double rank;
    private String resourceBundle;
    private String pluginId;

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
