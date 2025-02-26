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

import org.apache.unomi.api.tasks.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages task execution history, including success/failure records,
 * execution times, and crash records.
 */
public class TaskHistoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskHistoryManager.class);
    private static final int MAX_HISTORY_SIZE = 10;

    private final String nodeId;
    private final TaskMetricsManager metricsManager;

    public TaskHistoryManager(String nodeId, TaskMetricsManager metricsManager) {
        this.nodeId = nodeId;
        this.metricsManager = metricsManager;
    }

    /**
     * Records a successful task execution
     */
    public void recordSuccess(ScheduledTask task, long executionTime) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", new Date());
        entry.put("status", "SUCCESS");
        entry.put("nodeId", nodeId);
        entry.put("executionTime", executionTime);
        
        addToHistory(task, entry);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_EXECUTION_TIME, executionTime);
    }

    /**
     * Records a failed task execution
     */
    public void recordFailure(ScheduledTask task, String error) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", new Date());
        entry.put("status", "FAILED");
        entry.put("nodeId", nodeId);
        entry.put("error", error);
        
        addToHistory(task, entry);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_FAILED);
    }

    /**
     * Records a task crash
     */
    public void recordCrash(ScheduledTask task) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", new Date());
        entry.put("status", "CRASHED");
        entry.put("nodeId", nodeId);
        
        addToHistory(task, entry);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_CRASHED);
    }

    /**
     * Records task cancellation
     */
    public void recordCancellation(ScheduledTask task) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", new Date());
        entry.put("status", "CANCELLED");
        entry.put("nodeId", nodeId);
        
        addToHistory(task, entry);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_CANCELLED);
    }

    public void recordResume(ScheduledTask task) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", new Date());
        entry.put("status", "RESUMED");
        entry.put("nodeId", nodeId);
        
        addToHistory(task, entry);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_RESUMED);
    }

    public void recordRetry(ScheduledTask task) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", new Date());
        entry.put("status", "RETRIED");
        entry.put("nodeId", nodeId);
        
        addToHistory(task, entry);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_RETRIED);
    }

    private void addToHistory(ScheduledTask task, Map<String, Object> entry) {
        Map<String, Object> details = task.getStatusDetails();
        if (details == null) {
            details = new HashMap<>();
            task.setStatusDetails(details);
        } else if (!(details instanceof HashMap)) {
            // If the details map is unmodifiable, create a new modifiable copy
            details = new HashMap<>(details);
            task.setStatusDetails(details);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) details.get("executionHistory");
        if (history == null) {
            history = new ArrayList<>();
            details.put("executionHistory", history);
        } else if (!(history instanceof ArrayList)) {
            // If the history list is unmodifiable, create a new modifiable copy
            history = new ArrayList<>(history);
            details.put("executionHistory", history);
        }

        // Maintain history size limit
        while (history.size() >= MAX_HISTORY_SIZE) {
            history.remove(0);
        }

        history.add(entry);
    }

    /**
     * Gets execution history for a task
     */
    public List<Map<String, Object>> getExecutionHistory(ScheduledTask task) {
        Map<String, Object> details = task.getStatusDetails();
        if (details == null) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) details.get("executionHistory");
        return history != null ? history : Collections.emptyList();
    }
} 