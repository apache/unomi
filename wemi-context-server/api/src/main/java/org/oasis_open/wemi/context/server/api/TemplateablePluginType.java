package org.oasis_open.wemi.context.server.api;

/**
 * Created by loom on 02.09.14.
 */
public interface TemplateablePluginType extends PluginType {

    public String getTemplate();

    public void setTemplate(String template);

}
