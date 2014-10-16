package org.oasis_open.wemi.context.server.api;

/**
 * Created by loom on 16.10.14.
 */
public class PropertyMergeStrategyType implements PluginType {

    private String id;
    private String filter;

    private String pluginId;
    private String resourceBundle;

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
}
