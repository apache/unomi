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
package org.apache.unomi.groovy.actions;

import groovy.util.ResourceConnector;
import groovy.util.ResourceException;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Dictionary;

/**
 * This implementation of a Groovy ResourceConnector will load resources either from an OSGi bundle or from a source
 * code folder if the corresponding header has been set in the bundle.
 */
public class GroovyBundleResourceConnector implements ResourceConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroovyBundleResourceConnector.class.getName());

    private BundleContext bundleContext;

    public GroovyBundleResourceConnector(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public URLConnection getResourceConnection(String resourcePath) throws ResourceException {
        // This piece of code should be made more generic but basically allows to work on the template from the source
        // code without having to redeploy the bundle.
        URL resourceURL = null;
        Dictionary<String,String> headers = bundleContext.getBundle().getHeaders();
        if (headers.get("Unomi-Source-Folders") != null) {
            File moduleSourceFolder = new File(headers.get("Unomi-Source-Folders"));
            if (moduleSourceFolder.exists()) {
                File resourcesSourceFolder = new File(moduleSourceFolder, "src/main/resources");
                if (resourcesSourceFolder.exists()) {
                    File resourceFile = new File(resourcesSourceFolder, resourcePath);
                    if (resourceFile.exists()) {
                        try {
                            LOGGER.info("Loading file {} from module source !", resourcePath);
                            resourceURL = resourceFile.toURI().toURL();
                        } catch (MalformedURLException e) {
                            LOGGER.warn("Error loading file {} from module source code", resourcePath, e);
                        }
                    }
                }
            }
        }
        if (resourceURL == null) {
            resourceURL = bundleContext.getBundle().getEntry(resourcePath);
            if (resourceURL == null) {
                throw new ResourceException("Could find Groovy resource " + resourcePath);
            }
        }
        try {
            return resourceURL.openConnection();
        } catch (IOException e) {
            throw new ResourceException(e);
        }
    }
}
