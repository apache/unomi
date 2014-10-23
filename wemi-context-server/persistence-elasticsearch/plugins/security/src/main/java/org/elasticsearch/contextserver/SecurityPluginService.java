package org.elasticsearch.contextserver;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;

/**
 * Created by loom on 23.10.14.
 */
public class SecurityPluginService extends AbstractLifecycleComponent<SecurityPluginService> {

    @Inject
    public SecurityPluginService(Settings settings, SettingsFilter settingsFilter) {
        super(settings);
    }

    @Override
    protected void doStart() throws ElasticsearchException {

    }

    @Override
    protected void doStop() throws ElasticsearchException {

    }

    @Override
    protected void doClose() throws ElasticsearchException {

    }
}
