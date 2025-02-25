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
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages task locking and unlocking in both persistent and in-memory contexts.
 * Handles lock acquisition, release, and expiration for task execution.
 */
public class TaskLockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskLockManager.class);

    private final String nodeId;
    private final long lockTimeout;
    private final PersistenceService persistenceService;
    private final ConcurrentMap<String, String> runningTaskTypes;
    private final TaskMetricsManager metricsManager;

    public TaskLockManager(String nodeId, long lockTimeout, PersistenceService persistenceService, TaskMetricsManager metricsManager) {
        this.nodeId = nodeId;
        this.lockTimeout = lockTimeout;
        this.persistenceService = persistenceService;
        this.metricsManager = metricsManager;
        this.runningTaskTypes = new ConcurrentHashMap<>();
    }

    /**
     * Attempts to acquire a lock for task execution
     */
    public boolean acquireLock(ScheduledTask task) {
        if (task.isAllowParallelExecution()) {
            return true;
        }

        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ATTEMPTS);
        return task.isPersistent() ? 
            acquirePersistentLock(task) : 
            acquireMemoryLock(task);
    }

    /**
     * Acquires a lock for persistent tasks using persistence service
     */
    private boolean acquirePersistentLock(ScheduledTask task) {
        try {
            List<ScheduledTask> runningTasks = findRunningTasksOfType(task.getTaskType());
            
            // Check for valid locks first
            if (hasValidLock(runningTasks)) {
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
                return false;
            }

            // Try to acquire lock
            String oldLockOwner = task.getLockOwner();
            setTaskLock(task, nodeId);
            
            // Save with optimistic locking
            if (!persistenceService.save(task)) {
                setTaskLock(task, oldLockOwner);
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
                return false;
            }

            // Verify lock ownership
            ScheduledTask savedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
            if (savedTask == null || !nodeId.equals(savedTask.getLockOwner())) {
                setTaskLock(task, oldLockOwner);
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            LOGGER.debug("Failed to acquire lock for task: {}", task.getItemId(), e);
            setTaskLock(task, null);
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
            return false;
        }
    }

    /**
     * Acquires a lock for in-memory tasks
     */
    private boolean acquireMemoryLock(ScheduledTask task) {
        synchronized (runningTaskTypes) {
            String runningNodeId = runningTaskTypes.get(task.getTaskType());
            if (runningNodeId != null && !nodeId.equals(runningNodeId)) {
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
                return false;
            }

            setTaskLock(task, nodeId);
            runningTaskTypes.put(task.getTaskType(), nodeId);
            return true;
        }
    }

    /**
     * Releases a task lock
     */
    public void releaseLock(ScheduledTask task) {
        if (!task.isAllowParallelExecution()) {
            String oldLockOwner = task.getLockOwner();
            clearLock(task);

            if (task.isPersistent()) {
                releasePersistentLock(task, oldLockOwner);
            } else {
                releaseMemoryLock(task);
            }
        }
    }

    /**
     * Releases a persistent task lock
     */
    private void releasePersistentLock(ScheduledTask task, String oldLockOwner) {
        try {
            if (!persistenceService.save(task)) {
                // If save failed, restore old lock owner
                setTaskLock(task, oldLockOwner);
            }
        } catch (Exception e) {
            LOGGER.error("Error releasing lock for task: " + task.getItemId(), e);
            // Restore old lock owner on error
            setTaskLock(task, oldLockOwner);
        }
    }

    /**
     * Releases an in-memory task lock
     */
    private void releaseMemoryLock(ScheduledTask task) {
        runningTaskTypes.remove(task.getTaskType());
    }

    /**
     * Checks if a lock has expired
     */
    public boolean isLockExpired(ScheduledTask task) {
        boolean expired = task.getLockDate() != null &&
               System.currentTimeMillis() - task.getLockDate().getTime() > lockTimeout;
        if (expired) {
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_TIMEOUTS);
        }
        return expired;
    }

    /**
     * Validates lock ownership
     */
    public boolean validateLockOwnership(ScheduledTask task) {
        if (!task.isAllowParallelExecution() && task.getLockOwner() != null) {
            if (!nodeId.equals(task.getLockOwner())) {
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
                return false;
            }
            if (isLockExpired(task)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds all running tasks of a specific type
     */
    private List<ScheduledTask> findRunningTasksOfType(String taskType) {
        Condition condition = new Condition(PROPERTY_CONDITION_TYPE);
        condition.setParameter("propertyName", "taskType");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", taskType);

        Condition statusCondition = new Condition(PROPERTY_CONDITION_TYPE);
        statusCondition.setParameter("propertyName", "status");
        statusCondition.setParameter("comparisonOperator", "equals");
        statusCondition.setParameter("propertyValue", ScheduledTask.TaskStatus.RUNNING.toString());

        Condition andCondition = new Condition(BOOLEAN_CONDITION_TYPE);
        andCondition.setParameter("operator", "and");
        andCondition.setParameter("subConditions", Arrays.asList(condition, statusCondition));

        return persistenceService.query(andCondition, null, ScheduledTask.class, 0, -1).getList();
    }

    /**
     * Checks if any task in the list has a valid lock
     */
    private boolean hasValidLock(List<ScheduledTask> tasks) {
        return tasks.stream()
            .anyMatch(t -> !isLockExpired(t));
    }

    /**
     * Sets task lock information
     */
    private void setTaskLock(ScheduledTask task, String owner) {
        task.setLockOwner(owner);
        task.setLockDate(owner != null ? new Date() : null);
    }

    /**
     * Clears lock information from a task
     */
    private void clearLock(ScheduledTask task) {
        task.setLockOwner(null);
        task.setLockDate(null);
    }

    // Condition types for persistence queries
    private static final ConditionType PROPERTY_CONDITION_TYPE = new ConditionType();
    private static final ConditionType BOOLEAN_CONDITION_TYPE = new ConditionType();
    
    static {
        PROPERTY_CONDITION_TYPE.setItemId("propertyCondition");
        PROPERTY_CONDITION_TYPE.setItemType(ConditionType.ITEM_TYPE);
        PROPERTY_CONDITION_TYPE.setConditionEvaluator("propertyConditionEvaluator");
        PROPERTY_CONDITION_TYPE.setQueryBuilder("propertyConditionESQueryBuilder");

        BOOLEAN_CONDITION_TYPE.setItemId("booleanCondition");
        BOOLEAN_CONDITION_TYPE.setItemType(ConditionType.ITEM_TYPE);
        BOOLEAN_CONDITION_TYPE.setConditionEvaluator("booleanConditionEvaluator");
        BOOLEAN_CONDITION_TYPE.setQueryBuilder("booleanConditionESQueryBuilder");
    }
} 