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

package org.apache.unomi.healthcheck;

import org.apache.unomi.healthcheck.servlet.HealthCheckHttpContext;
import org.apache.unomi.healthcheck.servlet.HealthCheckServlet;
import org.osgi.service.component.annotations.*;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.unomi.healthcheck.HealthCheckConfig.CONFIG_AUTH_REALM;

/**
 * Health check service that aggregates health checks from multiple providers and ensure asynchronous execution. The service is
 * aware of any configuration changes.
 */
@Component (service = HealthCheckService.class, immediate = true)
public class HealthCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckService.class.getName());

    private final List<HealthCheckProvider> providers = new ArrayList<>();
    private final Object cacheLock = new Object();
    private volatile long cacheTimestamp = 0L;
    private volatile List<HealthCheckResponse> healthCache = Collections.emptyList();
    private volatile boolean initialized = false;
    private volatile boolean busy = false;
    private boolean registered = false;

    @Reference
    protected HttpService httpService;

    private HealthCheckConfig config;

    public HealthCheckService() {
        LOGGER.info("Building healthcheck service...");
    }

    @Activate
    public void activate() throws ServletException, NamespaceException {
        LOGGER.info("Activating healthcheck service...");
        if (!registered) {
            setConfig(config);
        }
    }

    @Reference(service = HealthCheckConfig.class, policy = ReferencePolicy.DYNAMIC, updated = "setConfig")
    private void setConfig(HealthCheckConfig config) throws ServletException, NamespaceException {
        this.config = config;
        if (httpService == null ) {
            LOGGER.info("Healthcheck config with {} entrie(s) did not update the service as not fully started yet.", config.getSize());
            return;
        }
        if (config.isEnabled()) {
            LOGGER.info("Updating healthcheck service...");
            if (registered) {
                httpService.unregister("/health/check");
                registered = false;
            }
            httpService.registerServlet("/health/check", new HealthCheckServlet(this), null,
                    new HealthCheckHttpContext(config.get(CONFIG_AUTH_REALM)));
            registered = true;
        } else {
            httpService.unregister("/health/check");
            registered = false;
            LOGGER.info("Healthcheck service is disabled");
        }
    }

    private void unsetConfig(HealthCheckConfig config) {
        this.config = null;
    }

    @Deactivate
    public void deactivate() {
        LOGGER.info("Deactivating healthcheck service...");
        if (registered) {
            httpService.unregister("/health/check");
            registered = false;
        }
    }

    @Reference(service = HealthCheckProvider.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, unbind = "unbind")
    protected void bind(HealthCheckProvider provider) {
        LOGGER.info("Binding provider {}", provider.name());
        providers.add(provider);
    }

    protected void unbind(HealthCheckProvider provider) {
        LOGGER.info("Unbinding provider {}", provider.name());
        providers.remove(provider);
    }

    public List<HealthCheckResponse> check() {
        if (config == null || !config.isEnabled()) {
            LOGGER.info("Healthcheck service is disabled");
            return Collections.emptyList();
        }
        if (!initialized) {
            synchronized (cacheLock) {
                if (!initialized) {
                    refreshCache();
                    initialized = true;
                }
            }
        } else if (shouldRefreshCache()) {
            synchronized (cacheLock) {
                if (!busy) {
                    busy = true;
                    try {
                       refreshCache();
                    } finally {
                        busy = false;
                    }
                }
            }
        }
        return healthCache;
    }

    private boolean shouldRefreshCache() {
        return !busy && (System.currentTimeMillis() - cacheTimestamp) > 1000;
    }

    private void refreshCache() {
        try {
            List<HealthCheckResponse> health = new ArrayList<>();
            health.add(HealthCheckResponse.live("karaf"));
            for (HealthCheckProvider provider : providers.stream()
                    .filter(p -> config.getEnabledProviders().contains(p.name()))
                    .toList()) {
                try {
                    HealthCheckResponse response = provider.execute();
                    health.add(response);
                } catch (Exception e) {
                    LOGGER.error("Error while executing health check", e);
                    health.add(provider.timeout());
                }
            }
            healthCache = health;
            cacheTimestamp = System.currentTimeMillis();
        } catch (Exception e) {
            LOGGER.error("Error refreshing health cache", e);
        }
    }
}
