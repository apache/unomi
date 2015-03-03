package org.elasticsearch.contextserver;

import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.AbstractPlugin;

import java.util.Collection;

import static org.elasticsearch.common.collect.Lists.newArrayList;

public class SecurityPlugin extends AbstractPlugin {

    private final Settings settings;

    public SecurityPlugin(Settings settings) {
        this.settings = settings;
    }

    public String name() {
        return "contextserver-security";
    }

    public String description() {
        return "A plugin that provides some basic security to the Context Server elasticsearch HTTP and Transport connectors";
    }

    @Override
    public Collection<Class<? extends Module>> modules() {
        Collection<Class<? extends Module>> modules = newArrayList();
        // if (settings.getAsBoolean("security.enabled", true)) {
        modules.add(SecurityPluginModule.class);
        // }
        return modules;
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> services() {
        Collection<Class<? extends LifecycleComponent>> services = newArrayList();
        // if (settings.getAsBoolean("security.enabled", true)) {
        services.add(SecurityPluginService.class);
        // }
        return services;
    }
}
