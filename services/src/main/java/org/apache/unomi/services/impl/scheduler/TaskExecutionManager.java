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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages task execution, including scheduling, execution tracking, and completion handling.
 */
public class TaskExecutionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutionManager.class);
    private static final int MIN_THREAD_POOL_SIZE = 4;

    private final String nodeId;
    private final ScheduledExecutorService sharedScheduler;
    private final Map<String, ScheduledFuture<?>> runningTasks;
    private final TaskStateManager stateManager;
    private final TaskLockManager lockManager;
    private final TaskMetricsManager metricsManager;
    private final Map<String, TaskExecutor> taskExecutors;
    private final TaskHistoryManager historyManager;
    private final Map<String, Set<String>> executingTasksByType;
    private final PersistenceService persistenceService;

    public TaskExecutionManager(String nodeId, int threadPoolSize,
                              TaskStateManager stateManager,
                              TaskLockManager lockManager,
                              TaskMetricsManager metricsManager,
                              TaskHistoryManager historyManager,
                                PersistenceService persistenceService) {
        this.nodeId = nodeId;
        this.stateManager = stateManager;
        this.lockManager = lockManager;
        this.metricsManager = metricsManager;
        this.historyManager = historyManager;
        this.runningTasks = new ConcurrentHashMap<>();
        this.taskExecutors = new ConcurrentHashMap<>();
        this.executingTasksByType = new ConcurrentHashMap<>();
        this.persistenceService = persistenceService;

        // Initialize shared scheduler
        this.sharedScheduler = Executors.newScheduledThreadPool(
            Math.max(MIN_THREAD_POOL_SIZE, threadPoolSize),
            r -> {
                Thread t = new Thread(r);
                t.setName("UnomiSharedScheduler-" + t.getId());
                t.setDaemon(true);
                return t;
            }
        );
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
            executingTasksByType.putIfAbsent(taskType, ConcurrentHashMap.newKeySet());

            TaskExecutor.TaskStatusCallback statusCallback = createStatusCallback(task);
            Runnable taskWrapper = createTaskWrapper(task, executor, statusCallback);

            if (!task.isPersistent()) {
                // For in-memory tasks, execute directly and store actual future
                ScheduledFuture<?> future = sharedScheduler.schedule(taskWrapper, 0, TimeUnit.MILLISECONDS);
                runningTasks.put(task.getItemId(), future);
                executingTasksByType.get(taskType).add(task.getItemId());
            } else {
                // For persistent tasks, execute if ready
                ScheduledFuture<?> future = sharedScheduler.schedule(taskWrapper, 0, TimeUnit.MILLISECONDS);
                runningTasks.put(task.getItemId(), future);
                executingTasksByType.get(taskType).add(task.getItemId());
            }
        } catch (Exception e) {
            LOGGER.error("Node "+nodeId+", Error executing task: " + task.getItemId(), e);
            handleTaskError(task, e.getMessage(), System.currentTimeMillis());
        }
    }

    /**
     * Schedules a recurring in-memory task using the shared scheduler.
     * This method should only be used for non-persistent tasks.
     * If the task is already scheduled, it will not be rescheduled unless it has crashed.
     */
    public void scheduleRecurringTask(ScheduledTask task, Runnable taskWrapper) {
        if (!task.isPersistent() && task.getPeriod() > 0 && !task.isOneShot()) {
            // Check if task is already scheduled
            ScheduledFuture<?> existingFuture = runningTasks.get(task.getItemId());
            if (existingFuture != null && !existingFuture.isDone() && !existingFuture.isCancelled()) {
                LOGGER.debug("Node {}, Task {} is already scheduled, skipping scheduling", nodeId, task.getItemId());
                return;
            }

            long initialDelay = calculateInitialDelay(task);
            ScheduledFuture<?> future;

            if (task.isFixedRate()) {
                future = sharedScheduler.scheduleAtFixedRate(
                    taskWrapper, initialDelay, task.getPeriod(), task.getTimeUnit());
            } else {
                future = sharedScheduler.scheduleWithFixedDelay(
                    taskWrapper, initialDelay, task.getPeriod(), task.getTimeUnit());
            }
            runningTasks.put(task.getItemId(), future);
            LOGGER.debug("Scheduled recurring task {} with initial delay {} and period {}",
                task.getItemId(), initialDelay, task.getPeriod());
        } else {
            LOGGER.warn("Attempted to schedule recurring task {} with shared scheduler, but task is persistent or not recurring",
                task.getItemId());
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
        if (task.isPersistent()) {
            updateTaskInPersistence(task);
        }
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
                updateTaskInPersistence(task);
            }

            @Override
            public void checkpoint(Map<String, Object> checkpointData) {
                task.setCheckpointData(checkpointData);
                updateTaskInPersistence(task);
            }

            @Override
            public void updateStatusDetails(Map<String, Object> details) {
                task.setStatusDetails(details);
                updateTaskInPersistence(task);
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

            // Prepare task for execution (both persistent and in-memory)
            if (!prepareForExecution(task)) {
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
                if (task.isPersistent()) {
                    updateTaskInPersistence(task);
                }

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
                if (task.isPersistent()) {
                    updateTaskInPersistence(task);
                }

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
     * Calculates initial delay for task execution
     */
    private long calculateInitialDelay(ScheduledTask task) {
        if (task.getInitialDelay() > 0) {
            // If initial delay is specified, use it
            return task.getInitialDelay();
        }

        if (task.getNextScheduledExecution() != null) {
            // If next execution time is set, calculate delay from now
            long now = System.currentTimeMillis();
            long nextExecution = task.getNextScheduledExecution().getTime();
            return Math.max(0, nextExecution - now);
        }

        return 0;
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

            historyManager.recordSuccess(task, executionTime);
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED);
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_EXECUTION_TIME, executionTime);

            // Handle task completion based on type
            if (task.isOneShot()) {
                task.setEnabled(false);
                task.setNextScheduledExecution(null);  // Clear next execution time
                runningTasks.remove(task.getItemId());
            } else if (task.getPeriod() > 0) {
                if (task.isPersistent()) {
                    // For persistent periodic tasks, calculate next execution time
                    stateManager.calculateNextExecutionTime(task);
                    // Only transition to SCHEDULED if next execution is set (task might be disabled)
                    if (task.getNextScheduledExecution() != null) {
                        stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
                    }
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

            updateTaskInPersistence(task);
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
            if (task.getFailureCount() < task.getMaxRetries()) {
                // Calculate next retry time
                stateManager.calculateNextExecutionTime(task, true);
                stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);

                // Schedule retry
                Runnable retryTask = () -> {
                    TaskExecutor executor = getTaskExecutor(task.getTaskType());
                    if (executor != null) {
                        executeTask(task, executor);
                    }
                };
                long retryDelay = task.getNextScheduledExecution().getTime() - System.currentTimeMillis();
                sharedScheduler.schedule(retryTask, retryDelay, TimeUnit.MILLISECONDS);
                LOGGER.debug("Scheduled retry #{} for task {} in {} ms",
                    task.getFailureCount(), task.getItemId(), retryDelay);
            } else if (!task.isOneShot()) {
                LOGGER.debug("Periodic task {} failed all retries but scheduling for next period in {} ms", task.getItemId(), task.getPeriod());
                updateTaskInPersistence(task); // persist failure state before going back to scheduled state
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

            updateTaskInPersistence(task);
            runningTasks.remove(task.getItemId());
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
     * Records task execution history
     */
    private void recordTaskExecution(ScheduledTask task, boolean success, String error) {
        Map<String, Object> execution = new HashMap<>();
        execution.put("timestamp", new Date());
        execution.put("success", success);
        execution.put("nodeId", nodeId);
        if (error != null) {
            execution.put("error", error);
        }

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

        if (history.size() >= 10) { // Keep last 10 executions
            history.remove(0);
        }
        history.add(execution);
    }

    /**
     * Registers a task executor
     */
    public void registerTaskExecutor(TaskExecutor executor) {
        taskExecutors.put(executor.getTaskType(), executor);
    }

    /**
     * Unregisters a task executor
     */
    public void unregisterTaskExecutor(TaskExecutor executor) {
        taskExecutors.remove(executor.getTaskType());
    }

    /**
     * Gets a task executor by type
     */
    public TaskExecutor getTaskExecutor(String taskType) {
        return taskExecutors.get(taskType);
    }

    /**
     * Cancels a running task
     */
    public void cancelTask(String taskId) {
        ScheduledFuture<?> future = runningTasks.remove(taskId);
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
        // Cancel all running tasks
        for (ScheduledFuture<?> future : runningTasks.values()) {
            future.cancel(true);
        }
        runningTasks.clear();
        taskExecutors.clear();
        executingTasksByType.clear();

        // Shutdown scheduler
        sharedScheduler.shutdown();
        try {
            if (!sharedScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                sharedScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sharedScheduler.shutdownNow();
        }
    }

    private void updateTaskInPersistence(ScheduledTask task) {
        if (task.isPersistent()) {
            persistenceService.save(task);
        }
    }

    public ScheduledExecutorService getSharedScheduler() {
        return sharedScheduler;
    }

    /**
     * Creates a dummy future for one-time tasks
     */
    private ScheduledFuture<?> createDummyFuture() {
        return new ScheduledFuture<Object>() {
            @Override
            public long getDelay(TimeUnit unit) {
                return 0;
            }

            @Override
            public int compareTo(Delayed o) {
                return 0;
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return true;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return true;
            }

            @Override
            public Object get() {
                return null;
            }

            @Override
            public Object get(long timeout, TimeUnit unit) {
                return null;
            }
        };
    }
}
