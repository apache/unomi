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
    private boolean executorNode = true;

    /**
     * Creates the manager for Blueprint dependency injection.
     */
    public TaskRecoveryManager() {
        // Parameterless constructor for Blueprint dependency injection
    }

    /**
     * Sets the cluster node ID.
     *
     * @param nodeId the node ID
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * Sets the task state manager.
     *
     * @param stateManager the state manager
     */
    public void setStateManager(TaskStateManager stateManager) {
        this.stateManager = stateManager;
    }

    /**
     * Sets the task lock manager.
     *
     * @param lockManager the lock manager
     */
    public void setLockManager(TaskLockManager lockManager) {
        this.lockManager = lockManager;
    }

    /**
     * Sets the task metrics manager.
     *
     * @param metricsManager the metrics manager
     */
    public void setMetricsManager(TaskMetricsManager metricsManager) {
        this.metricsManager = metricsManager;
    }

    /**
     * Sets the task execution manager.
     *
     * @param executionManager the execution manager
     */
    public void setExecutionManager(TaskExecutionManager executionManager) {
        this.executionManager = executionManager;
    }

    /**
     * Sets the task executor registry.
     *
     * @param executorRegistry the executor registry
     */
    public void setExecutorRegistry(TaskExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    /**
     * Sets the scheduler service reference.
     *
     * @param schedulerService the scheduler service
     */
    public void setSchedulerService(SchedulerServiceImpl schedulerService) {
        this.schedulerService = schedulerService;
    }

    /**
     * Sets whether this node is an executor node. Non-executors may still mark
     * crashed tasks and clear dead locks, but must not dispatch resume/restart
     * unless the task is {@code runOnAllNodes}.
     *
     * @param executorNode true when this node runs cluster tasks
     */
    public void setExecutorNode(boolean executorNode) {
        this.executorNode = executorNode;
    }

    /**
     * Marks the manager as shutting down so recovery work is skipped.
     */
    public void prepareForShutdown() {
        this.shutdownNow = true;
        LOGGER.debug("TaskRecoveryManager prepared for shutdown");
    }

    /**
     * Recovers crashed and stale locked tasks after node failure.
     * <p>
     * Running tasks with expired locks are marked crashed, then resumed or restarted.
     * Expired locks on non-running tasks are released and eligible tasks are rescheduled.
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
     * Recovers running tasks whose locks have expired.
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
     * Recovers one crashed task by marking it crashed, recording history, and resuming or restarting it.
     *
     * @param task the task to recover
     */
    private void recoverCrashedTask(ScheduledTask task) {
        // Skip cancelled tasks - they should not be recovered
        if (task.getStatus() == ScheduledTask.TaskStatus.CANCELLED) {
            LOGGER.debug("Node {} Skipping recovery of cancelled task {} : {}", nodeId, task.getTaskType(), task.getItemId());
            return;
        }

        // Re-check shutdown right before writing: preDestroy() marks RUNNING tasks owned by this
        // node as crashed with a more specific "Interrupted by scheduler shutdown" cause, and that
        // write must win over the generic "Node failure detected" cause below if both race.
        if (shutdownNow) {
            LOGGER.debug("Node {} Skipping recovery of task {} : {} as scheduler is shutting down",
                nodeId, task.getTaskType(), task.getItemId());
            return;
        }

        // Reload + CAS so two survivors do not both alwaysOverwrite CRASHED/history.
        ScheduledTask latest = schedulerService.getTask(task.getItemId(), true);
        if (latest == null) {
            latest = task;
        }
        if (latest.getStatus() == ScheduledTask.TaskStatus.CANCELLED) {
            return;
        }
        if (latest.getStatus() != ScheduledTask.TaskStatus.CRASHED
                && latest.getStatus() != ScheduledTask.TaskStatus.RUNNING) {
            LOGGER.debug("Node {} Skipping recovery of task {} : {} — store status is {}",
                nodeId, latest.getTaskType(), latest.getItemId(), latest.getStatus());
            return;
        }
        if (latest.getStatus() == ScheduledTask.TaskStatus.RUNNING && !lockManager.isLockExpired(latest)) {
            LOGGER.debug("Node {} Skipping recovery of task {} : {} — lock no longer expired",
                nodeId, latest.getTaskType(), latest.getItemId());
            return;
        }

        // Carry OCC tokens from the fresh load when present
        Object seq = latest.getSystemMetadata("seq_no");
        if (seq == null) {
            seq = latest.getSystemMetadata("_seq_no");
        }
        Object term = latest.getSystemMetadata("primary_term");
        if (term == null) {
            term = latest.getSystemMetadata("_primary_term");
        }
        if (seq != null) {
            latest.setSystemMetadata("seq_no", seq);
            latest.setSystemMetadata("_seq_no", seq);
        }
        if (term != null) {
            latest.setSystemMetadata("primary_term", term);
            latest.setSystemMetadata("_primary_term", term);
        }

        // Mark as crashed, then drop the dead owner's lock. prepareForExecution() /
        // acquireLock() takes a fresh lock on resume/restart. Leaving an expired lock
        // in place races recoverLockedTasks() in the same pass: it can overwrite the
        // newly acquired lock (alwaysOverwrite save) and the resume dispatch fails
        // verification with "Lost lock ownership".
        String previousOwner = latest.getLockOwner();
        if (latest.getStatus() != ScheduledTask.TaskStatus.CRASHED) {
            stateManager.updateTaskState(latest, ScheduledTask.TaskStatus.CRASHED,
                "Node failure detected: " + previousOwner, nodeId);
        }
        latest.setLockOwner(null);
        latest.setLockDate(null);

        // Record the crash in execution history
        recordCrash(latest, previousOwner);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_CRASHED);

        boolean saved = latest.isPersistent()
            ? schedulerService.saveTaskWithRefresh(latest)
            : schedulerService.saveTask(latest);
        if (!saved) {
            LOGGER.debug("Node {} lost CRASH CAS race for task {} : {}",
                nodeId, latest.getTaskType(), latest.getItemId());
            return;
        }

        // Keep caller-visible fields in sync for tests that hold the original reference
        task.setStatus(latest.getStatus());
        task.setLockOwner(null);
        task.setLockDate(null);
        task.setStatusDetails(latest.getStatusDetails());
        task.setCurrentStep(latest.getCurrentStep());
        task.setLastError(latest.getLastError());
        task.setCheckpointData(latest.getCheckpointData());

        TaskExecutor executor = executorRegistry.getExecutor(latest.getTaskType());
        if (executor != null && executor.canResume(latest)) {
            attemptTaskResumption(latest, executor);
        } else if (shouldRestartTask(latest)) {
            attemptTaskRestart(latest, executor);
        }
    }

    /**
     * Appends a crash entry to the task execution history.
     *
     * @param task the crashed task
     * @param previousOwner the node that previously held the lock
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
     * Reschedules and executes a crashed task that supports resumption.
     *
     * @param task the task to resume
     * @param executor the executor for the task type
     */
    private void attemptTaskResumption(ScheduledTask task, TaskExecutor executor) {
        if (executor == null) {
            LOGGER.warn("Node {} cannot resume task {} : {} — no executor registered",
                nodeId, task.getTaskType(), task.getItemId());
            return;
        }
        if (!mayDispatchRecovery(task)) {
            LOGGER.debug("Node {} marked task {} : {} CRASHED but not dispatching resume (non-executor)",
                nodeId, task.getTaskType(), task.getItemId());
            return;
        }
        LOGGER.info("Node {} resuming crashed task {} : {}", nodeId, task.getTaskType(), task.getItemId());
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_RESUMED);
        // Keep CRASHED so the execution wrapper calls executor.resume(). Persist so the
        // checker can retry if executeTask is a no-op (e.g. previous dispatch claim held).
        // Do not pre-acquire the lock here — prepareForExecution owns locking.
        schedulerService.saveTask(task);
        executionManager.executeTask(task, executor);
    }

    /**
     * Reschedules and executes a crashed task that cannot be resumed.
     *
     * @param task the task to restart
     * @param executor the executor for the task type
     */
    private void attemptTaskRestart(ScheduledTask task, TaskExecutor executor) {
        if (executor == null) {
            LOGGER.warn("Node {} cannot restart task {} : {} — no executor registered",
                nodeId, task.getTaskType(), task.getItemId());
            return;
        }
        if (!mayDispatchRecovery(task)) {
            LOGGER.debug("Node {} marked task {} : {} CRASHED but not dispatching restart (non-executor)",
                nodeId, task.getTaskType(), task.getItemId());
            return;
        }
        LOGGER.info("Node {} restarting crashed task: {}", nodeId, task.getItemId());
        stateManager.resetTaskToScheduled(task);
        task.setNextScheduledExecution(new Date());
        // Persist SCHEDULED before best-effort dispatch so the checker is a safety net when
        // executeTask no-ops (dispatch claim still held by a dying node's stalled wrapper).
        schedulerService.saveTask(task);
        executionManager.executeTask(task, executor);
    }

    /**
     * Releases expired locks on tasks that are not currently running.
     */
    private void recoverLockedTasks() {
        List<ScheduledTask> lockedTasks = schedulerService.findLockedTasks();

        for (ScheduledTask task : lockedTasks) {
            // RUNNING / CRASHED are owned by recoverRunningTasks (resume/restart).
            // Releasing their locks here races the async dispatch that just acquired a new lock.
            if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING
                || task.getStatus() == ScheduledTask.TaskStatus.CRASHED) {
                continue;
            }
            if (lockManager.isLockExpired(task)) {
                LOGGER.info("Node {} releasing expired lock for task: {}", nodeId, task.getItemId());
                recoverLockedTask(task);
            }
        }
    }

    /**
     * Releases an expired lock and reschedules the task when appropriate.
     *
     * @param task the locked task to recover
     */
    private void recoverLockedTask(ScheduledTask task) {
        lockManager.releaseLock(task);

        // Check if task can be rescheduled
        if (task.getStatus() == ScheduledTask.TaskStatus.WAITING &&
            stateManager.canRescheduleTask(task, getTaskDependencies(task))) {
            stateManager.resetTaskToScheduled(task);
        }

        if (schedulerService.saveTask(task)) {
            // If task is now scheduled, try to execute it (executor nodes / runOnAllNodes only)
            if (task.getStatus() == ScheduledTask.TaskStatus.SCHEDULED && mayDispatchRecovery(task)) {
                TaskExecutor executor = executorRegistry.getExecutor(task.getTaskType());
                if (executor != null) {
                    executionManager.executeTask(task, executor);
                }
            }
        }
    }

    /**
     * Whether this node may dispatch resume/restart after marking a task crashed.
     * Non-executors may still persist CRASHED / clear locks, but only executors
     * (or runOnAllNodes tasks) should run the work.
     */
    private boolean mayDispatchRecovery(ScheduledTask task) {
        return executorNode || task.isRunOnAllNodes();
    }

    /**
     * Returns whether a crashed task should be restarted instead of abandoned.
     *
     * @param task the crashed task
     * @return {@code true} when the task should be restarted
     */
    private boolean shouldRestartTask(ScheduledTask task) {
        if (!task.isEnabled()) {
            return false;
        }

        // Align with handleTaskError(): after a failure, failureCount is incremented and a
        // retry is scheduled while failureCount <= maxRetries. A crash mid-attempt has not
        // yet incremented failureCount for that attempt, so restart while still within budget.
        // Importantly, do NOT abandon one-shots merely because lastExecutionDate is set —
        // that field is written on every failure, and abandoning them stranded one-shots that
        // crashed mid-retry with budget remaining.
        return task.getFailureCount() <= task.getMaxRetries();
    }

    /**
     * Loads dependency tasks referenced by the given task.
     *
     * @param task the task whose dependencies are needed
     * @return dependency tasks keyed by ID
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
     * Marks a running task as crashed and releases its lock.
     *
     * @param task the task to mark as crashed
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
     * Marks a stalled running task as failed after a timeout.
     *
     * @param task the stalled task
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
