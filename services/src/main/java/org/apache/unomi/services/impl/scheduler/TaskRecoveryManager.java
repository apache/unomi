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

    private String nodeId;
    private TaskStateManager stateManager;
    private TaskLockManager lockManager;
    private TaskMetricsManager metricsManager;
    private TaskExecutionManager executionManager;
    private TaskExecutorRegistry executorRegistry;
    private SchedulerServiceImpl schedulerService;
    private volatile boolean shutdownNow = false;

    public TaskRecoveryManager() {
        // Parameterless constructor for Blueprint dependency injection
    }

    // Setter methods for Blueprint dependency injection
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setStateManager(TaskStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void setLockManager(TaskLockManager lockManager) {
        this.lockManager = lockManager;
    }

    public void setMetricsManager(TaskMetricsManager metricsManager) {
        this.metricsManager = metricsManager;
    }

    public void setExecutionManager(TaskExecutionManager executionManager) {
        this.executionManager = executionManager;
    }

    public void setExecutorRegistry(TaskExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    public void setSchedulerService(SchedulerServiceImpl schedulerService) {
        this.schedulerService = schedulerService;
    }

    /**
     * Set the shutdown flag to prevent operations during shutdown
     */
    public void prepareForShutdown() {
        this.shutdownNow = true;
        LOGGER.debug("TaskRecoveryManager prepared for shutdown");
    }

    /**
     * Recovers tasks that crashed due to node failure or unexpected termination
     * Process:
     * 1. Identify tasks with expired locks
     * 2. Release locks and update states
     * 3. Attempt to resume tasks with checkpoint data
     * 4. Reschedule tasks that can't be resumed
     */
    public void recoverCrashedTasks() {
        if (shutdownNow) {
            LOGGER.debug("Skipping crashed task recovery during shutdown");
            return;
        }

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
        if (shutdownNow) return;

        List<ScheduledTask> runningTasks = schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING);

        for (ScheduledTask task : runningTasks) {
            if (shutdownNow) return;

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

        if (schedulerService.saveTask(task)) {
            // If task has checkpoint data and can be resumed, try to resume it
            TaskExecutor executor = executorRegistry.getExecutor(task.getTaskType());
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
        List<ScheduledTask> lockedTasks = schedulerService.findLockedTasks();

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

        if (schedulerService.saveTask(task)) {
            // If task is now scheduled, try to execute it
            if (task.getStatus() == ScheduledTask.TaskStatus.SCHEDULED) {
                TaskExecutor executor = executorRegistry.getExecutor(task.getTaskType());
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
     * Gets dependencies for a task
     */
    private Map<String, ScheduledTask> getTaskDependencies(ScheduledTask task) {
        if (task.getDependsOn() == null || task.getDependsOn().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, ScheduledTask> dependencies = new HashMap<>();
        for (String dependencyId : task.getDependsOn()) {
            ScheduledTask dependency = schedulerService.getTask(dependencyId);
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

}
