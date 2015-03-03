package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlTransient;

public class PropertyMergeStrategyType implements PluginType {

    private String id;
    private String filter;

    private long pluginId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

}
