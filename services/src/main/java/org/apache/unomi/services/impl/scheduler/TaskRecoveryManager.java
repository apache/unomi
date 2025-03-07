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
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages task recovery after node crashes or failures.
 * Handles task state recovery, lock recovery, and task resumption.
 */
public class TaskRecoveryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskRecoveryManager.class);
    private static final int MAX_CRASH_RECOVERY_AGE_MINUTES = 60; // 1 hour

    private final String nodeId;
    private final PersistenceService persistenceService;
    private final TaskStateManager stateManager;
    private final TaskLockManager lockManager;
    private final TaskMetricsManager metricsManager;
    private final TaskExecutionManager executionManager;
    private final SchedulerServiceImpl schedulerService;

    public TaskRecoveryManager(String nodeId,
                             PersistenceService persistenceService,
                             TaskStateManager stateManager,
                             TaskLockManager lockManager,
                             TaskMetricsManager metricsManager,
                             TaskExecutionManager executionManager,
                             SchedulerServiceImpl schedulerService) {
        this.nodeId = nodeId;
        this.persistenceService = persistenceService;
        this.stateManager = stateManager;
        this.lockManager = lockManager;
        this.metricsManager = metricsManager;
        this.executionManager = executionManager;
        this.schedulerService = schedulerService;
    }

    /**
     * Recovers tasks after node crashes or failures.
     * Recovery process:
     * 1. Identify tasks with expired locks
     * 2. Release locks and update states
     * 3. Attempt to resume tasks with checkpoint data
     * 4. Reschedule tasks that can't be resumed
     */
    public void recoverCrashedTasks() {
        try {
            recoverRunningTasks();
            recoverLockedTasks();
        } catch (Exception e) {
            LOGGER.error("Node {} Error recovering crashed tasks", nodeId, e);
        }
    }

    /**
     * Recovers tasks that are marked as running but have expired locks
     */
    private void recoverRunningTasks() {
        List<ScheduledTask> runningTasks = findTasksByStatus(ScheduledTask.TaskStatus.RUNNING);

        for (ScheduledTask task : runningTasks) {
            if (lockManager.isLockExpired(task)) {
                LOGGER.info("Node {} Recovering crashed task {} : {}", nodeId, task.getTaskType(), task.getItemId());
                recoverCrashedTask(task);
            }
        }
    }

    /**
     * Recovers a single crashed task
     */
    private void recoverCrashedTask(ScheduledTask task) {
        // Skip cancelled tasks - they should not be recovered
        if (task.getStatus() == ScheduledTask.TaskStatus.CANCELLED) {
            LOGGER.debug("Node {} Skipping recovery of cancelled task {} : {}", nodeId, task.getTaskType(), task.getItemId());
            return;
        }

        // First mark as crashed and release lock
        String previousOwner = task.getLockOwner();
        if (task.getStatus() != ScheduledTask.TaskStatus.CRASHED) {
            stateManager.updateTaskState(task, ScheduledTask.TaskStatus.CRASHED,
                "Node failure detected: " + previousOwner, nodeId);
        }

        // Record the crash in execution history
        recordCrash(task, previousOwner);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_CRASHED);

        if (persistenceService.save(task)) {
            // If task has checkpoint data and can be resumed, try to resume it
            TaskExecutor executor = executionManager.getTaskExecutor(task.getTaskType());
            if (executor != null && executor.canResume(task)) {
                attemptTaskResumption(task, executor);
            } else {
                // If task can't be resumed, try to restart it
                if (shouldRestartTask(task)) {
                    attemptTaskRestart(task, executor);
                }
            }
        }
    }

    /**
     * Records a task crash in its execution history
     */
    private void recordCrash(ScheduledTask task, String previousOwner) {
        Map<String, Object> crash = new HashMap<>();
        crash.put("timestamp", new Date());
        crash.put("type", "crash");
        crash.put("previousOwner", previousOwner);
        crash.put("recoveryNode", nodeId);

        Map<String, Object> details = task.getStatusDetails();
        if (details == null) {
            details = new HashMap<>();
            task.setStatusDetails(details);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) details.get("executionHistory");
        if (history == null) {
            history = new ArrayList<>();
            details.put("executionHistory", history);
        }

        if (history.size() >= 10) {
            history.remove(0);
        }
        history.add(crash);
    }

    /**
     * Attempts to resume a crashed task
     */
    private void attemptTaskResumption(ScheduledTask task, TaskExecutor executor) {
        LOGGER.info("Node {} resuming crashed task {} : {}", nodeId, task.getTaskType(), task.getItemId());
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_RESUMED);
        stateManager.resetTaskToScheduled(task);
        if (lockManager.acquireLock(task)) {
            executionManager.executeTask(task, executor);
        }
    }

    /**
     * Attempts to restart a task that can't be resumed
     */
    private void attemptTaskRestart(ScheduledTask task, TaskExecutor executor) {
        LOGGER.info("Node {} restarting crashed task: {}", nodeId, task.getItemId());
        stateManager.resetTaskToScheduled(task);
        if (lockManager.acquireLock(task)) {
            executionManager.executeTask(task, executor);
        }
    }

    /**
     * Recovers tasks with expired locks that are not marked as running
     */
    private void recoverLockedTasks() {
        List<ScheduledTask> lockedTasks = findLockedTasks();

        for (ScheduledTask task : lockedTasks) {
            if (lockManager.isLockExpired(task)) {
                LOGGER.info("Node {} releasing expired lock for task: {}", nodeId, task.getItemId());
                recoverLockedTask(task);
            }
        }
    }

    /**
     * Recovers a single locked task
     */
    private void recoverLockedTask(ScheduledTask task) {
        lockManager.releaseLock(task);

        // Check if task can be rescheduled
        if (task.getStatus() == ScheduledTask.TaskStatus.WAITING &&
            stateManager.canRescheduleTask(task, getTaskDependencies(task))) {
            stateManager.resetTaskToScheduled(task);
        }

        if (persistenceService.save(task)) {
            // If task is now scheduled, try to execute it
            if (task.getStatus() == ScheduledTask.TaskStatus.SCHEDULED) {
                TaskExecutor executor = executionManager.getTaskExecutor(task.getTaskType());
                if (executor != null) {
                    executionManager.executeTask(task, executor);
                }
            }
        }
    }

    /**
     * Determines if a crashed task should be restarted
     */
    private boolean shouldRestartTask(ScheduledTask task) {
        // Don't restart one-shot tasks that have already started
        if (task.isOneShot() && task.getLastExecutionDate() != null) {
            return false;
        }

        // Check retry configuration
        if (task.getMaxRetries() > 0 && task.getFailureCount() >= task.getMaxRetries()) {
            return false;
        }

        return task.isEnabled();
    }

    /**
     * Finds tasks by status
     */
    private List<ScheduledTask> findTasksByStatus(ScheduledTask.TaskStatus status) {
        Condition statusCondition = new Condition(PROPERTY_CONDITION_TYPE);
        statusCondition.setParameter("propertyName", "status");
        statusCondition.setParameter("comparisonOperator", "equals");
        statusCondition.setParameter("propertyValue", status);

        return persistenceService.query(statusCondition, null, ScheduledTask.class, 0, -1).getList();
    }

    /**
     * Finds tasks with locks
     */
    private List<ScheduledTask> findLockedTasks() {
        Condition lockCondition = new Condition(PROPERTY_CONDITION_TYPE);
        lockCondition.setParameter("propertyName", "lockOwner");
        lockCondition.setParameter("comparisonOperator", "exists");

        Condition statusCondition = new Condition(PROPERTY_CONDITION_TYPE);
        statusCondition.setParameter("propertyName", "status");
        statusCondition.setParameter("comparisonOperator", "in");
        statusCondition.setParameter("propertyValues", Arrays.asList(
            ScheduledTask.TaskStatus.SCHEDULED,
            ScheduledTask.TaskStatus.WAITING
        ));

        Condition andCondition = new Condition(BOOLEAN_CONDITION_TYPE);
        andCondition.setParameter("operator", "and");
        andCondition.setParameter("subConditions", Arrays.asList(lockCondition, statusCondition));

        return persistenceService.query(andCondition, null, ScheduledTask.class, 0, -1).getList();
    }

    /**
     * Gets dependencies for a task
     */
    private Map<String, ScheduledTask> getTaskDependencies(ScheduledTask task) {
        if (task.getDependsOn() == null || task.getDependsOn().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, ScheduledTask> dependencies = new HashMap<>();
        for (String dependencyId : task.getDependsOn()) {
            ScheduledTask dependency = persistenceService.load(dependencyId, ScheduledTask.class);
            if (dependency != null) {
                dependencies.put(dependencyId, dependency);
            }
        }
        return dependencies;
    }

    /**
     * Update running task to crashed state
     */
    private void markAsCrashed(ScheduledTask task) {
        try {
            if (task != null) {
                // Mark the task as crashed so it can be recovered
                task.setStatus(ScheduledTask.TaskStatus.CRASHED);
                task.setCurrentStep("CRASHED");
                if (task.getStatusDetails() == null) {
                    task.setStatusDetails(new HashMap<>());
                }
                task.getStatusDetails().put("crashTime", new Date());
                task.getStatusDetails().put("crashedNode", task.getLockOwner());

                // Release the lock but preserve the lock owner for reference
                String lockOwner = task.getLockOwner();
                lockManager.releaseLock(task);
                task.getStatusDetails().put("crashedNode", lockOwner);

                if (schedulerService.saveTask(task)) {
                    LOGGER.info("Task {} marked as crashed (previous lock owner: {})", task.getItemId(), lockOwner);
                    metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_CRASHED);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to mark task as crashed: {}", task.getItemId(), e);
        }
    }

    /**
     * Resets a task that has been in running state for too long
     */
    private void resetStalledTask(ScheduledTask task) {
        try {
            if (task != null) {
                // Mark the task as failed due to timeout
                stateManager.updateTaskState(task, ScheduledTask.TaskStatus.FAILED, "Task execution timeout exceeded", nodeId);
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_FAILED);

                if (schedulerService.saveTask(task)) {
                    LOGGER.info("Stalled task {} reset to FAILED state", task.getItemId());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to reset stalled task: {}", task.getItemId(), e);
        }
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
