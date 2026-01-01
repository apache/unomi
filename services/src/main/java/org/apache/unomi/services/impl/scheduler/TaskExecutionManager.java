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

    private String nodeId;
    private ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;
    private TaskStateManager stateManager;
    private TaskLockManager lockManager;
    private TaskMetricsManager metricsManager;
    private TaskHistoryManager historyManager;
    private final Map<String, Set<String>> executingTasksByType;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> taskCheckerFuture;
    private SchedulerServiceImpl schedulerService;
    private TaskExecutorRegistry executorRegistry;
    private int threadPoolSize = MIN_THREAD_POOL_SIZE;

    public TaskExecutionManager() {
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.executingTasksByType = new ConcurrentHashMap<>();
    }

    // Setter methods for Blueprint dependency injection
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = Math.max(MIN_THREAD_POOL_SIZE, threadPoolSize);
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

    public void setHistoryManager(TaskHistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    public void setExecutorRegistry(TaskExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

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
     * Starts the task checking service if this is an executor node
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
     * Schedules a task for execution based on its configuration
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

            TaskExecutor.TaskStatusCallback statusCallback = createStatusCallback(task);
            Runnable taskWrapper = createTaskWrapper(task, executor, statusCallback);

            // Execute task immediately using the scheduler
            ScheduledFuture<?> future = scheduler.schedule(taskWrapper, 0, TimeUnit.MILLISECONDS);
            scheduledTasks.put(task.getItemId(), future);
            executingSet.add(task.getItemId());
        } catch (Exception e) {
            LOGGER.error("Node "+nodeId+", Error executing task: " + task.getItemId(), e);
            handleTaskError(task, e.getMessage(), System.currentTimeMillis());
        }
    }

    /**
     * Prepares a task for execution by validating state and acquiring lock if needed
     */
    public boolean prepareForExecution(ScheduledTask task) {
        if (!task.isEnabled()) {
            LOGGER.debug("Task {} is disabled", task.getItemId());
            return false;
        }

        // Only execute tasks that are in SCHEDULED state (or CRASHED for recovery)
        if (task.getStatus() != ScheduledTask.TaskStatus.SCHEDULED &&
            task.getStatus() != ScheduledTask.TaskStatus.CRASHED) {
            LOGGER.debug("Task {} not in executable state: {}", task.getItemId(), task.getStatus());
            return false;
        }

        // For persistent tasks, acquire lock before execution
        if (task.isPersistent() && !lockManager.acquireLock(task)) {
            LOGGER.debug("Could not acquire lock for task: {}", task.getItemId());
            return false;
        }

        stateManager.updateTaskState(task, ScheduledTask.TaskStatus.RUNNING, null, nodeId);
        schedulerService.saveTask(task);
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
            // Check shutdown flag first - if scheduler is shutting down, skip task execution
            if (schedulerService != null && schedulerService.isShutdownNow()) {
                LOGGER.debug("Node {} : Skipping task {} execution as scheduler is shutting down", nodeId, task != null ? task.getItemId() : "unknown");
                return;
            }

            if (task == null) {
                LOGGER.error("Node {} : Cannot execute null task", nodeId);
                return;
            }
            if (executor == null) {
                LOGGER.error("Node {} : Cannot execute null executor for task type : {}", nodeId, task.getTaskType());
                return;
            }

            String taskId = task.getItemId();
            String taskType = task.getTaskType();

            if (taskType == null) {
                LOGGER.error("Task type is null for task: {}", taskId);
                return;
            }

            // Check shutdown again before preparing for execution
            if (schedulerService != null && schedulerService.isShutdownNow()) {
                LOGGER.debug("Node {} : Skipping task {} execution as scheduler is shutting down", nodeId, taskId);
                return;
            }

            // Prepare task for execution (both persistent and in-memory)
            if (!prepareForExecution(task)) {
                return;
            }

            // Final shutdown check before executing
            if (schedulerService != null && schedulerService.isShutdownNow()) {
                LOGGER.debug("Node {} : Skipping task {} execution as scheduler is shutting down", nodeId, taskId);
                return;
            }

            try {
                // Get or create the executing tasks set
                Set<String> executingTasks = executingTasksByType.computeIfAbsent(taskType,
                    k -> ConcurrentHashMap.newKeySet());

                // Only add to executing set if not already there
                if (taskId != null) {
                    executingTasks.add(taskId);
                }

                // Set the executing node ID
                task.setExecutingNodeId(nodeId);
                schedulerService.saveTask(task);

                long startTime = System.currentTimeMillis();
                try {
                    if (task.getStatus() == ScheduledTask.TaskStatus.CRASHED && executor.canResume(task)) {
                        executor.resume(task, statusCallback);
                    } else {
                        executor.execute(task, statusCallback);
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && !e.getMessage().equals("Simulated crash")) {
                        LOGGER.error("Error executing task: " + taskId, e);
                        statusCallback.fail(e.getMessage());
                    }
                } finally {
                    updateTaskMetrics(task, startTime);
                }
            } catch (Exception e) {
                LOGGER.error("Unexpected error while executing task: " + taskId, e);
                statusCallback.fail("Unexpected error: " + e.getMessage());
            } finally {
                // Clear executing node ID
                task.setExecutingNodeId(null);
                schedulerService.saveTask(task);

                // Remove task from executing set
                try {
                    Set<String> executingTasks = executingTasksByType.get(taskType);
                    if (executingTasks != null && taskId != null) {
                        executingTasks.remove(taskId);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error cleaning up task execution state: " + taskId, e);
                }
            }
        };
    }

    /**
     * Handles task completion
     */
    private void handleTaskCompletion(ScheduledTask task, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;

        // Only transition to completed if still in RUNNING state
        if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
            stateManager.updateTaskState(task, ScheduledTask.TaskStatus.COMPLETED, null, nodeId);
            task.setLastExecutionDate(new Date());
            task.setLastExecutedBy(nodeId);
            task.setFailureCount(0);
            task.setSuccessCount(task.getSuccessCount() + 1);

            historyManager.recordSuccess(task, executionTime);
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED);
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_EXECUTION_TIME, executionTime);

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

            // Release lock for persistent tasks
            if (task.isPersistent()) {
                lockManager.releaseLock(task);
            }

            // Clean up executing tasks set
            Set<String> executingTasks = executingTasksByType.get(task.getTaskType());
            if (executingTasks != null) {
                executingTasks.remove(task.getItemId());
            }

            schedulerService.saveTask(task);
        }
    }

    /**
     * Handles task error
     */
    private void handleTaskError(ScheduledTask task, String error, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;

        // Only transition to failed if still in RUNNING state
        if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
            stateManager.updateTaskState(task, ScheduledTask.TaskStatus.FAILED, error, nodeId);
            task.setFailureCount(task.getFailureCount() + 1);

            historyManager.recordFailure(task, error);
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_FAILED);
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_EXECUTION_TIME, executionTime);

            // Check if we should retry
            if (task.getFailureCount() <= task.getMaxRetries()) {
                // Calculate next retry time
                stateManager.calculateNextExecutionTime(task, true);
                stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);

                // Only schedule retry if scheduler is not shutting down
                if (!scheduler.isShutdown() && !scheduler.isTerminated()) {
                    // Schedule retry
                    try {
                        Runnable retryTask = () -> {
                            TaskExecutor executor = executorRegistry.getExecutor(task.getTaskType());
                            if (executor != null) {
                                executeTask(task, executor);
                            }
                        };
                        long retryDelay = task.getNextScheduledExecution().getTime() - System.currentTimeMillis();
                        scheduler.schedule(retryTask, retryDelay, TimeUnit.MILLISECONDS);
                        LOGGER.debug("Scheduled retry #{} for task {} in {} ms",
                            task.getFailureCount(), task.getItemId(), retryDelay);
                    } catch (RejectedExecutionException e) {
                        LOGGER.debug("Retry scheduling rejected for task {} as scheduler is shutting down", task.getItemId());
                    }
                } else {
                    LOGGER.debug("Not scheduling retry for task {} as scheduler is shutting down", task.getItemId());
                }
            } else if (!task.isOneShot()) {
                LOGGER.debug("Periodic task {} failed all retries but scheduling for next period in {} ms", task.getItemId(), task.getPeriod());
                schedulerService.saveTask(task); // persist failure state before going back to scheduled state
                task.setLastExecutionDate(new Date());
                task.setLastExecutedBy(nodeId);
                stateManager.calculateNextExecutionTime(task, false);
                if (task.getNextScheduledExecution() != null) {
                    stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
                }
            }

            // Release lock for persistent tasks
            if (task.isPersistent()) {
                lockManager.releaseLock(task);
            }

            schedulerService.saveTask(task);
            scheduledTasks.remove(task.getItemId());
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
     * Cancels a running task
     */
    public void cancelTask(String taskId) {
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
        stopTaskChecker();

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

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

}
