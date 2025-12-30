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

package org.apache.unomi.api.services;

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service for scheduling and managing tasks in a cluster-aware manner.
 * This service provides comprehensive task scheduling capabilities including:
 * <ul>
 *   <li>Task creation and lifecycle management</li>
 *   <li>Cluster-aware task execution and coordination</li>
 *   <li>Task recovery after node failures</li>
 *   <li>Support for persistent and in-memory tasks</li>
 *   <li>Task dependency management</li>
 *   <li>Execution history and metrics tracking</li>
 * </ul>
 *
 * The service supports both single-node and clustered environments, ensuring
 * tasks are executed reliably and efficiently across the cluster.
 */
public interface SchedulerService {

    /**
     * Creates a new scheduled task.
     * This method provides full control over task configuration including
     * execution timing, persistence, and parallel execution settings.
     * The task can be either persistent (stored in persistence service and
     * visible across the cluster) or non-persistent (stored only in memory
     * on the local node).
     *
     * @param taskType unique identifier for the task type
     * @param parameters task-specific parameters
     * @param initialDelay delay before first execution
     * @param period period between executions (0 for one-shot tasks)
     * @param timeUnit time unit for delay and period
     * @param fixedRate whether to use fixed rate (true) or fixed delay (false)
     * @param oneShot whether this is a one-time task
     * @param allowParallelExecution whether parallel execution is allowed
     * @param persistent whether to store the task in persistence service (true) or only in memory (false)
     * @return the created task instance
     * @throws IllegalArgumentException if task configuration is invalid
     */
    ScheduledTask createTask(String taskType,
            Map<String, Object> parameters,
            long initialDelay,
            long period,
            TimeUnit timeUnit,
            boolean fixedRate,
            boolean oneShot,
            boolean allowParallelExecution,
            boolean persistent);

    /**
     * Schedules an existing task for execution.
     * The task will be validated and scheduled according to its configuration.
     * For periodic tasks, this sets up recurring execution.
     *
     * @param task the task to schedule
     * @throws IllegalArgumentException if task configuration is invalid
     */
    void scheduleTask(ScheduledTask task);

    /**
     * Cancels a scheduled task.
     * This will stop any current execution and prevent future executions.
     * The task remains in storage but is marked as cancelled.
     *
     * @param taskId the task ID to cancel
     */
    void cancelTask(String taskId);

    /**
     * Gets all tasks from both storage and memory.
     * This provides a complete view of all tasks in the system,
     * both persistent and in-memory.
     *
     * @return combined list of all tasks
     */
    List<ScheduledTask> getAllTasks();

    /**
     * Gets a task by ID from either storage or memory.
     * This will search both persistent storage and in-memory tasks.
     *
     * @param taskId the task ID
     * @return the task or null if not found
     */
    ScheduledTask getTask(String taskId);

    /**
     * Gets all tasks stored in memory.
     * These are non-persistent tasks that exist only on this node.
     *
     * @return list of all in-memory tasks
     */
    List<ScheduledTask> getMemoryTasks();

    /**
     * Gets all tasks from persistent storage.
     * These tasks are visible across the cluster.
     *
     * @return list of all persistent tasks
     */
    List<ScheduledTask> getPersistentTasks();

    /**
     * Registers a task executor.
     * The executor will be used to execute tasks of its declared type.
     *
     * @param executor the executor to register
     */
    void registerTaskExecutor(TaskExecutor executor);

    /**
     * Unregisters a task executor.
     * Tasks of this type will no longer be executed on this node.
     *
     * @param executor the executor to unregister
     */
    void unregisterTaskExecutor(TaskExecutor executor);

    /**
     * Checks if this node is a task executor node.
     * Executor nodes are responsible for executing tasks in the cluster.
     *
     * @return true if this node executes tasks
     */
    boolean isExecutorNode();

    /**
     * Gets the node ID of this scheduler instance.
     * This ID uniquely identifies this node in the cluster.
     *
     * @return the node ID
     */
    String getNodeId();

    /**
     * Gets tasks with the specified status.
     * This allows filtering tasks by their current state.
     * The results include both persistent and in-memory tasks.
     *
     * @param status the task status to filter by
     * @param offset the starting offset for pagination
     * @param size the maximum number of tasks to return
     * @param sortBy optional sort field (null for default sorting)
     * @return partial list of matching tasks
     */
    PartialList<ScheduledTask> getTasksByStatus(ScheduledTask.TaskStatus status, int offset, int size, String sortBy);

    /**
     * Gets tasks for a specific executor type.
     * This allows filtering tasks by their type.
     * The results include both persistent and in-memory tasks.
     *
     * @param taskType the task type to filter by
     * @param offset the starting offset for pagination
     * @param size the maximum number of tasks to return
     * @param sortBy optional sort field (null for default sorting)
     * @return partial list of matching tasks
     */
    PartialList<ScheduledTask> getTasksByType(String taskType, int offset, int size, String sortBy);

    /**
     * Retries a failed task.
     * The task will be rescheduled for execution with optional
     * failure count reset. The task must be in FAILED status
     * for this operation to succeed.
     *
     * @param taskId the task ID to retry
     * @param resetFailureCount whether to reset the failure count to 0
     */
    void retryTask(String taskId, boolean resetFailureCount);

    /**
     * Resumes a crashed task from its last checkpoint.
     * This attempts to continue execution from where the task
     * left off before crashing. The task must be in CRASHED status
     * and have checkpoint data available for this operation to succeed.
     *
     * @param taskId the task ID to resume
     */
    void resumeTask(String taskId);

    /**
     * Checks for crashed tasks from other nodes and attempts recovery.
     * This is part of the cluster's self-healing mechanism.
     */
    void recoverCrashedTasks();

    /**
     * Saves changes to an existing task.
     * This persists the task state and configuration changes to storage.
     *
     * @param task the task to save
     * @return true if the save was successful, false otherwise
     */
    boolean saveTask(ScheduledTask task);

    /**
     * Creates a simple recurring task with default settings.
     * This is a convenience method for services that just need periodic execution.
     * The task will use fixed rate scheduling and allow parallel execution.
     * The created task will be automatically scheduled for execution.
     *
     * @param taskType unique identifier for the task type
     * @param period time between executions (must be > 0)
     * @param timeUnit unit for the period
     * @param runnable the code to execute
     * @param persistent whether to store in persistence service (true) or only in memory (false)
     * @return the created and scheduled task
     * @throws IllegalArgumentException if period <= 0 or timeUnit is null
     */
    ScheduledTask createRecurringTask(String taskType, long period, TimeUnit timeUnit, Runnable runnable, boolean persistent);

    /**
     * Creates a new task builder for fluent task creation.
     * The builder pattern provides a more readable way to configure tasks
     * with optional parameters.
     * Example usage:
     * <pre>
     * schedulerService.newTask("myTask")
     *     .withPeriod(1, TimeUnit.HOURS)
     *     .withSimpleExecutor(() -> doSomething())
     *     .schedule();
     * </pre>
     *
     * @param taskType unique identifier for the task type
     * @return a builder to configure and create the task
     */
    TaskBuilder newTask(String taskType);

    /**
     * Gets the value of a specific metric.
     * @param metric The metric name
     * @return The current value of the metric
     */
    long getMetric(String metric);

    /**
     * Resets all metrics to zero.
     */
    void resetMetrics();

    /**
     * Gets all metrics as a map.
     * @return Map of metric names to their current values
     */
    Map<String, Long> getAllMetrics();

    List<ScheduledTask> findTasksByStatus(ScheduledTask.TaskStatus taskStatus);

    /**
     * Builder interface for fluent task creation.
     * This interface provides methods to configure all aspects of a task
     * in a readable manner.
     */
    interface TaskBuilder {
        /**
         * Sets task parameters.
         * @param parameters task-specific parameters
         */
        TaskBuilder withParameters(Map<String, Object> parameters);

        /**
         * Sets initial execution delay.
         * @param initialDelay delay before first execution
         * @param timeUnit time unit for delay
         */
        TaskBuilder withInitialDelay(long initialDelay, TimeUnit timeUnit);

        /**
         * Sets execution period.
         * @param period time between executions
         * @param timeUnit time unit for period
         */
        TaskBuilder withPeriod(long period, TimeUnit timeUnit);

        /**
         * Uses fixed delay scheduling.
         * Period is measured from completion of one execution to start of next.
         */
        TaskBuilder withFixedDelay();

        /**
         * Uses fixed rate scheduling.
         * Period is measured from start of one execution to start of next.
         */
        TaskBuilder withFixedRate();

        /**
         * Makes this a one-shot task.
         * Task will execute once and then be disabled.
         */
        TaskBuilder asOneShot();

        /**
         * Disallows parallel execution.
         * Task will use locking to ensure only one instance runs at a time.
         */
        TaskBuilder disallowParallelExecution();

        /**
         * Sets the task executor.
         * @param executor the executor to handle this task
         */
        TaskBuilder withExecutor(TaskExecutor executor);

        /**
         * Sets a simple runnable as the executor.
         * @param runnable the code to execute
         */
        TaskBuilder withSimpleExecutor(Runnable runnable);

        /**
         * Makes this a non-persistent task.
         * Task will only exist in memory on this node.
         */
        TaskBuilder nonPersistent();

        /**
         * Runs the task on all nodes in the cluster rather than just executor nodes.
         * This is helpful for distributed cache refreshes or local data maintenance.
         */
        TaskBuilder runOnAllNodes();

        /**
         * Marks this task as a system task.
         * System tasks are created during system initialization and should be
         * preserved across restarts rather than being recreated.
         *
         * @return this builder for method chaining
         */
        TaskBuilder asSystemTask();

        /**
         * Sets the maximum number of retry attempts after failures.
         * For one-shot tasks:
         * - When a task fails, it will be automatically retried up to this many times
         * - Each retry attempt occurs after waiting for retryDelay
         * - After reaching this limit, the task remains in FAILED state until manually retried
         *
         * For periodic tasks:
         * - Retries only apply within a single scheduled execution
         * - If retries are exhausted, the task will still attempt its next scheduled execution
         * - The next scheduled execution resets the failure count
         *
         * A value of 0 means no automatic retries in either case.
         *
         * @param maxRetries maximum number of retries (must be >= 0)
         * @throws IllegalArgumentException if maxRetries is negative
         */
        TaskBuilder withMaxRetries(int maxRetries);

        /**
         * Sets the delay between retry attempts.
         * For one-shot tasks:
         * - This delay is applied between each retry attempt after a failure
         * - Helps prevent rapid-fire retries that could overload the system
         *
         * For periodic tasks:
         * - This delay is used between retry attempts within a single scheduled execution
         * - Does not affect the task's configured period/scheduling
         *
         * @param delay delay duration (must be >= 0)
         * @param unit time unit for delay
         * @throws IllegalArgumentException if delay is negative
         */
        TaskBuilder withRetryDelay(long delay, TimeUnit unit);

        /**
         * Sets the task dependencies.
         * The task will not execute until all dependencies have completed.
         * @param taskIds IDs of tasks this task depends on
         */
        TaskBuilder withDependencies(String... taskIds);

        /**
         * Creates and schedules the task with current configuration.
         * @return the created and scheduled task
         */
        ScheduledTask schedule();
    }
}
