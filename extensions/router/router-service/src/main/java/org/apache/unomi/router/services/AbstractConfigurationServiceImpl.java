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
package org.apache.unomi.router.services;

import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.IRouterCamelContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by amidani on 26/06/2017.
 */
public abstract class AbstractConfigurationServiceImpl implements SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(AbstractConfigurationServiceImpl.class.getName());

    protected BundleContext bundleContext;
    protected PersistenceService persistenceService;
    protected IRouterCamelContext routerCamelContext;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setRouterCamelContext(IRouterCamelContext routerCamelContext) {
        this.routerCamelContext = routerCamelContext;
    }

    public IRouterCamelContext getRouterCamelContext() {
        return routerCamelContext;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        processBundleStartup(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                processBundleStartup(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
        logger.info("Configuration service initialized.");
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("Configuration service shutdown.");
    }

    @Override
    public void bundleChanged(BundleEvent bundleEvent) {

    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
    }
}
