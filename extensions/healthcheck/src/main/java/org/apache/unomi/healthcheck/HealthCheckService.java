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

import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author Jerome Blanchard
 */
@Component (service = HealthCheckService.class, immediate = true)
public class HealthCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckService.class.getName());

    private final List<HealthCheckProvider> providers = new ArrayList<>();
    private ExecutorService executor;

    public HealthCheckService() {
        LOGGER.info("Building healthcheck service...");
    }

    @Activate
    public void activate() {
        LOGGER.info("Activating healthcheck service...");
        executor = Executors.newSingleThreadExecutor();
    }

    @Deactivate
    public void deactivate() {
        LOGGER.info("Deactivating healthcheck service...");
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
        LOGGER.info("Health check");
        List<HealthCheckResponse> health = new ArrayList<>();
        health.add(HealthCheckResponse.live("karaf"));
        for (HealthCheckProvider provider : providers) {
            Future<HealthCheckResponse> future = executor.submit(provider::execute);
            try {
                HealthCheckResponse response = future.get(500, TimeUnit.MILLISECONDS);
                health.add(response);
            } catch (TimeoutException e) {
                future.cancel(true);
            } catch (Exception e) {
                // handle other exceptions
            }
        }
        return health;
    }
}
