package org.elasticsearch.contextserver;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;

public class SecurityPluginModule extends AbstractModule {

    private final Settings settings;

    public SecurityPluginModule(Settings settings) {
        this.settings = settings;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    protected void configure() {
        bind(SecurityPluginService.class).asEagerSingleton();
    }
}
