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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckServiceTest {

    private static final int TIMEOUT = 200;

    private final CountDownLatch neverReleased = new CountDownLatch(1);
    private HealthCheckService service;

    @BeforeEach
    void setUp() throws Exception {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabledProviders(List.of("fast", "blocked"));
        config.setTimeout(TIMEOUT);
        service = new HealthCheckService();
        service.setConfig(config);
    }

    @AfterEach
    void tearDown() {
        service.deactivate();
    }

    @Test
    void blockedProviderTimesOutAndOtherProvidersAnswer() {
        service.bind(new TestProvider("fast", () -> HealthCheckResponse.live("fast")));
        service.bind(new TestProvider("blocked", this::waitForever));

        List<HealthCheckResponse> health = assertTimeoutPreemptively(Duration.ofMillis(10L * TIMEOUT), service::check);

        assertEquals(3, health.size());
        assertTrue(response(health, "karaf").isLive());
        assertTrue(response(health, "fast").isLive());
        HealthCheckResponse blocked = response(health, "blocked");
        assertTrue(blocked.isError());
        assertEquals("timeout", blocked.getData().get("error.cause"));
    }

    @Test
    void secondCallWithinOneSecondReturnsTheCachedResults() {
        TestProvider fast = new TestProvider("fast", () -> HealthCheckResponse.live("fast"));
        service.bind(fast);

        List<HealthCheckResponse> first = service.check();
        List<HealthCheckResponse> second = service.check();

        assertEquals(1, fast.calls.get());
        assertEquals(first, second);
    }

    @Test
    void concurrentCallsOnAnExpiredCacheRefreshOnlyOnce() throws Exception {
        TestProvider slow = new TestProvider("fast", () -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return HealthCheckResponse.live("fast");
        });
        service.bind(slow);
        service.check();
        Thread.sleep(1100);

        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                calls.add(callers.submit(() -> {
                    start.await();
                    return service.check();
                }));
            }
            start.countDown();
            for (Future<?> call : calls) {
                call.get(5, TimeUnit.SECONDS);
            }
        } finally {
            callers.shutdownNow();
        }

        assertEquals(2, slow.calls.get());
    }

    private HealthCheckResponse waitForever() {
        try {
            neverReleased.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return HealthCheckResponse.live("blocked");
    }

    private static HealthCheckResponse response(List<HealthCheckResponse> health, String name) {
        return health.stream().filter(r -> r.getName().equals(name)).findFirst().orElseThrow();
    }

    private static class TestProvider implements HealthCheckProvider {

        private final String name;
        private final Supplier<HealthCheckResponse> execution;
        private final AtomicInteger calls = new AtomicInteger();

        TestProvider(String name, Supplier<HealthCheckResponse> execution) {
            this.name = name;
            this.execution = execution;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public HealthCheckResponse execute() {
            calls.incrementAndGet();
            return execution.get();
        }
    }
}
