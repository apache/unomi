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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages task execution and scheduling, including task checking, execution tracking, and completion handling.
 */
public class TaskExecutionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutionManager.class);
    private static final int MIN_THREAD_POOL_SIZE = 4;
    private static final long TASK_CHECK_INTERVAL = 1000; // 1 second
    private static final long MIN_LOCK_RENEWAL_INTERVAL_MS = 100;

    private String nodeId;
    private ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;
    private TaskStateManager stateManager;
    private TaskLockManager lockManager;
    private TaskMetricsManager metricsManager;
    private TaskHistoryManager historyManager;
    private final Map<String, Set<String>> executingTasksByType;
    private final Map<String, LockRenewalHandle> activeLockRenewals = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * Set at the start of {@link #shutdown()} before canceling in-flight work. Failures that race
     * with shutdown must not schedule retries: {@link ScheduledExecutorService#isShutdown()} only
     * flips after {@code scheduler.shutdown()}, which runs after {@code future.cancel(true)} and
     * can race with {@link #handleTaskError}.
     */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private ScheduledFuture<?> taskCheckerFuture;
    private SchedulerServiceImpl schedulerService;
    private TaskExecutorRegistry executorRegistry;
    private int threadPoolSize = MIN_THREAD_POOL_SIZE;

    /**
     * Creates the execution manager.
     */
    public TaskExecutionManager() {
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.executingTasksByType = new ConcurrentHashMap<>();
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
     * Sets the scheduler thread pool size.
     *
     * @param threadPoolSize the thread pool size
     */
    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = Math.max(MIN_THREAD_POOL_SIZE, threadPoolSize);
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
     * Sets the task history manager.
     *
     * @param historyManager the history manager
     */
    public void setHistoryManager(TaskHistoryManager historyManager) {
        this.historyManager = historyManager;
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
     * Initializes the scheduler after all dependencies are set
     */
    public void initialize() {
        if (scheduler == null) {
            this.scheduler = Executors.newScheduledThreadPool(
                threadPoolSize,
                r -> {
                    Thread t = new Thread(r);
                    t.setName("UnomiScheduler-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
            );
        }
    }

    /**
     * Starts the task checking service if this is an executor node.
     *
     * @param taskChecker runnable that polls for due tasks
     */
    public void startTaskChecker(Runnable taskChecker) {
        if (running.compareAndSet(false, true)) {
            taskCheckerFuture = scheduler.scheduleAtFixedRate(
                taskChecker,
                0,
                TASK_CHECK_INTERVAL,
                TimeUnit.MILLISECONDS
            );
            LOGGER.debug("Task checker started with interval {} ms", TASK_CHECK_INTERVAL);
        }
    }

    /**
     * Stops the task checking service
     */
    public void stopTaskChecker() {
        if (running.compareAndSet(true, false) && taskCheckerFuture != null) {
            taskCheckerFuture.cancel(false);
            taskCheckerFuture = null;
            LOGGER.debug("Task checker stopped");
        }
    }

    /**
     * Schedules a task for execution based on its configuration.
     *
     * @param task the task to schedule
     * @param taskRunner runnable invoked when the task is due
     */
    public void scheduleTask(ScheduledTask task, Runnable taskRunner) {
        // Calculate initial execution time if not set
        if (task.getNextScheduledExecution() == null) {
            if (task.getInitialDelay() > 0) {
                // If initial delay is specified, calculate from now
                long nextExecution = System.currentTimeMillis() +
                    task.getTimeUnit().toMillis(task.getInitialDelay());
                task.setNextScheduledExecution(new Date(nextExecution));
            } else {
                // Start immediately
                task.setNextScheduledExecution(new Date());
            }
        }

        // Set task to SCHEDULED state
        if (!ScheduledTask.TaskStatus.SCHEDULED.equals(task.getStatus())) {
            stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
        }

        // Save the task
        schedulerService.saveTask(task);
    }

    /**
     * Executes a task immediately with the specified executor.
     * This method should only be called when a task is ready to execute.
     *
     * @param task the task to execute
     * @param executor the task executor implementation
     */
    public void executeTask(ScheduledTask task, TaskExecutor executor) {
        try {
            if (!task.isEnabled()) {
                LOGGER.debug("Node {} : Task {} is disabled, skipping execution", nodeId, task.getItemId());
                return;
            }

            if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
                LOGGER.debug("Node {} : Task {} is already running", nodeId, task.getItemId());
                return;
            }

            String taskType = task.getTaskType();
            // Ensure the executing set exists even under concurrent clears during shutdown
            Set<String> executingSet = executingTasksByType.computeIfAbsent(taskType, k -> ConcurrentHashMap.newKeySet());

            // Atomically claim this task before dispatching. The persisted/cached status check above
            // is only updated once prepareForExecution() actually runs inside the async taskWrapper below,
            // so a second poll tick landing in that gap could otherwise see a stale non-RUNNING status and
            // dispatch the same task again. Set.add() is atomic, so only one caller can win this race.
            if (!executingSet.add(task.getItemId())) {
                LOGGER.debug("LOCK-DIAG [{}] node {} : executeTask() dispatch claim REJECTED - task already "
                        + "claimed by an in-flight dispatch (this is the duplicate-dispatch guard working)",
                    task.getItemId(), nodeId);
                LOGGER.debug("Node {} : Task {} is already dispatched for execution, skipping duplicate dispatch", nodeId, task.getItemId());
                return;
            }
            LOGGER.debug("LOCK-DIAG [{}] node {} : executeTask() dispatch claim ACQUIRED, scheduling wrapper "
                    + "(caller thread={})",
                task.getItemId(), nodeId, Thread.currentThread().getName());

            TaskExecutor.TaskStatusCallback statusCallback = createStatusCallback(task);
            Runnable taskWrapper = createTaskWrapper(task, executor, statusCallback);

            // Execute task immediately using the scheduler
            try {
                ScheduledFuture<?> future = scheduler.schedule(taskWrapper, 0, TimeUnit.MILLISECONDS);
                scheduledTasks.put(task.getItemId(), future);
            } catch (Exception e) {
                // Scheduling failed (e.g. rejected during shutdown): release the claim so a later
                // attempt isn't permanently blocked, since the wrapper that would normally do so never ran.
                executingSet.remove(task.getItemId());
                throw e;
            }
        } catch (Exception e) {
            LOGGER.error("Node "+nodeId+", Error executing task: " + task.getItemId(), e);
            handleTaskError(task, e.getMessage(), System.currentTimeMillis());
        }
    }

    /**
     * Prepares a task for execution by validating state and acquiring lock if needed.
     *
     * @param task the task to prepare
     * @return true if the task is ready to run
     */
    public boolean prepareForExecution(ScheduledTask task) {
        LOGGER.debug("LOCK-DIAG [{}] node {} : prepareForExecution() starting on thread {}, status={}",
            task.getItemId(), nodeId, Thread.currentThread().getName(), task.getStatus());
        if (!task.isEnabled()) {
            LOGGER.debug("Task {} is disabled", task.getItemId());
            return false;
        }

        // SCHEDULED (normal), CRASHED (recovery/resume), WAITING (deps just satisfied)
        if (task.getStatus() != ScheduledTask.TaskStatus.SCHEDULED &&
            task.getStatus() != ScheduledTask.TaskStatus.CRASHED &&
            task.getStatus() != ScheduledTask.TaskStatus.WAITING) {
            LOGGER.debug("Task {} not in executable state: {}", task.getItemId(), task.getStatus());
            return false;
        }

        // Don't start a SCHEDULED task before its due time. The same task can be dispatched
        // both by its scheduled retry closure and by the periodic task checker; without this
        // guard a duplicate dispatch arriving after an attempt just failed could run the next
        // attempt immediately, ignoring the configured retry delay (or run a periodic task
        // before its next period). A skipped dispatch is not lost: the task checker picks the
        // task up again once its nextScheduledExecution time is actually reached.
        if (task.getStatus() == ScheduledTask.TaskStatus.SCHEDULED) {
            Date nextExecution = task.getNextScheduledExecution();
            if (nextExecution != null && System.currentTimeMillis() < nextExecution.getTime()) {
                LOGGER.debug("Task {} not due yet (next execution at {}), skipping execution",
                    task.getItemId(), nextExecution);
                return false;
            }
        }

        // Acquire lock for exclusive tasks (persistent distributed or in-memory).
        // allowParallelExecution tasks get a non-exclusive lock marker inside acquireLock.
        long lockAttemptStart = System.currentTimeMillis();
        boolean lockAcquired = lockManager.acquireLock(task);
        LOGGER.debug("LOCK-DIAG [{}] node {} : prepareForExecution() acquireLock() returned {} in {} ms",
            task.getItemId(), nodeId, lockAcquired, System.currentTimeMillis() - lockAttemptStart);
        if (!lockAcquired) {
            LOGGER.debug("Could not acquire lock for task: {}", task.getItemId());
            return false;
        }

        // Clear waiting state when leaving WAITING for execution
        if (task.getStatus() == ScheduledTask.TaskStatus.WAITING) {
            task.setWaitingOnTasks(null);
            task.setWaitingForTaskType(null);
        }

        stateManager.updateTaskState(task, ScheduledTask.TaskStatus.RUNNING, null, nodeId);
        schedulerService.saveTask(task);
        LOGGER.debug("LOCK-DIAG [{}] node {} : prepareForExecution() succeeded, task now RUNNING",
            task.getItemId(), nodeId);
        return true;
    }

    /**
     * Creates a status callback for task execution
     */
    private TaskExecutor.TaskStatusCallback createStatusCallback(ScheduledTask task) {
        return new TaskExecutor.TaskStatusCallback() {
            @Override
            public void updateStep(String step, Map<String, Object> details) {
                task.setCurrentStep(step);
                task.setStatusDetails(details);
                schedulerService.saveTask(task);
            }

            @Override
            public void checkpoint(Map<String, Object> checkpointData) {
                task.setCheckpointData(checkpointData);
                schedulerService.saveTask(task);
            }

            @Override
            public void updateStatusDetails(Map<String, Object> details) {
                task.setStatusDetails(details);
                schedulerService.saveTask(task);
            }

            @Override
            public void complete() {
                handleTaskCompletion(task, System.currentTimeMillis());
            }

            @Override
            public void fail(String error) {
                handleTaskError(task, error, System.currentTimeMillis());
            }
        };
    }

    /**
     * Creates a wrapper for task execution
     */
    private Runnable createTaskWrapper(ScheduledTask task, TaskExecutor executor,
                                     TaskExecutor.TaskStatusCallback statusCallback) {
        return () -> {
            // executeTask() has already atomically claimed taskId in executingTasksByType
            // before scheduling this wrapper. Every exit path below - including early returns for
            // shutdown and prepareForExecution() failing - must release that claim in the outer
            // finally, or the task is permanently blocked from ever being dispatched again.
            String taskId = task != null ? task.getItemId() : null;
            String taskType = task != null ? task.getTaskType() : null;
            boolean executingNodeIdSet = false;
            try {
                // Check shutdown flag first - if scheduler is shutting down, skip task execution
                if (schedulerService != null && schedulerService.isShutdownNow()) {
                    LOGGER.debug("Node {} : Skipping task {} execution as scheduler is shutting down",
                        nodeId, taskId != null ? taskId : "unknown");
                    return;
                }

                if (task == null) {
                    LOGGER.error("Node {} : Cannot execute null task", nodeId);
                    return;
                }
                if (executor == null) {
                    LOGGER.error("Node {} : Cannot execute null executor for task type : {}", nodeId, taskType);
                    return;
                }

                if (taskType == null) {
                    LOGGER.error("Task type is null for task: {}", taskId);
                    return;
                }

                // Check shutdown again before preparing for execution
                if (schedulerService != null && schedulerService.isShutdownNow()) {
                    LOGGER.debug("Node {} : Skipping task {} execution as scheduler is shutting down", nodeId, taskId);
                    return;
                }

                // Decide resume vs execute before prepareForExecution() flips CRASHED→RUNNING
                boolean shouldResume = task.getStatus() == ScheduledTask.TaskStatus.CRASHED
                    && executor.canResume(task);

                // Prepare task for execution (both persistent and in-memory)
                if (!prepareForExecution(task)) {
                    return;
                }

                // Final shutdown check before executing. prepareForExecution already set RUNNING
                // and acquired the lock — roll that back so peers are not blocked until lock timeout.
                if (schedulerService != null && schedulerService.isShutdownNow()) {
                    LOGGER.debug("Node {} : Aborting prepared task {} — scheduler is shutting down", nodeId, taskId);
                    abortPreparedExecution(task);
                    return;
                }

                // Set the executing node ID
                task.setExecutingNodeId(nodeId);
                executingNodeIdSet = true;
                schedulerService.saveTask(task);

                // Heartbeat the lock while we execute so peers only see it expire if this
                // node actually dies (stops renewing), not just because execution ran long.
                startLockRenewal(task);

                long startTime = System.currentTimeMillis();
                try {
                    if (shouldResume) {
                        executor.resume(task, statusCallback);
                    } else {
                        executor.execute(task, statusCallback);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error executing task: " + taskId, e);
                    statusCallback.fail(e.getMessage());
                } finally {
                    updateTaskMetrics(task, startTime);
                }
            } catch (Exception e) {
                LOGGER.error("Unexpected error while executing task: " + taskId, e);
                if (statusCallback != null) {
                    statusCallback.fail("Unexpected error: " + e.getMessage());
                }
            } finally {
                // Catch-all renewal stop for paths where no terminal callback ran (executor
                // returned without complete()/fail()); terminal handlers already stopped it.
                stopLockRenewal(taskId);

                // Only clear/save executingNodeId if we actually set it above; otherwise we never
                // touched the task and a redundant save here could race a concurrent legitimate holder.
                // Use CAS, not a blind overwrite: when our own terminal transition above was skipped
                // because a peer already reclaimed/restarted this task (canCommitTerminalTransition
                // returned false), task still holds our stale local view (status RUNNING). A blind
                // save here would clobber the peer's newer state and revive an already-resolved race,
                // letting the peer's restart re-dispatch and double-execute. CAS fails harmlessly
                // instead, since the peer is now the authoritative owner.
                if (executingNodeIdSet) {
                    task.setExecutingNodeId(null);
                    if (!schedulerService.saveTaskWithRefresh(task)) {
                        LOGGER.debug("Node {} : Could not clear executingNodeId for task {} — "
                                + "a peer likely reclaimed it first, which is expected",
                            nodeId, taskId);
                    }
                }

                // Always release the dispatch claim taken by executeTask(), regardless of which path
                // above was taken (including the outer shutdown early-return).
                try {
                    if (taskType != null && taskId != null) {
                        Set<String> executingTasks = executingTasksByType.get(taskType);
                        if (executingTasks != null) {
                            executingTasks.remove(taskId);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error cleaning up task execution state: " + taskId, e);
                }
            }
        };
    }

    /**
     * Rolls back a task that was prepared (RUNNING + lock) but must not execute because
     * the scheduler is shutting down. Marks CRASHED and clears the lock so recovery on
     * the next instance is immediate.
     */
    private void abortPreparedExecution(ScheduledTask task) {
        try {
            stateManager.updateTaskState(task, ScheduledTask.TaskStatus.CRASHED,
                "Interrupted by scheduler shutdown", nodeId);
            task.setExecutingNodeId(null);
            task.setLockOwner(null);
            task.setLockDate(null);
            schedulerService.saveTask(task, true);
        } catch (Exception e) {
            LOGGER.warn("Failed to abort prepared task {} during shutdown: {}",
                task.getItemId(), e.getMessage());
        }
    }

    /**
     * Handle for the periodic lock-renewal (heartbeat) scheduled while a task executes.
     * The mutex serializes renewal ticks against {@link #stopLockRenewal(String)} so a
     * terminal transition can guarantee no renewal write lands after its fresh OCC read.
     */
    private static final class LockRenewalHandle {
        private final Object mutex = new Object();
        private volatile boolean cancelled;
        private volatile ScheduledFuture<?> future;
    }

    /**
     * Starts periodic lock renewal (heartbeating) for a persistent exclusive task while it
     * executes on this node, so its lock only expires when the owner actually stops renewing
     * (crash, shutdown) rather than merely because the execution outlived the lock timeout.
     * Without renewal, crash recovery on a peer can legitimately steal the lock from a node
     * that is still executing and double-run the task.
     */
    private void startLockRenewal(ScheduledTask task) {
        if (!task.isPersistent() || task.isAllowParallelExecution()) {
            return;
        }
        long interval = Math.max(MIN_LOCK_RENEWAL_INTERVAL_MS, lockManager.getLockTimeout() / 3);
        LockRenewalHandle handle = new LockRenewalHandle();
        activeLockRenewals.put(task.getItemId(), handle);
        try {
            handle.future = scheduler.scheduleAtFixedRate(() -> {
                synchronized (handle.mutex) {
                    if (handle.cancelled) {
                        return;
                    }
                    try {
                        lockManager.renewLock(task);
                    } catch (Exception e) {
                        LOGGER.debug("Lock renewal failed for task {}: {}", task.getItemId(), e.getMessage());
                    }
                }
            }, interval, interval, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Scheduler is shutting down; without renewal the lock simply ages out, which is
            // exactly what peers need in order to recover the work.
            activeLockRenewals.remove(task.getItemId(), handle);
        }
    }

    /**
     * Stops lock renewal for a task, waiting out any in-flight renewal tick. Must be called
     * before a terminal transition's fresh OCC read: a renewal write landing after that read
     * would advance the store version and make the terminal compare-and-set fail, stranding
     * a finished task as RUNNING in the store.
     */
    private void stopLockRenewal(String taskId) {
        if (taskId == null) {
            return;
        }
        LockRenewalHandle handle = activeLockRenewals.remove(taskId);
        if (handle == null) {
            return;
        }
        synchronized (handle.mutex) {
            handle.cancelled = true;
        }
        if (handle.future != null) {
            handle.future.cancel(false);
        }
    }

    /**
     * Reclaims a task that crash recovery prematurely marked as CRASHED while it was in fact
     * still executing on this node (e.g. the executing thread stalled long enough for the
     * task lock to expire). The executor invoking its status callback proves the execution is
     * alive, so the CRASHED marker is wrong: without reclaiming, the callback would be
     * silently ignored and a one-shot task would be stranded in CRASHED state forever
     * (recovery refuses to restart one-shot tasks that already executed, and the task checker
     * only selects SCHEDULED/WAITING tasks).
     *
     * The executingNodeId guard ensures we only reclaim executions this node actually owns:
     * it is set by the task wrapper right after successful preparation and cleared when the
     * wrapper finishes, and recovery preserves it when marking a task CRASHED.
     */
    private void reclaimIfPrematurelyCrashed(ScheduledTask task) {
        if (task.getStatus() == ScheduledTask.TaskStatus.CRASHED
                && nodeId != null && nodeId.equals(task.getExecutingNodeId())) {
            LOGGER.info("Task {} was marked CRASHED by recovery while still executing on node {}; reclaiming it",
                task.getItemId(), nodeId);
            stateManager.updateTaskState(task, ScheduledTask.TaskStatus.RUNNING, null, nodeId);
        }
    }

    /**
     * Returns whether this node may commit a terminal completion/error transition.
     * Reloads the store view so we do not overwrite CANCELLED or a peer that stole the
     * lock / execution after our local RUNNING view became stale.
     */
    private boolean canCommitTerminalTransition(ScheduledTask task) {
        reclaimIfPrematurelyCrashed(task);
        if (task.getStatus() != ScheduledTask.TaskStatus.RUNNING) {
            return false;
        }
        if (!task.isPersistent() || schedulerService == null) {
            return true;
        }

        ScheduledTask latest = schedulerService.getTask(task.getItemId(), true);
        if (latest == null) {
            // Store unavailable / deleted: fall back to the in-memory RUNNING decision.
            return true;
        }

        if (latest.getStatus() == ScheduledTask.TaskStatus.CANCELLED) {
            LOGGER.info("Skipping terminal transition for task {}: store status is CANCELLED",
                task.getItemId());
            task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
            return false;
        }

        String latestExecutor = latest.getExecutingNodeId();
        if (latestExecutor != null && !nodeId.equals(latestExecutor)) {
            LOGGER.info("Skipping terminal transition for task {}: peer {} owns execution",
                task.getItemId(), latestExecutor);
            return false;
        }

        String latestLockOwner = latest.getLockOwner();
        if (latestLockOwner != null && !nodeId.equals(latestLockOwner)
                && !lockManager.isLockExpired(latest)) {
            LOGGER.info("Skipping terminal transition for task {}: peer {} holds a non-expired lock",
                task.getItemId(), latestLockOwner);
            return false;
        }

        // Allow RUNNING. Allow CRASHED only when this node still owns executingNodeId
        // (premature-crash reclaim). Any other store status means cancel/recovery/peer
        // already moved the document — a late complete/fail must not clobber it.
        if (latest.getStatus() == ScheduledTask.TaskStatus.CRASHED) {
            if (latestExecutor == null || !nodeId.equals(latestExecutor)) {
                LOGGER.info("Skipping terminal transition for task {}: CRASHED without our executingNodeId",
                    task.getItemId());
                return false;
            }
        } else if (latest.getStatus() != ScheduledTask.TaskStatus.RUNNING) {
            LOGGER.info("Skipping terminal transition for task {}: store status is {}",
                task.getItemId(), latest.getStatus());
            return false;
        }

        // Carry OCC tokens from the fresh load so persistTerminalState can CAS.
        TaskLockManager.copyOccMetadata(latest, task);
        return true;
    }

    /**
     * Persists a terminal task state. Persistent tasks use compare-and-set so a late
     * complete/fail cannot clobber CANCELLED or a peer's RUNNING document. Lock fields are
     * cleared on the same write to avoid a separate alwaysOverwrite unlock race.
     */
    private boolean persistTerminalState(ScheduledTask task) {
        task.setLockOwner(null);
        task.setLockDate(null);
        if (!task.isPersistent()) {
            boolean saved = schedulerService.saveTask(task);
            if (!saved) {
                LOGGER.warn("Failed to persist terminal state for non-persistent task {} (status={})",
                    task.getItemId(), task.getStatus());
            }
            return saved;
        }
        boolean saved = schedulerService.saveTaskWithRefresh(task);
        if (!saved) {
            LOGGER.warn("Terminal persist lost OCC race for task {} (status={})",
                task.getItemId(), task.getStatus());
        }
        return saved;
    }

    /**
     * Handles task completion
     */
    private void handleTaskCompletion(ScheduledTask task, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;

        // Stop heartbeating before the terminal fresh read below — a renewal landing after
        // that read would advance the store version and fail the terminal compare-and-set.
        stopLockRenewal(task.getItemId());

        if (!canCommitTerminalTransition(task)) {
            return;
        }

        stateManager.updateTaskState(task, ScheduledTask.TaskStatus.COMPLETED, null, nodeId);
        task.setLastExecutionDate(new Date());
        task.setLastExecutedBy(nodeId);
        task.setFailureCount(0);
        task.setSuccessCount(task.getSuccessCount() + 1);

        historyManager.recordSuccess(task, executionTime);

        // Handle task completion based on type
        if (task.isOneShot()) {
            task.setEnabled(false);
            task.setNextScheduledExecution(null);  // Clear next execution time
            scheduledTasks.remove(task.getItemId());
        } else if (task.getPeriod() > 0) {
            // For periodic tasks, calculate next execution time
            stateManager.calculateNextExecutionTime(task);
            // Only transition to SCHEDULED if next execution is set (task might be disabled)
            if (task.getNextScheduledExecution() != null) {
                stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
            }
        }

        // Clean up executing tasks set
        Set<String> executingTasks = executingTasksByType.get(task.getTaskType());
        if (executingTasks != null) {
            executingTasks.remove(task.getItemId());
        }

        if (!persistTerminalState(task)) {
            return;
        }

        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_EXECUTION_TIME, executionTime);
    }

    /**
     * Handles task error
     */
    private void handleTaskError(ScheduledTask task, String error, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;

        // Stop heartbeating before the terminal fresh read below — a renewal landing after
        // that read would advance the store version and fail the terminal compare-and-set.
        stopLockRenewal(task.getItemId());

        if (!canCommitTerminalTransition(task)) {
            return;
        }

        stateManager.updateTaskState(task, ScheduledTask.TaskStatus.FAILED, error, nodeId);
        task.setFailureCount(task.getFailureCount() + 1);

        historyManager.recordFailure(task, error);

        boolean scheduleRetry = false;
        // Check if we should retry
        if (task.getFailureCount() <= task.getMaxRetries()) {
            // Calculate next retry time
            stateManager.calculateNextExecutionTime(task, true);
            stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
            scheduleRetry = true;
        } else if (!task.isOneShot()) {
            LOGGER.debug("Periodic task {} failed all retries but scheduling for next period in {} ms", task.getItemId(), task.getPeriod());
            task.setLastExecutionDate(new Date());
            task.setLastExecutedBy(nodeId);
            // Reset failure count so the next period gets a fresh retry budget
            // (matches ScheduledTask API docs and prevents immediate exhaustion on period 2).
            task.setFailureCount(0);
            stateManager.calculateNextExecutionTime(task, false);
            if (task.getNextScheduledExecution() != null) {
                stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
            }
        }

        scheduledTasks.remove(task.getItemId());

        if (!persistTerminalState(task)) {
            return;
        }

        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_FAILED);
        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_EXECUTION_TIME, executionTime);

        if (scheduleRetry) {
            // Only schedule retry if this manager is not shutting down. Check shuttingDown before
            // scheduler.isShutdown(): canceling in-flight futures can invoke handleTaskError before
            // scheduler.shutdown() runs, which would otherwise queue a retry that fires after teardown.
            if (!shuttingDown.get() && !scheduler.isShutdown() && !scheduler.isTerminated()) {
                try {
                    Runnable retryTask = () -> {
                        TaskExecutor executor = executorRegistry.getExecutor(task.getTaskType());
                        if (executor != null) {
                            executeTask(task, executor);
                        }
                    };
                    // Use the configured retry delay directly rather than re-deriving it from
                    // nextScheduledExecution: that target was computed before the state/history/metrics
                    // bookkeeping above ran, so subtracting "now" here would silently erode the delay
                    // by however long that bookkeeping took (worse under slower/contended runners).
                    long retryDelay = task.getRetryDelay();
                    scheduler.schedule(retryTask, retryDelay, TimeUnit.MILLISECONDS);
                    LOGGER.debug("Scheduled retry #{} for task {} in {} ms",
                        task.getFailureCount(), task.getItemId(), retryDelay);
                } catch (RejectedExecutionException e) {
                    LOGGER.debug("Retry scheduling rejected for task {} as scheduler is shutting down", task.getItemId());
                }
            } else {
                LOGGER.debug("Not scheduling retry for task {} as scheduler is shutting down", task.getItemId());
            }
        }
    }

    /**
     * Updates task metrics
     */
    private void updateTaskMetrics(ScheduledTask task, long startTime) {
        if (task.getStatus() == ScheduledTask.TaskStatus.COMPLETED) {
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED);
            long duration = System.currentTimeMillis() - startTime;
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_EXECUTION_TIME, duration);
        } else if (task.getStatus() == ScheduledTask.TaskStatus.FAILED) {
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_FAILED);
        } else if (task.getStatus() == ScheduledTask.TaskStatus.CRASHED) {
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_CRASHED);
        } else if (task.getStatus() == ScheduledTask.TaskStatus.WAITING) {
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_WAITING);
        } else if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_RUNNING);
        }
    }

    /**
     * Cancels a running task.
     *
     * @param taskId the task ID to cancel
     */
    public void cancelTask(String taskId) {
        stopLockRenewal(taskId);

        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(true);
        }

        // Remove from all executing task sets
        for (Set<String> executingTasks : executingTasksByType.values()) {
            executingTasks.remove(taskId);
        }
    }

    /**
     * Shuts down the execution manager
     */
    public void shutdown() {
        // Mark before canceling futures so in-flight failures cannot schedule retries.
        shuttingDown.set(true);
        stopTaskChecker();

        // Stop all lock heartbeats so held locks age out and peers can recover the work
        for (String taskId : new ArrayList<>(activeLockRenewals.keySet())) {
            stopLockRenewal(taskId);
        }

        // Cancel all scheduled and running tasks
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(true);
        }
        scheduledTasks.clear();
        executingTasksByType.clear();

        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    /**
     * Returns the internal scheduled executor service.
     *
     * @return the scheduler executor
     */
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

}
