package org.elasticsearch.contextserver;

/*
 * #%L
 * context-server-persistence-elasticsearch-plugins-security
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
