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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class SecurityPlugin extends Plugin {

    public SecurityPlugin(Settings settings) {
        super();
    }

    @Override
    public Collection<Module> createGuiceModules() {
        return Collections.<Module>singletonList(new SecurityPluginModule());
    }

    @Override
    @SuppressWarnings("rawtypes") // Plugin use a rawtype
    public Collection<Class<? extends LifecycleComponent>> getGuiceServiceClasses() {
        Collection<Class<? extends LifecycleComponent>> services = new ArrayList<>();
        services.add(SecurityPluginService.class);
        return services;
    }

    @Override
    public Settings additionalSettings() {
        return Settings.EMPTY;
    }

}
