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
import java.util.List;
import java.util.concurrent.*;

import static org.apache.unomi.healthcheck.HealthCheckConfig.CONFIG_AUTH_REALM;

/**
 * Health check service that aggregates health checks from multiple providers and ensure asynchronous execution. The service is
 * aware of any configuration changes.
 */
@Component (service = HealthCheckService.class, immediate = true)
public class HealthCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckService.class.getName());

    private final List<HealthCheckProvider> providers = new ArrayList<>();
    private ExecutorService executor;

    @Reference
    protected HttpService httpService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY, updated = "updated")
    private HealthCheckConfig config;

    public HealthCheckService() {
        LOGGER.info("Building healthcheck service...");
    }

    public void setConfig(HealthCheckConfig config) {
        this.config = config;
    }

    @Activate
    public void activate() throws ServletException, NamespaceException {
        LOGGER.info("Activating healthcheck service...");
        executor = Executors.newSingleThreadExecutor();
        httpService.registerServlet("/health/check", new HealthCheckServlet(this), null, new HealthCheckHttpContext(config.get(CONFIG_AUTH_REALM)));
    }

    public void updated() throws ServletException, NamespaceException {
        LOGGER.info("Updating healthcheck service...");
        httpService.unregister("/health/check");
        httpService.registerServlet("/health/check", new HealthCheckServlet(this), null, new HealthCheckHttpContext(config.get(CONFIG_AUTH_REALM)));
    }

    @Deactivate
    public void deactivate() {
        LOGGER.info("Deactivating healthcheck service...");
        httpService.unregister("/health/check");
        executor.shutdown();
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
        LOGGER.debug("Health check called");
        List<HealthCheckResponse> health = new ArrayList<>();
        health.add(HealthCheckResponse.live("karaf"));
        for (HealthCheckProvider provider : providers) {
            Future<HealthCheckResponse> future = executor.submit(provider::execute);
            try {
                HealthCheckResponse response = future.get(500, TimeUnit.MILLISECONDS);
                health.add(response);
            } catch (TimeoutException e) {
                future.cancel(true);
                health.add(provider.timeout());
            } catch (Exception e) {
                LOGGER.error("Error while executing health check", e);
            }
        }
        return health;
    }
}
