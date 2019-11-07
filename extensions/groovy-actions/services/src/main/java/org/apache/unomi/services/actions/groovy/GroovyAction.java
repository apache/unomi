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
package org.apache.unomi.services.actions.groovy;

import org.osgi.framework.BundleContext;

import java.net.URL;

/**
 * This class represents a Groovy action, containing all the contextual variables needed to execute such an action.
 * It is not designed to be used outside of the Groovy Action dispatcher.
 */
public class GroovyAction {

    private String name;
    private String path;
    private URL url;
    private BundleContext bundleContext;

    public GroovyAction(URL url, BundleContext bundleContext) {
        this.url = url;
        this.bundleContext = bundleContext;
        this.path = url.getPath();
        this.name = url.getPath().substring(url.getPath().lastIndexOf('/')+1);
        if (this.name.endsWith(".groovy")) {
            this.name = this.name.substring(0, this.name.length() - ".groovy".length());
        }
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public URL getUrl() {
        return url;
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }
}
