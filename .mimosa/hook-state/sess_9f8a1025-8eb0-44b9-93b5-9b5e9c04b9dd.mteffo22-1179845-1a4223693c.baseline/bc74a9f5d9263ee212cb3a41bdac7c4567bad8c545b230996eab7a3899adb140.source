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

package org.apache.unomi.services.impl.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TaskMetricsManager}.
 * Invoked by Surefire: {@code -Dtest=TaskMetricsManagerTest}. No data-file I/O.
 * Glob: no prior TaskMetricsManagerTest. User asked for Task*Managers unit tests.
 */
public class TaskMetricsManagerTest {

    private TaskMetricsManager metricsManager;

    @BeforeEach
    public void setUp() {
        metricsManager = new TaskMetricsManager();
    }

    @Test
    public void testMissingMetricReturnsZero() {
        assertEquals(0, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED));
    }

    @Test
    public void testIncrementAndAdd() {
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_EXECUTION_TIME, 42);
        assertEquals(2, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED));
        assertEquals(42, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_EXECUTION_TIME));
    }

    @Test
    public void testGetAllMetricsAndReset() {
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_FAILED);
        Map<String, Long> all = metricsManager.getAllMetrics();
        assertEquals(1L, all.get(TaskMetricsManager.METRIC_TASKS_FAILED));
        metricsManager.resetMetrics();
        assertEquals(0, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_FAILED));
        assertTrue(metricsManager.getAllMetrics().isEmpty());
    }

    @Test
    public void testConcurrentIncrements() throws Exception {
        int threads = 8;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(threads * perThread,
            metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED));
    }
}
