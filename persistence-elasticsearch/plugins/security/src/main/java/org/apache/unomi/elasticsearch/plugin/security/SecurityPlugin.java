/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.elasticsearch.plugin.security;

import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;

import java.util.Collection;

import static com.google.common.collect.Lists.newArrayList;

public class SecurityPlugin extends Plugin {

    public SecurityPlugin(Settings settings) {
        super();
    }

    public String name() {
        return "contextserver-security";
    }

    public String description() {
        return "A plugin that provides some basic security to the Context Server elasticsearch HTTP and Transport connectors";
    }

    @Override
    public Collection<Module> nodeModules() {
        Collection<Module> modules = newArrayList();
        // if (settings.getAsBoolean("security.enabled", true)) {
        modules.add(new SecurityPluginModule());
        // }
        return modules;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Collection<Class<? extends LifecycleComponent>> nodeServices() {
        Collection<Class<? extends LifecycleComponent>> services = newArrayList();
        // if (settings.getAsBoolean("security.enabled", true)) {
        services.add(SecurityPluginService.class);
        // }
        return services;
    }
}
