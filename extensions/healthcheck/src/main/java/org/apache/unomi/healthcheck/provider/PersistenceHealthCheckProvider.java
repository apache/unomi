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

import org.apache.unomi.api.PropertyType;
import org.apache.unomi.healthcheck.HealthCheckResponse;
import org.apache.unomi.healthcheck.HealthCheckProvider;
import org.apache.unomi.healthcheck.HealthCheckConfig;
import org.apache.unomi.healthcheck.util.CachedValue;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * A health check that track the Unomi persistence layer availability. An evolution would be to check the persistence migration status to
 * ensure that running instance is aligned with the underlying persistence migration status and structures.
 */
@Component(service = HealthCheckProvider.class, immediate = true)
public class PersistenceHealthCheckProvider implements HealthCheckProvider {

    public static final String NAME = "persistence";

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceHealthCheckProvider.class.getName());
    private final CachedValue<HealthCheckResponse> cache = new CachedValue<>(5, TimeUnit.MINUTES);

    @Reference(service = PersistenceService.class, cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, bind = "bind", unbind = "unbind")
    private volatile PersistenceService service;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private volatile HealthCheckConfig healthCheckConfig;

    // Lazily created delegate depending on the current persistence implementation
    private volatile HealthCheckProvider delegate;

    public PersistenceHealthCheckProvider() {
        LOGGER.info("Building persistence health provider service...");
    }

    public void bind(PersistenceService service) {
        this.service = service;
        // Reset delegate when persistence changes
        this.delegate = null;
    }

    public void unbind(PersistenceService service) {
        this.service = null;
        this.delegate = null;
    }

    @Override public String name() {
        return NAME;
    }

    @Override public HealthCheckResponse execute() {
        LOGGER.debug("Health check persistence");

        // If we can detect the underlying persistence, delegate to the appropriate provider
        HealthCheckProvider resolved = resolveDelegate();
        if (resolved != null) {
            if (resolved instanceof PersistenceEngineHealthProvider) {
                return ((PersistenceEngineHealthProvider) resolved).detailed();
            }
            return resolved.execute();
        }

        // Fallback to legacy behavior if no delegate is available yet
        if (cache.isStaled() || cache.getValue().isDown() || cache.getValue().isError()) {
            cache.setValue(refresh());
        }
        return cache.getValue();
    }

    private HealthCheckResponse refresh() {
        LOGGER.debug("Refresh value");
        HealthCheckResponse.Builder builder = new HealthCheckResponse.Builder();
        builder.name(NAME).down();
        try {
            if (service != null) {
                builder.up();
                //TODO replace by the expected persistence version when migrations steps will be stored in the persistence service
                if (!service.query("target", "profiles", null, PropertyType.class).isEmpty()) {
                    builder.live();
                }
            }
        } catch (Exception e) {
            builder.error().withData("error", e.getMessage());
            LOGGER.error("Error while checking persistence health", e);
        }
        return builder.build();
    }

    private HealthCheckProvider resolveDelegate() {
        try {
            if (delegate != null) {
                return delegate;
            }
            if (service == null) {
                return null;
            }
            String persistenceName;
            try {
                persistenceName = service.getName();
            } catch (Throwable t) {
                // Older SPI might not expose getName(); fallback to class inspection
                persistenceName = service.getClass().getName().toLowerCase();
            }

            if (persistenceName == null) {
                return null;
            }

            if (persistenceName.contains("opensearch")) {
                OpenSearchHealthCheckProvider provider = new OpenSearchHealthCheckProvider();
                if (healthCheckConfig != null) {
                    provider.setConfig(healthCheckConfig);
                }
                provider.activate();
                delegate = provider;
            } else if (persistenceName.contains("elasticsearch")) {
                ElasticSearchHealthCheckProvider provider = new ElasticSearchHealthCheckProvider();
                if (healthCheckConfig != null) {
                    provider.setConfig(healthCheckConfig);
                }
                provider.activate();
                delegate = provider;
            } else {
                // Unknown persistence implementation, no delegate
                return null;
            }
            return delegate;
        } catch (Exception e) {
            LOGGER.warn("Unable to resolve delegated health check provider", e);
            return null;
        }
    }
}
