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
import org.apache.unomi.api.conditions.ConditionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages task locks to coordinate execution in a cluster environment.
 * This class ensures that tasks which don't allow parallel execution
 * only run on a single node at a time.
 */
public class TaskLockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskLockManager.class);

    private final String nodeId;
    private final long lockTimeout;
    private final TaskMetricsManager metricsManager;
    private final SchedulerServiceImpl schedulerService;

    public TaskLockManager(String nodeId, long lockTimeout,  
                          TaskMetricsManager metricsManager, SchedulerServiceImpl schedulerService) {
        this.nodeId = nodeId;
        this.lockTimeout = lockTimeout;
        this.metricsManager = metricsManager;
        this.schedulerService = schedulerService;
    }

    /**
     * Attempts to acquire a lock for the given task.
     * For non-persistent tasks, simply sets lock info.
     * For persistent tasks, performs optimistic locking.
     *
     * @param task Task to lock
     * @return true if lock was successfully acquired
     */
    public boolean acquireLock(ScheduledTask task) {
        if (task == null) {
            return false;
        }

        // Always allow tasks that permit parallel execution
        if (task.isAllowParallelExecution()) {
            // Just set lock info but don't enforce exclusivity
            task.setLockOwner(nodeId);
            task.setLockDate(new Date());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
            return true;
        }

        // For non-persistent tasks, basic in-memory locking
        if (!task.isPersistent()) {
            if (task.getLockOwner() != null && !nodeId.equals(task.getLockOwner())) {
                if (!isLockExpired(task)) {
                    return false;
                }
            }
            task.setLockOwner(nodeId);
            task.setLockDate(new Date());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
            return true;
        }

        // For persistent tasks
        try {
            task.setLockOwner(nodeId);
            task.setLockDate(new Date());
            
            if (!schedulerService.saveTask(task)) {
                LOGGER.debug("Failed to acquire lock for task {}", task.getItemId());
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
                return false;
            }
            
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error acquiring lock for task {}: {}", task.getItemId(), e.getMessage());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
            return false;
        }
    }

    /**
     * Releases a lock on the given task.
     *
     * @param task Task to unlock
     * @return true if unlock was successful
     */
    public boolean releaseLock(ScheduledTask task) {
        if (task == null) {
            return false;
        }

        try {
            task.setLockOwner(null);
            task.setLockDate(null);
            
            if (!schedulerService.saveTask(task)) {
                LOGGER.error("Failed to release lock for task {}", task.getItemId());
                return false;
            }
            
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_RELEASED);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error releasing lock for task {}: {}", task.getItemId(), e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a task's lock has expired based on timeout.
     *
     * @param task Task to check
     * @return true if lock has expired or if task has no lock
     */
    public boolean isLockExpired(ScheduledTask task) {
        if (task == null || task.getLockDate() == null) {
            return true;
        }

        long lockAge = System.currentTimeMillis() - task.getLockDate().getTime();
        return lockAge > lockTimeout;
    }

}
