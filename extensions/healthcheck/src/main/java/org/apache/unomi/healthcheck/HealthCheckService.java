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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.unomi.healthcheck.HealthCheckConfig.CONFIG_AUTH_REALM;

/**
 * Health check service that aggregates health checks from multiple providers and ensure asynchronous execution. The service is
 * aware of any configuration changes.
 */
@Component (service = HealthCheckService.class, immediate = true)
public class HealthCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckService.class.getName());

    private final List<HealthCheckProvider> providers = new CopyOnWriteArrayList<>();
    private final AtomicInteger threadCount = new AtomicInteger();
    private final ExecutorService executor = Executors.newCachedThreadPool(
            r -> new Thread(r, "unomi-healthcheck-" + threadCount.incrementAndGet()));
    private final Object cacheLock = new Object();
    private volatile long cacheTimestamp = 0L;
    private volatile List<HealthCheckResponse> healthCache = Collections.emptyList();
    private volatile boolean initialized = false;
    private volatile boolean busy = false;
    private boolean registered = false;
    /** Configured provider names already reported as not registered, so the warning is logged once. */
    private final Set<String> missingProvidersReported = ConcurrentHashMap.newKeySet();

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
    protected void setConfig(HealthCheckConfig config) throws ServletException, NamespaceException {
        this.config = config;
        missingProvidersReported.clear();
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
            if (registered) {
                httpService.unregister("/health/check");
                registered = false;
            }
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
        executor.shutdownNow();
    }

    @Reference(service = HealthCheckProvider.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, unbind = "unbind")
    protected void bind(HealthCheckProvider provider) {
        LOGGER.info("Binding provider {}", provider.name());
        providers.add(provider);
        missingProvidersReported.remove(provider.name());
    }

    protected void unbind(HealthCheckProvider provider) {
        LOGGER.info("Unbinding provider {}", provider.name());
        providers.remove(provider);
    }

    public List<HealthCheckResponse> check() {
        if (config == null || !config.isEnabled()) {
            LOGGER.warn("Healthcheck service is disabled");
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
                if (isCacheExpired()) {
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

    /**
     * Logs once per name a configured provider that no bundle registers, for example a typo or a
     * backend-specific provider whose bundle is not installed (or not started yet). It is skipped.
     */
    private void reportMissingProviders(List<HealthCheckProvider> enabledProviders) {
        for (String name : config.getEnabledProviders()) {
            if (!"karaf".equals(name) && enabledProviders.stream().noneMatch(p -> p.name().equals(name))
                    && missingProvidersReported.add(name)) {
                LOGGER.warn("Healthcheck provider '{}' is configured but no provider with that name is registered; skipping it", name);
            }
        }
    }

    private boolean shouldRefreshCache() {
        return !busy && isCacheExpired();
    }

    private boolean isCacheExpired() {
        return (System.currentTimeMillis() - cacheTimestamp) > 1000;
    }

    private void refreshCache() {
        try {
            List<HealthCheckResponse> health = new ArrayList<>();
            health.add(HealthCheckResponse.live("karaf"));
            List<HealthCheckProvider> enabledProviders = providers.stream()
                    .filter(p -> config.getEnabledProviders().contains(p.name()))
                    .toList();
            reportMissingProviders(enabledProviders);
            List<Future<HealthCheckResponse>> futures = new ArrayList<>();
            for (HealthCheckProvider provider : enabledProviders) {
                futures.add(executor.submit(provider::execute));
            }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.getTimeout());
            for (int i = 0; i < enabledProviders.size(); i++) {
                HealthCheckProvider provider = enabledProviders.get(i);
                Future<HealthCheckResponse> future = futures.get(i);
                try {
                    health.add(future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
                } catch (TimeoutException e) {
                    future.cancel(true);
                    LOGGER.warn("Health check {} timed out after {} ms", provider.name(), config.getTimeout());
                    health.add(provider.timeout());
                } catch (InterruptedException e) {
                    futures.forEach(f -> f.cancel(true));
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Health check refresh interrupted, keeping the previous results");
                    return;
                } catch (Exception e) {
                    LOGGER.error("Error while executing health check {}", provider.name(), e);
                    // Match provider-level errors (and 3.0's error field): status ERROR with
                    // data.error. Do not report these as timeouts.
                    Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                    String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
                    health.add(HealthCheckResponse.named(provider.name()).error().withData("error", message).build());
                }
            }
            health.sort(Comparator.comparing(HealthCheckResponse::getName));
            healthCache = List.copyOf(health);
            cacheTimestamp = System.currentTimeMillis();
        } catch (Exception e) {
            LOGGER.error("Error refreshing health cache", e);
        }
    }
}
