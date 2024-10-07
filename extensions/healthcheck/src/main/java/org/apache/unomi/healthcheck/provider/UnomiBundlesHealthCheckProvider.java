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

package org.apache.unomi.healthcheck.provider;

import org.apache.unomi.healthcheck.HealthCheckResponse;
import org.apache.unomi.healthcheck.HealthCheckProvider;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A health check that track the Unomi bundles availability.
 */
@Component(service = HealthCheckProvider.class, immediate = true)
public class UnomiBundlesHealthCheckProvider implements HealthCheckProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnomiBundlesHealthCheckProvider.class.getName());
    public static final String NAME = "unomi";

    @Reference(service = BundleWatcher.class, cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, bind = "bind", unbind = "unbind")
    private volatile BundleWatcher service;

    public UnomiBundlesHealthCheckProvider() {
        LOGGER.info("Building unomi bundles health provider service...");
    }

    public void bind(BundleWatcher service) {
        this.service = service;
    }

    public void unbind(BundleWatcher service) {
        this.service = null;
    }

    @Override public String name() {
        return NAME;
    }

    @Override public HealthCheckResponse execute() {
        LOGGER.debug("Health check unomi bundles");
        HealthCheckResponse.Builder builder = new HealthCheckResponse.Builder();
        builder.name(NAME).down();
        try {
            if (service != null) {
                builder.up();
                if (service.isStartupComplete()) {
                    builder.live();
                }
            }
        } catch (Exception e) {
            builder.error().withData("error", e.getMessage());
        }
        return builder.build();
    }
}
