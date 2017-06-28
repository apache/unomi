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
package org.apache.unomi.router.core.config;

import org.apache.unomi.api.services.ConfigSharingService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by amidani on 27/06/2017.
 */
public class ConfigSharingServiceImpl implements ConfigSharingService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(ConfigSharingServiceImpl.class);

    private String oneshotImportUploadDir;
    private BundleContext bundleContext;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public String getOneshotImportUploadDir() {
        return oneshotImportUploadDir;
    }

    @Override
    public void setOneshotImportUploadDir(String oneshotImportUploadDir) {
        this.oneshotImportUploadDir = oneshotImportUploadDir;
    }

    /** Methods below not used in router bundle implementation of the ConfigSharingService **/

    @Override
    public String getInternalServerPort() {
        return null;
    }

    @Override
    public void setInternalServerPort(String internalServerPort) { }


    public void preDestroy() throws Exception {
        bundleContext.removeBundleListener(this);
        logger.info("Config sharing service for Router is shutdown.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
    }

    @Override
    public void bundleChanged(BundleEvent bundleEvent) {

    }

}
