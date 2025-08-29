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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages task execution metrics and statistics.
 * Provides thread-safe tracking of various task-related metrics.
 */
public class TaskMetricsManager {
    // Metric constants
    public static final String METRIC_TASKS_COMPLETED = "tasks.completed";
    public static final String METRIC_TASKS_FAILED = "tasks.failed";
    public static final String METRIC_TASKS_CRASHED = "tasks.crashed";
    public static final String METRIC_TASKS_CREATED = "tasks.created";
    public static final String METRIC_TASKS_CANCELLED = "tasks.cancelled";
    public static final String METRIC_TASKS_RESUMED = "tasks.resumed";
    public static final String METRIC_TASKS_RETRIED = "tasks.retried";
    public static final String METRIC_TASKS_WAITING = "tasks.waiting";
    public static final String METRIC_TASKS_RUNNING = "tasks.running";
    public static final String METRIC_TASKS_LOCK_TIMEOUTS = "tasks.lock.timeouts";
    public static final String METRIC_TASKS_LOCK_CONFLICTS = "tasks.lock.conflicts";
    public static final String METRIC_TASKS_LOCK_ATTEMPTS = "tasks.lock.attempts";
    public static final String METRIC_TASKS_LOCK_ACQUIRED = "tasks.lock.acquired";
    public static final String METRIC_TASKS_LOCK_RELEASED = "tasks.lock.released";
    public static final String METRIC_TASKS_EXECUTION_TIME = "tasks.execution.time";
    public static final String METRIC_TASKS_RECOVERY_ATTEMPTS = "tasks.recovery.attempts";
    public static final String METRIC_TASKS_RECOVERY_SUCCESSES = "tasks.recovery.successes";

    private final Map<String, AtomicLong> taskMetrics = new ConcurrentHashMap<>();

    /**
     * Updates a metric counter
     * @param metric The metric name to update
     */
    public void updateMetric(String metric) {
        taskMetrics.computeIfAbsent(metric, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Updates a metric counter by a specific value
     * @param metric The metric name to update
     * @param value The value to add
     */
    public void updateMetric(String metric, long value) {
        taskMetrics.computeIfAbsent(metric, k -> new AtomicLong()).addAndGet(value);
    }

    /**
     * Gets the current value of a metric
     * @param metric The metric name
     * @return The current value, or 0 if metric doesn't exist
     */
    public long getMetric(String metric) {
        AtomicLong value = taskMetrics.get(metric);
        return value != null ? value.get() : 0;
    }

    /**
     * Gets all metrics as a map
     * @return Map of metric names to their current values
     */
    public Map<String, Long> getAllMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        taskMetrics.forEach((key, value) -> metrics.put(key, value.get()));
        return metrics;
    }

    /**
     * Resets all metrics to zero
     */
    public void resetMetrics() {
        taskMetrics.clear();
    }
} 
